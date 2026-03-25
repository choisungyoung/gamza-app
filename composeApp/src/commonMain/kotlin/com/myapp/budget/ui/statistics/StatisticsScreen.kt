package com.myapp.budget.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.myapp.budget.ui.components.EmojiText
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.Category
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.IncomeColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.ui.theme.PotatoLight
import com.myapp.budget.util.formatAsWon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onMenuClick: () -> Unit = {},
    viewModel: StatisticsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "통계",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "메뉴", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PotatoBrown,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MonthSummaryCard(
                    month = state.currentMonth,
                    income = state.currentMonthIncome,
                    expense = state.currentMonthExpense
                )
            }

            item {
                MonthlyBarChart(data = state.monthlyData)
            }

            if (state.topCategories.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(categories = state.topCategories)
                }
            }

            item {
                FixedExpenseListCard(fixedExpenses = state.fixedExpenses)
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(month: String, income: Long, expense: Long) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(PotatoBrown, Color(0xFFFFBD5E), PotatoDark)
    )
    val net = income - expense

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = "$month 요약",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(label = "총 수입", amount = income, color = Color(0xFFB9F6CA))
                StatItem(label = "총 지출", amount = expense, color = Color(0xFFFFCDD2))
                StatItem(
                    label = "순이익",
                    amount = net,
                    color = if (net >= 0) Color(0xFFB9F6CA) else Color(0xFFFFCDD2)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, amount: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = amount.formatAsWon(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun MonthlyBarChart(data: List<MonthlyBarData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PotatoBrown)
                )
                Text(
                    text = "월별 수입/지출",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PotatoDeep
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color = IncomeColor, label = "수입")
                LegendItem(color = ExpenseColor, label = "지출")
            }
            Spacer(Modifier.height(16.dp))

            if (data.all { it.income == 0L && it.expense == 0L }) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "데이터가 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val maxValue = data.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1)
                val chartHeight = 160.dp
                val incomeColorVal = IncomeColor
                val expenseColorVal = ExpenseColor
                val trackColor = Color(0xFFF0E8DC)

                Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
                    val totalWidth = size.width
                    val totalHeight = size.height
                    val barGroupWidth = totalWidth / data.size
                    val barWidth = barGroupWidth * 0.28f
                    val gap = barGroupWidth * 0.06f

                    // 배경 가이드라인
                    for (i in 0..4) {
                        val y = totalHeight * (1f - i / 4f)
                        drawLine(
                            color = trackColor,
                            start = Offset(0f, y),
                            end = Offset(totalWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    data.forEachIndexed { index, monthData ->
                        val groupX = barGroupWidth * index + barGroupWidth * 0.12f

                        val incomeRatio = monthData.income.toFloat() / maxValue
                        val incomeHeight = totalHeight * incomeRatio
                        if (incomeHeight > 0) {
                            drawRoundRect(
                                color = incomeColorVal,
                                topLeft = Offset(groupX, totalHeight - incomeHeight),
                                size = Size(barWidth, incomeHeight),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                        }

                        val expenseRatio = monthData.expense.toFloat() / maxValue
                        val expenseHeight = totalHeight * expenseRatio
                        if (expenseHeight > 0) {
                            drawRoundRect(
                                color = expenseColorVal,
                                topLeft = Offset(groupX + barWidth + gap, totalHeight - expenseHeight),
                                size = Size(barWidth, expenseHeight),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    data.forEach { monthData ->
                        Text(
                            text = monthData.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FixedExpenseListCard(fixedExpenses: List<FixedExpense>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PotatoBrown)
                )
                Text(
                    text = "고정 지출 목록",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PotatoDeep
                )
                Spacer(Modifier.weight(1f))
                if (fixedExpenses.isNotEmpty()) {
                    Text(
                        text = "월 합계 ${fixedExpenses.sumOf { it.amount }.formatAsWon()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (fixedExpenses.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmojiText("📋", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "등록된 고정 지출이 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                fixedExpenses.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                    val cat = Category.fromCategoryStr(item.category)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ExpenseColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EmojiText(text = cat.emoji, fontSize = 18.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "매월 ${item.dayOfMonth}일",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = item.amount.formatAsWon(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseColor
                        )
                    }
                }
            }
        }
    }
}

private val categoryColors = listOf(
    Color(0xFFFF8066), Color(0xFFF0A040), Color(0xFFFFBD5E),
    Color(0xFF4CC88A), Color(0xFF5BAFF0), Color(0xFFBB82F5)
)

@Composable
private fun CategoryBreakdownCard(categories: List<CategoryData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ExpenseColor)
                )
                Text(
                    text = "이번달 카테고리별 지출",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PotatoDeep
                )
            }
            Spacer(Modifier.height(16.dp))
            categories.forEachIndexed { idx, cat ->
                val barColor = categoryColors[idx % categoryColors.size]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(barColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EmojiText(text = cat.emoji, fontSize = 20.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = cat.amount.formatAsWon(),
                                style = MaterialTheme.typography.bodySmall,
                                color = barColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(cat.ratio)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor)
                            )
                        }
                        Text(
                            text = "${(cat.ratio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
