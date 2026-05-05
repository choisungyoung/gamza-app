package com.myapp.budget.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** FCM 알림 탭 또는 foreground 수신 시 강퇴된 bookId를 App.kt 수집기로 전달하는 전역 브로커 */
object KickNotificationRegistry {
    private val _kickedBookId = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val kickedBookId: SharedFlow<String> = _kickedBookId.asSharedFlow()

    fun emit(bookId: String) {
        _kickedBookId.tryEmit(bookId)
    }
}
