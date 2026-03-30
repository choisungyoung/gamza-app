package com.myapp.budget.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import gamzaapp.composeapp.generated.resources.Res
import gamzaapp.composeapp.generated.resources.potato_character
import org.jetbrains.compose.resources.painterResource

@Composable
fun PotatoCharacter(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.potato_character),
        contentDescription = "감자 캐릭터",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
