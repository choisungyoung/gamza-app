package com.myapp.budget.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.MonthlySummary
import com.myapp.budget.ui.components.PotatoCharacter
import com.myapp.budget.ui.components.TransactionItem
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.util.formatAsWon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onMenuClick: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val activeBook by viewModel.activeBook.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isLoggedIn = currentUser != null
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.bookSwitchedEvent.collect { book ->
            snackbarHostState.showSnackbar("${book.iconEmoji} ${book.name}로 전환되었습니다")
        }
    }

    LaunchedEffect(syncState.syncSuccess, syncState.error) {
        when {
            syncState.syncSuccess -> {
                snackbarHostState.showSnackbar("동기화 완료")
                viewModel.clearSyncState()
            }
            syncState.error != null -> {
                snackbarHostState.showSnackbar(syncState.error!!)
                viewModel.clearSyncState()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PotatoCharacter(modifier = Modifier.size(32.dp))
                        Text(
                            "감자 가계부",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (isLoggedIn) {
                            activeBook?.let { book ->
                                Text(
                                    "${book.iconEmoji} ${book.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.70f),
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        if (syncState.isSyncing) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.syncCurrentBook() }) {
                                Icon(
                                    Icons.Default.Sync, contentDescription = "동기화",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            Icons.Default.Menu, contentDescription = "메뉴",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PotatoBrown,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = PotatoDark,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "거래 추가", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 월 네비게이션
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { viewModel.previousMonth() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "이전 달",
                            tint = PotatoBrown,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = state.currentMonth,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PotatoDeep,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { viewModel.nextMonth() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "다음 달",
                            tint = PotatoBrown,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 월별 요약 카드
            item {
                MonthlySummaryCard(summary = state.summary, month = state.currentMonth)
            }

            // 카테고리 지출 현황
            if (state.summary.categoryBreakdown.isNotEmpty()) {
                item {
                    CategoryBreakdownCard(
                        breakdown = state.summary.categoryBreakdown,
                        totalExpense = state.summary.totalExpense
                    )
                }
            }

            // 이달의 고정지출 섹션
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ExpenseColor)
                        )
                        Text(
                            text = "이달의 고정지출",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PotatoDeep
                        )
                    }
                    if (state.fixedExpenseTransactions.isNotEmpty()) {
                        Text(
                            text = state.fixedExpenseTransactions.sumOf { it.amount }.formatAsWon(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseColor
                        )
                    }
                }
            }

            if (state.fixedExpenseTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "이번 달 고정지출이 없어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val sorted = state.fixedExpenseTransactions.sortedByDescending { it.date }
                items(sorted, key = { "fixed_tx_${it.id}" }) { tx ->
                    TransactionItem(
                        transaction = tx,
                        onClick = { onTransactionClick(tx.id) },
                        showTime = false
                    )
                }
            }

            // 최근 거래
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "최근 거래",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PotatoDeep
                    )
                }
            }

            if (state.recentTransactions.isEmpty()) {
                item { EmptyPlaceholder() }
            } else {
                items(state.recentTransactions, key = { "recent_${it.id}" }) { tx ->
                    TransactionItem(transaction = tx, onClick = { onTransactionClick(tx.id) })
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(summary: MonthlySummary, month: String) {
    val balanceColor = if (summary.balance >= 0) PotatoDeep else Color(0xFFE05050)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text(
                text = month,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF888888)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "잔액",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = summary.balance.formatAsWon(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = balanceColor,
                fontSize = 36.sp
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE))
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    label = "수입",
                    amount = summary.totalIncome,
                    iconColor = Color(0xFF43A879),
                    icon = "▲"
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color(0xFFEEEEEE))
                )
                SummaryItem(
                    label = "지출",
                    amount = summary.totalExpense,
                    iconColor = Color(0xFFFF8A65),
                    icon = "▼"
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: Long, iconColor: Color, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, color = iconColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = amount.formatAsWon(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = PotatoDeep
        )
    }
}

private val breakdownColors = listOf(
    Color(0xFFFF8066), Color(0xFFF0A040), Color(0xFFFFBD5E),
    Color(0xFF4CC88A), Color(0xFF5BAFF0), Color(0xFFBB82F5)
)

@Composable
private fun CategoryBreakdownCard(breakdown: Map<String, Long>, totalExpense: Long) {
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
                    text = "지출 현황",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PotatoDeep
                )
            }
            Spacer(Modifier.height(16.dp))
            breakdown.entries.toList().take(6).forEachIndexed { idx, (name, amount) ->
                val ratio = if (totalExpense > 0) amount.toFloat() / totalExpense else 0f
                val barColor = breakdownColors[idx % breakdownColors.size]
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(ratio * 100).toInt()}%  ${amount.formatAsWon()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = barColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PotatoCharacter(modifier = Modifier.size(64.dp))
            Text(
                text = "아직 거래가 없어요",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = PotatoDark
            )
            Text(
                text = "+ 버튼으로 첫 거래를 추가해보세요!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
