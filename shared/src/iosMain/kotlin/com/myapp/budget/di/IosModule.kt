package com.myapp.budget.di

import com.myapp.budget.db.DatabaseDriverFactory
import com.myapp.budget.db.createDatabase
import org.koin.dsl.module

val iosModule = module {
    single { DatabaseDriverFactory() }
    single { createDatabase(get()) }
}
