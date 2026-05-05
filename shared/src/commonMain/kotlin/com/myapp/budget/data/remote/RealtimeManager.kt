package com.myapp.budget.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

class RealtimeManager(private val supabase: SupabaseClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _transactionChanges = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 변경이 감지된 bookId를 방출 */
    val transactionChanges: SharedFlow<String> = _transactionChanges.asSharedFlow()

    /** 강퇴되거나 가계부가 삭제된 bookId를 방출 */
    private val _membershipRevoked = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val membershipRevoked: SharedFlow<String> = _membershipRevoked.asSharedFlow()

    private var currentChannel: RealtimeChannel? = null
    private var watchingBookId: String? = null
    private var membershipChannel: RealtimeChannel? = null

    fun startWatching(bookId: String) {
        if (watchingBookId == bookId) return

        // 이전 채널 정리
        val old = currentChannel
        currentChannel = null
        watchingBookId = bookId
        if (old != null) {
            scope.launch { runCatching { old.unsubscribe() } }
        }

        // 새 채널 구독
        scope.launch {
            runCatching {
                val channel = supabase.channel("book-$bookId")
                val emit: suspend () -> Unit = {
                    if (watchingBookId == bookId) _transactionChanges.tryEmit(bookId)
                }
                listOf("transactions", "assets", "asset_groups").forEach { tbl ->
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = tbl
                    }.onEach { emit() }.launchIn(scope)
                }
                channel.subscribe()
                currentChannel = channel
            }.onFailure {
                println("[RealtimeManager] 구독 실패: ${it.message}")
            }
        }
    }

    fun stopWatching() {
        val channel = currentChannel ?: return
        currentChannel = null
        watchingBookId = null
        scope.launch { runCatching { channel.unsubscribe() } }
    }

    /** 로그인 시 호출. book_members DELETE 이벤트(강퇴/가계부 삭제)를 감시한다. */
    fun startWatchingMembership(userId: String) {
        scope.launch {
            runCatching {
                membershipChannel?.let { runCatching { it.unsubscribe() } }
                val channel = supabase.channel("membership-$userId")
                // RLS가 허용하는 book_members DELETE 이벤트를 수신 후 user_id로 필터링
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "book_members"
                }.onEach { action ->
                    if (action is PostgresAction.Delete) {
                        val deletedUserId = runCatching {
                            action.oldRecord["user_id"]?.jsonPrimitive?.content
                        }.getOrNull()
                        val bookId = runCatching {
                            action.oldRecord["book_id"]?.jsonPrimitive?.content
                        }.getOrNull()
                        if (deletedUserId == userId && bookId != null) {
                            _membershipRevoked.tryEmit(bookId)
                        }
                    }
                }.launchIn(scope)
                channel.subscribe()
                membershipChannel = channel
            }.onFailure {
                println("[RealtimeManager] 멤버십 구독 실패: ${it.message}")
            }
        }
    }

    /** 로그아웃 시 호출. */
    fun stopWatchingMembership() {
        val channel = membershipChannel ?: return
        membershipChannel = null
        scope.launch { runCatching { channel.unsubscribe() } }
    }
}
