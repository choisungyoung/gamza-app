package com.myapp.budget

import android.app.Application
import com.myapp.budget.di.androidModule
import com.myapp.budget.di.appModule
import com.myapp.budget.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BudgetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BudgetApp)
            modules(androidModule, sharedModule, appModule)
        }
    }
}
