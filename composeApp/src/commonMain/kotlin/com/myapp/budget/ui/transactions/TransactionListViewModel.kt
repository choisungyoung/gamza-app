package com.myapp.budget.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

enum class TransactionFilter { ALL, INCOME, EXPENSE, TRANSFER }

data class TransactionListUiState(
    val transactions: List<Transaction> = emptyList(),
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val currentYear: Int = 0,
    val currentMonth: Int = 0,
    val filter: TransactionFilter = TransactionFilter.ALL
)

class TransactionListViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _year = MutableStateFlow(today.year)
    private val _month = MutableStateFlow(today.monthNumber)
    private val _filter = MutableStateFlow(TransactionFilter.ALL)

    val uiState: StateFlow<TransactionListUiState> = combine(
        repository.getAll(), _year, _month, _filter
    ) { transactions, year, month, filter ->
        val monthly = transactions.filter { it.date.year == year && it.date.monthNumber == month }
        val filtered = when (filter) {
            TransactionFilter.ALL -> monthly
            TransactionFilter.INCOME -> monthly.filter { it.type == TransactionType.INCOME }
            TransactionFilter.EXPENSE -> monthly.filter { it.type == TransactionType.EXPENSE }
            TransactionFilter.TRANSFER -> monthly.filter { it.type == TransactionType.TRANSFER }
        }
        TransactionListUiState(
            transactions = filtered,
            totalIncome = monthly.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            totalExpense = monthly.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            currentYear = year,
            currentMonth = month,
            filter = filter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListUiState(
        currentYear = today.year,
        currentMonth = today.monthNumber
    ))

    fun previousMonth() {
        val prev = LocalDate(_year.value, _month.value, 1).minus(DatePeriod(months = 1))
        _year.value = prev.year
        _month.value = prev.monthNumber
    }

    fun nextMonth() {
        val next = LocalDate(_year.value, _month.value, 1).plus(DatePeriod(months = 1))
        _year.value = next.year
        _month.value = next.monthNumber
    }

    fun setFilter(filter: TransactionFilter) {
        _filter.value = filter
    }
}
