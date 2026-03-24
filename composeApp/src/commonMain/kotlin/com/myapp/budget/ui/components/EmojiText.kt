package com.myapp.budget.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

@Composable
expect fun EmojiText(text: String, fontSize: TextUnit, modifier: Modifier = Modifier)
