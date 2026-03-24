package com.myapp.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.MonthlySummary
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class HomeUiState(
    val summary: MonthlySummary = MonthlySummary(),
    val recentTransactions: List<Transaction> = emptyList(),
    val currentMonth: String = ""
)

class HomeViewModel(
    private val repository: TransactionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            fixedExpenseRepository.autoRegisterPending(today)
        }
    }

    val uiState: StateFlow<HomeUiState> = repository.getAll()
        .map { transactions ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val thisMonth = transactions.filter {
                it.date.year == today.year && it.date.monthNumber == today.monthNumber
            }
            val income = thisMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = thisMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val breakdown = thisMonth
                .filter { it.type == TransactionType.EXPENSE && it.type != TransactionType.TRANSFER }
                .groupBy { transaction ->
                    val parent = com.myapp.budget.domain.model.Category.fromCategoryStr(transaction.category)
                    val sub = com.myapp.budget.domain.model.Category.subcategoryOf(transaction.category)
                    if (sub != null) "${parent.emoji} $sub" else "${parent.emoji} ${parent.displayName}"
                }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value }

            HomeUiState(
                summary = MonthlySummary(income, expense, income - expense, breakdown),
                recentTransactions = transactions.take(5),
                currentMonth = "${today.year}년 ${today.monthNumber}월"
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
