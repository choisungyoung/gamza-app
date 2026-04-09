package com.myapp.budget.domain.model

data class FixedExpense(
    val id: Long = 0,
    val title: String,
    val amount: Long,
    val category: String,
    val asset: String = "",
    val dayOfMonth: Int,
    val startYear: Int,
    val startMonth: Int,
    val note: String = "",
    val isActive: Boolean = true,
    val remoteId: String = ""
)
