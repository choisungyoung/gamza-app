package com.myapp.budget

import android.app.Application
import com.myapp.budget.data.remote.SupabaseClientProvider
import com.myapp.budget.di.androidModule
import com.myapp.budget.di.appModule
import com.myapp.budget.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BudgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClientProvider.configure(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )
        startKoin {
            androidContext(this@BudgetApp)
            modules(androidModule, sharedModule, appModule)
        }
    }
}
