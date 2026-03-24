package com.myapp.budget.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

enum class SearchTypeFilter { ALL, INCOME, EXPENSE, TRANSFER }

class SearchViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val query = MutableStateFlow("")
    val fromDate = MutableStateFlow(today.minus(DatePeriod(months = 6)))
    val toDate = MutableStateFlow(today)
    val typeFilter = MutableStateFlow(SearchTypeFilter.ALL)

    val results = combine(
        repository.getAll(),
        query,
        fromDate,
        toDate,
        typeFilter
    ) { all, q, from, to, type ->
        all.filter { tx ->
            tx.date in from..to &&
            matchesType(tx, type) &&
            (q.isBlank() || tx.title.contains(q, ignoreCase = true))
        }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.time })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matchesType(tx: Transaction, filter: SearchTypeFilter): Boolean = when (filter) {
        SearchTypeFilter.ALL -> true
        SearchTypeFilter.INCOME -> tx.type == TransactionType.INCOME
        SearchTypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
        SearchTypeFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
    }

    fun setFromDate(date: LocalDate) {
        val clipped = date.coerceIn(toDate.value.minus(DatePeriod(years = 1)), toDate.value)
        fromDate.value = clipped
    }

    fun setToDate(date: LocalDate) {
        val clipped = date.coerceIn(fromDate.value, today)
        toDate.value = clipped
        // enforce max 1-year window: push fromDate forward if needed
        val earliest = clipped.minus(DatePeriod(years = 1))
        if (fromDate.value < earliest) fromDate.value = earliest
    }
}
