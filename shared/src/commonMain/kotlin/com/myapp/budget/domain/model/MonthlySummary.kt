package com.myapp.budget.domain.model

data class MonthlySummary(
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val balance: Long = totalIncome - totalExpense,
    val categoryBreakdown: Map<String, Long> = emptyMap()
)
