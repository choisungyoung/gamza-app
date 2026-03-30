package com.myapp.budget.domain.model

data class ParentCategory(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val type: TransactionType,
    val sortOrder: Int = 0
)
