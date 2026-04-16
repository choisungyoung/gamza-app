package com.myapp.budget

import androidx.compose.ui.window.ComposeUIViewController
import com.myapp.budget.data.remote.SupabaseClientProvider
import com.myapp.budget.di.appModule
import com.myapp.budget.di.iosModule
import com.myapp.budget.di.sharedModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController {
    App()
}

// Swift → Kotlin: GIDSignIn 시작을 요청하는 starter 등록
private var googleSignInStarter: (() -> Unit)? = null
private var pendingGoogleCallback: ((String?, String?, String?) -> Unit)? = null

fun registerGoogleSignInStarter(starter: () -> Unit) {
    googleSignInStarter = starter
}

// Swift → Kotlin: GIDSignIn 결과 전달 (idToken + rawNonce 함께)
fun onGoogleSignInResult(idToken: String?, rawNonce: String?, error: String?) {
    pendingGoogleCallback?.invoke(idToken, rawNonce, error)
    pendingGoogleCallback = null
}

// Kotlin → Swift: GIDSignIn 시작 요청
internal fun startNativeGoogleSignIn(callback: (String?, String?, String?) -> Unit) {
    pendingGoogleCallback = callback
    googleSignInStarter?.invoke()
}

// Supabase에 ID 토큰 + rawNonce로 로그인
internal suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
    SupabaseClientProvider.client.auth.signInWith(IDToken) {
        this.idToken = idToken
        this.nonce = rawNonce
        provider = Google
    }
}

fun initKoin(supabaseUrl: String, supabaseAnonKey: String, googleWebClientId: String = "") {
    SupabaseClientProvider.configure(
        url = supabaseUrl,
        anonKey = supabaseAnonKey,
        googleWebClientId = googleWebClientId,
    )
    startKoin {
        modules(iosModule, sharedModule, appModule)
    }
}
