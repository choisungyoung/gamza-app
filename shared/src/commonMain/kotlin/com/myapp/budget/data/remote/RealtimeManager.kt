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

class RealtimeManager(private val supabase: SupabaseClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _transactionChanges = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 변경이 감지된 bookId를 방출 */
    val transactionChanges: SharedFlow<String> = _transactionChanges.asSharedFlow()

    private var currentChannel: RealtimeChannel? = null
    private var watchingBookId: String? = null

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
                val channel = supabase.channel("transactions-$bookId")
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "transactions"
                }.onEach {
                    if (watchingBookId == bookId) {
                        _transactionChanges.tryEmit(bookId)
                    }
                }.launchIn(scope)
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
}
