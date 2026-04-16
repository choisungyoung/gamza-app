package com.myapp.budget.ui.fixedexpense

import com.myapp.budget.platform.OnBackPressed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.ui.components.EmojiText
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.util.formatAsWon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedExpenseScreen(
    onBack: () -> Unit,
    viewModel: FixedTransactionViewModel = koinViewModel()
) {
    OnBackPressed(enabled = true) { onBack() }

    val fixedTransactions by viewModel.fixedTransactions.collectAsState()
    var stoppingItem by remember { mutableStateOf<Transaction?>(null) }

    stoppingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { stoppingItem = null },
            title = { Text("고정 지출 중단", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "「${item.title}」을 고정 지출에서 중단합니다.\n다음 달부터 자동 생성되지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.stopRecurring(item) { stoppingItem = null }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColor)
                ) { Text("중단", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { stoppingItem = null }) { Text("취소") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("고정 지출 관리", color = Color.White, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PotatoBrown)
            )
        }
    ) { padding ->
        if (fixedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmojiText("📋", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "등록된 고정 지출이 없습니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "거래 추가 시 고정 지출로 저장할 수 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White)
            ) {
                items(fixedTransactions, key = { it.id }) { item ->
                    FixedTransactionItem(
                        item = item,
                        onStopClick = { stoppingItem = item }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun FixedTransactionItem(
    item: Transaction,
    onStopClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "매월 ${item.date.dayOfMonth}일 · ${item.amount.formatAsWon()}",
                style = MaterialTheme.typography.bodySmall,
                color = ExpenseColor
            )
            if (item.asset.isNotEmpty()) {
                Text(
                    text = item.asset,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onStopClick, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Stop,
                contentDescription = "중단",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
