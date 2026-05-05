package com.myapp.budget

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.myapp.budget.push.KickNotificationRegistry

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 허용/거부 무관하게 앱 계속 실행 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleKickIntent(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleKickIntent(intent)
    }

    private fun handleKickIntent(intent: Intent?) {
        if (intent?.getStringExtra(BudgetFcmService.EXTRA_TYPE) == BudgetFcmService.TYPE_KICK) {
            val bookId = intent.getStringExtra(BudgetFcmService.EXTRA_BOOK_ID) ?: return
            KickNotificationRegistry.emit(bookId)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST_CODE) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            val bytes = uri?.let {
                try { contentResolver.openInputStream(it)?.use { s -> s.readBytes() } } catch (_: Exception) { null }
            }
            FilePickerRegistry.callback?.invoke(bytes ?: ByteArray(0))
        }
    }

    companion object {
        const val FILE_PICKER_REQUEST_CODE = 9001
    }
}

/** 파일 선택 결과를 Compose로 전달하기 위한 글로벌 콜백 레지스트리 */
object FilePickerRegistry {
    var callback: ((ByteArray) -> Unit)? = null
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
