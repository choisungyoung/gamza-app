package com.myapp.budget.ui.util

import androidx.compose.ui.graphics.Color

fun String.toComposeColor(): Color {
    val hex = this.trimStart('#')
    return try {
        val value = hex.toLong(16)
        when (hex.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> Color.Gray
        }
    } catch (_: Exception) {
        Color.Gray
    }
}
