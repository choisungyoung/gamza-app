package com.myapp.budget.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.myapp.budget.domain.model.Category
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

data class MonthlyBarData(
    val label: String,
    val income: Long,
    val expense: Long
)

data class CategoryData(
    val emoji: String,
    val name: String,
    val amount: Long,
    val ratio: Float
)

data class StatisticsUiState(
    val monthlyData: List<MonthlyBarData> = emptyList(),
    val topCategories: List<CategoryData> = emptyList(),
    val currentMonthIncome: Long = 0,
    val currentMonthExpense: Long = 0,
    val currentMonth: String = "",
    val fixedExpenses: List<FixedExpense> = emptyList()
)

class StatisticsViewModel(
    private val repository: TransactionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.getAll(),
        fixedExpenseRepository.getAll()
    ) { transactions, fixedExpenses ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

            val monthlyData = (5 downTo 0).map { monthsBack ->
                val target = LocalDate(today.year, today.monthNumber, 1)
                    .minus(DatePeriod(months = monthsBack))
                val monthly = transactions.filter {
                    it.date.year == target.year && it.date.monthNumber == target.monthNumber
                }
                MonthlyBarData(
                    label = "${target.monthNumber}월",
                    income = monthly.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                    expense = monthly.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                )
            }

            val currentMonth = transactions.filter {
                it.date.year == today.year && it.date.monthNumber == today.monthNumber
            }
            val currentIncome = currentMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val currentExpense = currentMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

            val categoryMap = currentMonth
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .take(5)

            val topCategories = categoryMap.map { (name, amount) ->
                val cat = Category.fromCategoryStr(name)
                CategoryData(
                    emoji = cat.emoji,
                    name = Category.subcategoryOf(name) ?: cat.displayName,
                    amount = amount,
                    ratio = if (currentExpense > 0) amount.toFloat() / currentExpense.toFloat() else 0f
                )
            }

            StatisticsUiState(
                monthlyData = monthlyData,
                topCategories = topCategories,
                currentMonthIncome = currentIncome,
                currentMonthExpense = currentExpense,
                currentMonth = "${today.year}년 ${today.monthNumber}월",
                fixedExpenses = fixedExpenses
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun updateFixedExpense(id: Long, title: String, amount: Long, dayOfMonth: Int, note: String) {
        viewModelScope.launch {
            fixedExpenseRepository.update(id, title, amount, dayOfMonth, note)
        }
    }
}
