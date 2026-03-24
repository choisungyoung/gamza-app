package com.myapp.budget.di

import com.myapp.budget.db.DatabaseDriverFactory
import com.myapp.budget.db.createDatabase
import org.koin.dsl.module

val androidModule = module {
    single { DatabaseDriverFactory(get()) }
    single { createDatabase(get()) }
}
