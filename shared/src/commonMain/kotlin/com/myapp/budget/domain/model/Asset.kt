package com.myapp.budget.domain.model

data class Asset(
    val id: Long = 0,
    val name: String,
    val emoji: String = "",
    val owner: String = "",
    val initialBalance: Long = 0L,
    val groupKey: String,
    val sortOrder: Int = 0
)
