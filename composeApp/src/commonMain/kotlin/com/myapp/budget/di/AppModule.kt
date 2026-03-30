package com.myapp.budget.di

import com.myapp.budget.ui.addedit.AddEditViewModel
import com.myapp.budget.ui.asset.AssetViewModel
import com.myapp.budget.ui.category.CategoryManagementViewModel
import com.myapp.budget.ui.datamanagement.DataManagementViewModel
import com.myapp.budget.ui.dbviewer.DbViewerViewModel
import com.myapp.budget.ui.fixedexpense.FixedExpenseViewModel
import com.myapp.budget.ui.home.HomeViewModel
import com.myapp.budget.ui.statistics.StatisticsViewModel
import com.myapp.budget.ui.transactions.TransactionListViewModel
import com.myapp.budget.ui.search.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::TransactionListViewModel)
    viewModelOf(::AddEditViewModel)
    viewModelOf(::StatisticsViewModel)
    viewModelOf(::CategoryManagementViewModel)
    viewModelOf(::AssetViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::FixedExpenseViewModel)
    viewModelOf(::DataManagementViewModel)
    viewModelOf(::DbViewerViewModel)
}
