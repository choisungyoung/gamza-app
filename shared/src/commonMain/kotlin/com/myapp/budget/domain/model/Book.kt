package com.myapp.budget.domain.model

data class Book(
    val id: String,
    val name: String,
    val colorHex: String = "#A0522D",
    val iconEmoji: String = "📒",
    val ownerId: String,
    val isSelected: Boolean = false,
)
