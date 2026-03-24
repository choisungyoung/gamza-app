package com.myapp.budget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 감자 테마 컬러 팔레트 (밝고 귀여운 버전)
val PotatoBrown = Color(0xFFF0A040)       // 밝은 감자 골드 (primary)
val PotatoDark = Color(0xFFD4783A)        // 따뜻한 오렌지 브라운
val PotatoDeep = Color(0xFF8B5230)        // 딥 브라운 (텍스트용)
val PotatoLight = Color(0xFFFFE8A0)       // 감자 속살 밝은 노란색
val PotatoCream = Color(0xFFFFFBF0)       // 아주 밝은 크림 배경
val PotatoSkin = Color(0xFFE8C070)        // 따뜻한 골든

// 수입/지출/이체 컬러
val IncomeColor = Color(0xFF4CC88A)       // 밝은 새싹 초록
val ExpenseColor = Color(0xFFFF8066)      // 귀여운 코랄 핑크
val TransferColor = Color(0xFF5B9BD5)     // 이체 블루
val BalancePositiveColor = Color(0xFF4CC88A)
val BalanceNegativeColor = Color(0xFFFF8066)

// 표면 컬러
val CardBackground = Color(0xFFFFFFFF)
val ScreenBackground = PotatoCream

private val AppColorScheme = lightColorScheme(
    primary = PotatoBrown,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PotatoLight,
    onPrimaryContainer = PotatoDeep,
    secondary = PotatoSkin,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFF5DC),
    onSecondaryContainer = PotatoDark,
    tertiary = IncomeColor,
    onTertiary = Color(0xFFFFFFFF),
    background = PotatoCream,
    surface = CardBackground,
    surfaceVariant = Color(0xFFFFF5E0),
    onSurface = Color(0xFF5A3A20),
    onSurfaceVariant = Color(0xFFA07850),
    outline = Color(0xFFECDCC8),
    outlineVariant = Color(0xFFF5E8D4)
)

@Composable
fun BudgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
