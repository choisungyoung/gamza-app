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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                FixedExpenseListCard(
                    fixedExpenses = state.fixedExpenses,
                    onEdit = { fe, title, amount, day, note ->
                        viewModel.updateFixedExpense(fe.id, title, amount, day, note)
                    }
                )
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
private fun FixedExpenseListCard(
    fixedExpenses: List<FixedExpense>,
    onEdit: (FixedExpense, title: String, amount: Long, dayOfMonth: Int, note: String) -> Unit
) {
    var editingItem by remember { mutableStateOf<FixedExpense?>(null) }

    editingItem?.let { item ->
        FixedExpenseEditDialog(
            item = item,
            onConfirm = { title, amount, day, note ->
                onEdit(item, title, amount, day, note)
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingItem = item },
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

@Composable
private fun FixedExpenseEditDialog(
    item: FixedExpense,
    onConfirm: (title: String, amount: Long, dayOfMonth: Int, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var amountStr by remember { mutableStateOf(item.amount.toString()) }
    var dayStr by remember { mutableStateOf(item.dayOfMonth.toString()) }
    var note by remember { mutableStateOf(item.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("고정 지출 수정", fontWeight = FontWeight.Bold, color = PotatoDeep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PotatoBrown,
                        focusedLabelColor = PotatoBrown
                    )
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter(Char::isDigit) },
                    label = { Text("금액 (원)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PotatoBrown,
                        focusedLabelColor = PotatoBrown
                    )
                )
                OutlinedTextField(
                    value = dayStr,
                    onValueChange = { v -> dayStr = v.filter(Char::isDigit).take(2) },
                    label = { Text("결제일 (1~31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PotatoBrown,
                        focusedLabelColor = PotatoBrown
                    )
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("메모") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PotatoBrown,
                        focusedLabelColor = PotatoBrown
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountStr.toLongOrNull() ?: return@TextButton
                    val day = dayStr.toIntOrNull()?.coerceIn(1, 31) ?: return@TextButton
                    if (title.isNotBlank()) onConfirm(title.trim(), amount, day, note.trim())
                },
                colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
            ) { Text("수정", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        shape = RoundedCornerShape(20.dp)
    )
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
