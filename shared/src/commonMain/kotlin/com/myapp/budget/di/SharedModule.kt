package com.myapp.budget.di

import com.myapp.budget.data.repository.AssetRepositoryImpl
import com.myapp.budget.data.repository.CategoryRepositoryImpl
import com.myapp.budget.data.repository.DataExportRepositoryImpl
import com.myapp.budget.data.repository.DbViewerRepositoryImpl
import com.myapp.budget.data.repository.FixedExpenseRepositoryImpl
import com.myapp.budget.data.repository.TransactionRepositoryImpl
import com.myapp.budget.domain.repository.AssetRepository
import com.myapp.budget.domain.repository.CategoryRepository
import com.myapp.budget.domain.repository.DataExportRepository
import com.myapp.budget.domain.repository.DbViewerRepository
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import org.koin.dsl.module

val sharedModule = module {
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<AssetRepository> { AssetRepositoryImpl(get()) }
    single<FixedExpenseRepository> { FixedExpenseRepositoryImpl(get()) }
    single<DataExportRepository> { DataExportRepositoryImpl(get(), get()) }
    single<DbViewerRepository> { DbViewerRepositoryImpl(get()) }
}
