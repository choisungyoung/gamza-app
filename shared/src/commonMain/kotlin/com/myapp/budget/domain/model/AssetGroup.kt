package com.myapp.budget.domain.model

data class AssetGroup(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val key: String,
    val sortOrder: Int = 0,
    val isLiability: Boolean = false
)
