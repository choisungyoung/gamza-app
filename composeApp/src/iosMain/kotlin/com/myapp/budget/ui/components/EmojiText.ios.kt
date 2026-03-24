package com.myapp.budget.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

@Composable
actual fun EmojiText(text: String, fontSize: TextUnit, modifier: Modifier) {
    Text(text = text, fontSize = fontSize, modifier = modifier)
}
