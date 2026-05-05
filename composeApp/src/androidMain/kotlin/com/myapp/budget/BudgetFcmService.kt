package com.myapp.budget

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.myapp.budget.push.KickNotificationRegistry

class BudgetFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmTokenHolder.token = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "KICK") return
        val bookId = message.data["book_id"] ?: return

        // App이 foreground일 때 호출됨 — 시스템 알림 표시 + 앱 내 처리
        KickNotificationRegistry.emit(bookId)
        showNotification(
            title = message.notification?.title ?: "가계부 알림",
            body = message.notification?.body ?: "가계부에서 제외되었습니다.",
            bookId = bookId,
        )
    }

    private fun showNotification(title: String, body: String, bookId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_BOOK_ID, bookId)
            putExtra(EXTRA_TYPE, TYPE_KICK)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(this, bookId.hashCode(), intent, flags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(bookId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "kick_notifications"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_TYPE = "type"
        const val TYPE_KICK = "KICK"
    }
}

/** FCM 토큰 갱신 시 임시 보관 — 다음 로그인 시 Supabase에 등록됨 */
object FcmTokenHolder {
    @Volatile var token: String? = null
}
