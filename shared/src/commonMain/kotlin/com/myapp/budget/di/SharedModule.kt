package com.myapp.budget.di

import com.myapp.budget.data.remote.RealtimeManager
import com.myapp.budget.data.remote.SupabaseClientProvider
import com.myapp.budget.data.repository.AssetRepositoryImpl
import com.myapp.budget.data.repository.AuthRepositoryImpl
import com.myapp.budget.data.repository.BookRepositoryImpl
import com.myapp.budget.data.repository.CategoryRepositoryImpl
import com.myapp.budget.data.repository.DataExportRepositoryImpl
import com.myapp.budget.data.repository.DbViewerRepositoryImpl
import com.myapp.budget.data.repository.FixedExpenseRepositoryImpl
import com.myapp.budget.data.repository.TransactionRepositoryImpl
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.repository.AssetRepository
import com.myapp.budget.domain.repository.AuthRepository
import com.myapp.budget.domain.repository.BookRepository
import com.myapp.budget.domain.repository.CategoryRepository
import com.myapp.budget.domain.repository.DataExportRepository
import com.myapp.budget.domain.repository.DbViewerRepository
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import org.koin.dsl.module

val sharedModule = module {
    single { SessionManager() }
    single { SupabaseClientProvider.client }
    single { RealtimeManager(get()) }

    single<TransactionRepository> { TransactionRepositoryImpl(get(), get(), get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get(), get(), get()) }
    single<AssetRepository> { AssetRepositoryImpl(get(), get(), get()) }
    single<FixedExpenseRepository> { FixedExpenseRepositoryImpl(get(), get(), get()) }
    single<DataExportRepository> { DataExportRepositoryImpl(get(), get()) }
    single<DbViewerRepository> { DbViewerRepositoryImpl(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get()) }
    single<BookRepository> { BookRepositoryImpl(get()) }
}
