package com.myapp.budget.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.ui.components.PotatoCharacter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashTop    = Color(0xFFFFF8E0)
private val SplashBottom = Color(0xFFFFE8A0)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale      = remember { Animatable(0.5f) }
    val contentAlpha = remember { Animatable(0f) }
    val screenAlpha  = remember { Animatable(1f) }  // 배경은 처음부터 불투명

    LaunchedEffect(Unit) {
        // 캐릭터·텍스트 동시에 등장
        launch { scale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        contentAlpha.animateTo(1f, tween(500))
        delay(1000)
        // 전체 화면 페이드아웃
        screenAlpha.animateTo(0f, tween(300))
        onFinished()
    }

    // 배경: 항상 불투명 — alpha는 전체 화면 퇴장에만 사용
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha.value)
            .background(Brush.verticalGradient(listOf(SplashTop, SplashBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PotatoCharacter(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "감자가계부",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF7A5A20)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "내 돈을 알뜰하게 관리해요",
                fontSize = 14.sp,
                color = Color(0xFFA07830)
            )
        }
    }
}
