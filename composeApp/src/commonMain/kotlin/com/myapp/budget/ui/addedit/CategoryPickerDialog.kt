package com.myapp.budget.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import com.myapp.budget.ui.category.CategoryManagementViewModel
import com.myapp.budget.ui.components.EmojiText
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.IncomeColor
import com.myapp.budget.ui.theme.PotatoBrown
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoryPickerDialog(
    transactionType: TransactionType,
    selectedParentId: Long?,
    selectedSubcategoryId: Long?,
    onSelect: (ParentCategory, UserCategory) -> Unit,
    onDismiss: () -> Unit,
    viewModel: CategoryManagementViewModel = koinViewModel()
) {
    LaunchedEffect(transactionType) {
        viewModel.selectedType.value = transactionType
    }

    val parents by (
        if (transactionType == TransactionType.EXPENSE) viewModel.expenseParents
        else viewModel.incomeParents
    ).collectAsState()

    val categoriesMap by (
        if (transactionType == TransactionType.EXPENSE) viewModel.expenseCategories
        else viewModel.incomeCategories
    ).collectAsState()

    val accentColor = if (transactionType == TransactionType.EXPENSE) ExpenseColor else IncomeColor

    var expandedParents by remember { mutableStateOf(setOf<Long>()) }

    // 소분류 추가 다이얼로그
    var showAddSubFor by remember { mutableStateOf<Pair<String, TransactionType>?>(null) }
    var addSubName by remember { mutableStateOf("") }
    var addSubEmoji by remember { mutableStateOf("") }

    // 대분류 편집 다이얼로그
    var editingParent by remember { mutableStateOf<ParentCategory?>(null) }
    var editParentName by remember { mutableStateOf("") }
    var editParentEmoji by remember { mutableStateOf("") }

    // 소분류 편집 다이얼로그
    var editingSub by remember { mutableStateOf<UserCategory?>(null) }
    var editSubName by remember { mutableStateOf("") }
    var editSubEmoji by remember { mutableStateOf("") }

    // ── 소분류 추가 다이얼로그 ──
    showAddSubFor?.let { (parentKey, type) ->
        AlertDialog(
            onDismissRequest = { showAddSubFor = null; addSubName = ""; addSubEmoji = "" },
            title = { Text("소분류 추가", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addSubEmoji, onValueChange = { addSubEmoji = it },
                        label = { Text("이모지") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addSubName, onValueChange = { addSubName = it },
                        label = { Text("이름") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addSubcategory(addSubName, addSubEmoji, parentKey, type)
                        showAddSubFor = null; addSubName = ""; addSubEmoji = ""
                    },
                    enabled = addSubName.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("추가", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubFor = null; addSubName = ""; addSubEmoji = "" }) {
                    Text("취소")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── 대분류 편집 다이얼로그 ──
    editingParent?.let { parent ->
        AlertDialog(
            onDismissRequest = { editingParent = null },
            title = { Text("대분류 편집", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editParentEmoji, onValueChange = { editParentEmoji = it },
                        label = { Text("이모지") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editParentName, onValueChange = { editParentName = it },
                        label = { Text("이름") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateParent(parent.id, editParentName, editParentEmoji)
                        editingParent = null
                    },
                    enabled = editParentName.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("수정", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editingParent = null }) { Text("취소") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── 소분류 편집 다이얼로그 ──
    editingSub?.let { sub ->
        AlertDialog(
            onDismissRequest = { editingSub = null },
            title = { Text("소분류 편집", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editSubEmoji, onValueChange = { editSubEmoji = it },
                        label = { Text("이모지") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSubName, onValueChange = { editSubName = it },
                        label = { Text("이름") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSubcategory(sub.id, editSubName, editSubEmoji)
                        editingSub = null
                    },
                    enabled = editSubName.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("수정", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editingSub = null }) { Text("취소") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── 메인 다이얼로그 ──
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            PotatoBrown,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "카테고리 선택",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                    }
                }

                // 카테고리 목록
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    parents.forEachIndexed { index, parent ->
                        val isExpanded = parent.id in expandedParents
                        val subs = categoriesMap[parent.key] ?: emptyList()
                        val isParentSelected = parent.id == selectedParentId

                        item(key = "parent_${parent.id}") {
                            if (index > 0) HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isParentSelected && !isExpanded)
                                            accentColor.copy(alpha = 0.06f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        expandedParents = if (isExpanded)
                                            expandedParents - parent.id
                                        else expandedParents + parent.id
                                    }
                                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                EmojiText(parent.emoji, fontSize = 20.sp)
                                Text(
                                    parent.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        editParentName = parent.name
                                        editParentEmoji = parent.emoji
                                        editingParent = parent
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "편집",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp
                                    else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isExpanded) {
                            items(subs, key = { "sub_${it.id}" }) { sub ->
                                val isSelected = sub.id == selectedSubcategoryId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.1f)
                                            else Color.Transparent
                                        )
                                        .clickable { onSelect(parent, sub) }
                                        .padding(
                                            start = 52.dp, end = 4.dp,
                                            top = 8.dp, bottom = 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    EmojiText(sub.emoji, fontSize = 14.sp)
                                    Text(
                                        sub.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) accentColor
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.SemiBold
                                        else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            editSubName = sub.name
                                            editSubEmoji = sub.emoji
                                            editingSub = sub
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "편집",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteSubcategory(sub.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            modifier = Modifier.size(14.dp),
                                            tint = ExpenseColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            item(key = "add_sub_${parent.key}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            addSubName = ""
                                            addSubEmoji = ""
                                            showAddSubFor = parent.key to transactionType
                                        }
                                        .padding(
                                            start = 52.dp, end = 16.dp,
                                            top = 8.dp, bottom = 12.dp
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor
                                    )
                                    Text(
                                        "소분류 추가",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentColor
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
