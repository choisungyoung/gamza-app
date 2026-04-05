package com.myapp.budget

import androidx.compose.ui.window.ComposeUIViewController
import com.myapp.budget.data.remote.SupabaseClientProvider
import com.myapp.budget.di.appModule
import com.myapp.budget.di.iosModule
import com.myapp.budget.di.sharedModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController {
    App()
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
