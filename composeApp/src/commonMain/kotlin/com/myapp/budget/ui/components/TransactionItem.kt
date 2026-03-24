package com.myapp.budget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.Category
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.IncomeColor
import com.myapp.budget.ui.theme.TransferColor
import com.myapp.budget.util.dayOfWeekKo
import com.myapp.budget.util.formatAsWon
import com.myapp.budget.ui.components.EmojiText

@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = Category.fromCategoryStr(transaction.category)
    val isIncome = transaction.type == TransactionType.INCOME
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val amountColor = when {
        isIncome -> IncomeColor
        isTransfer -> TransferColor
        else -> ExpenseColor
    }
    val amountPrefix = when {
        isIncome -> "+"
        isTransfer -> ""
        else -> "-"
    }
    val bgColor = when {
        isIncome -> IncomeColor.copy(alpha = 0.12f)
        isTransfer -> TransferColor.copy(alpha = 0.12f)
        else -> ExpenseColor.copy(alpha = 0.12f)
    }
    val categoryLabel = if (isTransfer && transaction.toAsset.isNotEmpty()) {
        "${transaction.asset} → ${transaction.toAsset}"
    } else {
        Category.subcategoryOf(transaction.category) ?: category.displayName
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                EmojiText(text = category.emoji, fontSize = 22.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "$amountPrefix${transaction.amount.formatAsWon()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            val timeStr = "${transaction.time.hour.toString().padStart(2, '0')}:${transaction.time.minute.toString().padStart(2, '0')}"
            Text(
                text = "${transaction.date.monthNumber}/${transaction.date.dayOfMonth}(${transaction.date.dayOfWeekKo()}) $timeStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
