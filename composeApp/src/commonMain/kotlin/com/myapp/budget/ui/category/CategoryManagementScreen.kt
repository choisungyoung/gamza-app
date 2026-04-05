package com.myapp.budget.ui.category

import com.myapp.budget.platform.OnBackPressed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.myapp.budget.ui.components.EmojiText
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.IncomeColor
import com.myapp.budget.ui.theme.TransferColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDeep
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/** 3줄 드래그 핸들 아이콘 */
@Composable
private fun ThreeLineDragHandle(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val lineH = 2.dp.toPx()
        val totalLines = 3
        val gap = if (size.height > lineH * totalLines)
            (size.height - lineH * totalLines) / (totalLines - 1) else 2.dp.toPx()
        repeat(totalLines) { i ->
            val y = i * (lineH + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, y),
                size = Size(size.width, lineH),
                cornerRadius = CornerRadius(lineH / 2)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit = {},
    viewModel: CategoryManagementViewModel = koinViewModel()
) {
    OnBackPressed(enabled = true) { onBack() }

    val selectedType by viewModel.selectedType.collectAsState()
    val expenseParents by viewModel.expenseParents.collectAsState()
    val incomeParents by viewModel.incomeParents.collectAsState()
    val transferParents by viewModel.transferParents.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

    val currentParents = when (selectedType) {
        TransactionType.EXPENSE -> expenseParents
        TransactionType.INCOME -> incomeParents
        TransactionType.TRANSFER -> transferParents
    }
    val currentCategories = when (selectedType) {
        TransactionType.EXPENSE -> expenseCategories
        TransactionType.INCOME -> incomeCategories
        TransactionType.TRANSFER -> emptyMap()
    }
    val accentColor = when (selectedType) {
        TransactionType.EXPENSE -> ExpenseColor
        TransactionType.INCOME -> IncomeColor
        TransactionType.TRANSFER -> TransferColor
    }
    val tabIndex = when (selectedType) {
        TransactionType.EXPENSE -> 0
        TransactionType.INCOME -> 1
        TransactionType.TRANSFER -> 2
    }

    var editParentTarget by remember { mutableStateOf<ParentCategory?>(null) }
    var addSubcatParent by remember { mutableStateOf<ParentCategory?>(null) }
    var editSubcatTarget by remember { mutableStateOf<UserCategory?>(null) }
    var deleteSubcatTarget by remember { mutableStateOf<UserCategory?>(null) }
    var showAddParentDialog by remember { mutableStateOf(false) }

    // 대분류 펼침/접힘 상태 (기본: 접혀있음)
    var expandedParents by remember { mutableStateOf(setOf<Long>()) }

    // 대분류 드래그앤드랍 상태
    var parentDragIdx by remember { mutableStateOf<Int?>(null) }
    var parentDragOffsetY by remember { mutableStateOf(0f) }
    val parentTopY = remember { mutableMapOf<Int, Float>() }
    val parentHeightPx = remember { mutableMapOf<Int, Float>() }

    deleteSubcatTarget?.let { sub ->
        AlertDialog(
            onDismissRequest = { deleteSubcatTarget = null },
            title = { Text("소분류 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("「${sub.name}」을(를) 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubcategory(sub.id)
                        if (editSubcatTarget?.id == sub.id) editSubcatTarget = null
                        deleteSubcatTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColor)
                ) { Text("삭제", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSubcatTarget = null }) { Text("취소") }
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        )
    }

    if (showAddParentDialog) {
        CategoryInputDialog(
            title = "대분류 추가",
            initialName = "",
            initialEmoji = "",
            onConfirm = { name, emoji ->
                viewModel.addParent(name, emoji)
                showAddParentDialog = false
            },
            onDismiss = { showAddParentDialog = false }
        )
    }

    editParentTarget?.let { parent ->
        CategoryInputDialog(
            title = "${parent.emoji} 대분류 수정",
            initialName = parent.name,
            initialEmoji = parent.emoji,
            onConfirm = { name, emoji ->
                viewModel.updateParent(parent.id, name, emoji)
                editParentTarget = null
            },
            onDismiss = { editParentTarget = null }
        )
    }

    addSubcatParent?.let { parent ->
        CategoryInputDialog(
            title = "${parent.emoji} ${parent.name} — 소분류 추가",
            initialName = "",
            initialEmoji = "",
            onConfirm = { name, emoji ->
                viewModel.addSubcategory(name, emoji, parent.id, selectedType)
                addSubcatParent = null
            },
            onDismiss = { addSubcatParent = null }
        )
    }

    editSubcatTarget?.let { sub ->
        CategoryInputDialog(
            title = "소분류 수정",
            initialName = sub.name,
            initialEmoji = sub.emoji,
            showDelete = true,
            onConfirm = { name, emoji ->
                viewModel.updateSubcategory(sub.id, name, emoji)
                editSubcatTarget = null
            },
            onDelete = {
                editSubcatTarget = null
                deleteSubcatTarget = sub
            },
            onDismiss = { editSubcatTarget = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카테고리 관리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기",
                            tint = Color.White)
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
                onClick = { showAddParentDialog = true },
                containerColor = accentColor,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "대분류 추가", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = Color.White,
                contentColor = PotatoBrown,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                        color = accentColor
                    )
                }
            ) {
                Tab(
                    selected = selectedType == TransactionType.EXPENSE,
                    onClick = { viewModel.selectedType.value = TransactionType.EXPENSE },
                    text = {
                        Text(
                            "지출",
                            fontWeight = if (selectedType == TransactionType.EXPENSE) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedType == TransactionType.EXPENSE) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedType == TransactionType.INCOME,
                    onClick = { viewModel.selectedType.value = TransactionType.INCOME },
                    text = {
                        Text(
                            "수입",
                            fontWeight = if (selectedType == TransactionType.INCOME) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedType == TransactionType.INCOME) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Tab(
                    selected = selectedType == TransactionType.TRANSFER,
                    onClick = { viewModel.selectedType.value = TransactionType.TRANSFER },
                    text = {
                        Text(
                            "이체",
                            fontWeight = if (selectedType == TransactionType.TRANSFER) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedType == TransactionType.TRANSFER) TransferColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentParents.forEachIndexed { index, parent ->
                    val isDragging = parentDragIdx == index
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                parentTopY[index] = coords.positionInRoot().y
                                parentHeightPx[index] = coords.size.height.toFloat()
                            }
                            .offset { IntOffset(0, if (isDragging) parentDragOffsetY.roundToInt() else 0) }
                            .zIndex(if (isDragging) 1f else 0f)
                    ) {
                        ParentCategoryCard(
                            parent = parent,
                            subcategories = currentCategories[parent.id] ?: emptyList(),
                            accentColor = accentColor,
                            isExpanded = expandedParents.contains(parent.id),
                            showAddSubcat = selectedType != TransactionType.TRANSFER,
                            onToggleExpand = {
                                expandedParents = if (expandedParents.contains(parent.id))
                                    expandedParents - parent.id
                                else
                                    expandedParents + parent.id
                            },
                            onEditParent = { editParentTarget = parent },
                            onAddSubcat = { addSubcatParent = parent },
                            onEditSubcat = { editSubcatTarget = it },
                            onDeleteSubcat = { deleteSubcatTarget = it },
                            onReorderSubcat = { from, to ->
                                viewModel.reorderSubcategories(from, to, parent.id)
                            },
                            parentDragHandleModifier = Modifier.pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        parentDragIdx = index
                                        parentDragOffsetY = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        parentDragOffsetY += amount.y
                                    },
                                    onDragEnd = {
                                        val di = parentDragIdx
                                            ?: return@detectDragGesturesAfterLongPress
                                        val draggedCenter =
                                            (parentTopY[di] ?: 0f) + parentDragOffsetY +
                                                    (parentHeightPx[di] ?: 0f) / 2f
                                        var targetIdx = di
                                        var minDist = Float.MAX_VALUE
                                        for (i in currentParents.indices) {
                                            if (i == di) continue
                                            val top = parentTopY[i] ?: continue
                                            val center = top + (parentHeightPx[i] ?: 0f) / 2f
                                            val dist = abs(draggedCenter - center)
                                            if (dist < minDist) {
                                                minDist = dist
                                                targetIdx = i
                                            }
                                        }
                                        if (targetIdx != di) viewModel.reorderParents(di, targetIdx)
                                        parentDragIdx = null
                                        parentDragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        parentDragIdx = null
                                        parentDragOffsetY = 0f
                                    }
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ParentCategoryCard(
    parent: ParentCategory,
    subcategories: List<UserCategory>,
    accentColor: Color,
    isExpanded: Boolean,
    showAddSubcat: Boolean = true,
    onToggleExpand: () -> Unit,
    onEditParent: () -> Unit,
    onAddSubcat: () -> Unit,
    onEditSubcat: (UserCategory) -> Unit,
    onDeleteSubcat: (UserCategory) -> Unit,
    onReorderSubcat: (fromIndex: Int, toIndex: Int) -> Unit,
    parentDragHandleModifier: Modifier = Modifier,
) {
    // 소분류 드래그앤드랍 상태 (카드 내부)
    var subDragIdx by remember { mutableStateOf<Int?>(null) }
    var subDragOffsetY by remember { mutableStateOf(0f) }
    val subTopY = remember { mutableMapOf<Int, Float>() }
    val subHeightPx = remember { mutableMapOf<Int, Float>() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 대분류 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 대분류 드래그 핸들 (터치 영역 넓게)
                Box(
                    modifier = parentDragHandleModifier
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ThreeLineDragHandle(
                        modifier = Modifier.size(22.dp, 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EmojiText(parent.emoji, fontSize = 20.sp)
                    }
                    Text(
                        text = parent.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PotatoDeep
                    )
                    if (subcategories.isNotEmpty()) {
                        Text(
                            text = "${subcategories.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditParent, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "수정",
                            modifier = Modifier.size(16.dp), tint = PotatoBrown)
                    }
                    if (showAddSubcat) {
                        IconButton(onClick = onAddSubcat, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "소분류 추가",
                                modifier = Modifier.size(20.dp), tint = accentColor)
                        }
                    }
                    if (showAddSubcat) {
                        IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "접기" else "펼치기",
                                modifier = Modifier.size(20.dp),
                                tint = PotatoBrown
                            )
                        }
                    }
                }
            }

            // 소분류 목록 (접기/펼치기)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    if (subcategories.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    subcategories.forEachIndexed { idx, sub ->
                        val isSubDragging = subDragIdx == idx
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    subTopY[idx] = coords.positionInRoot().y
                                    subHeightPx[idx] = coords.size.height.toFloat()
                                }
                                .offset { IntOffset(0, if (isSubDragging) subDragOffsetY.roundToInt() else 0) }
                                .zIndex(if (isSubDragging) 1f else 0f)
                        ) {
                            Column {
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 소분류 드래그 핸들 (터치 영역 넓게)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .pointerInput(idx) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        subDragIdx = idx
                                                        subDragOffsetY = 0f
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        subDragOffsetY += amount.y
                                                    },
                                                    onDragEnd = {
                                                        val di = subDragIdx
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val draggedCenter =
                                                            (subTopY[di] ?: 0f) + subDragOffsetY +
                                                                    (subHeightPx[di] ?: 0f) / 2f
                                                        var targetIdx = di
                                                        var minDist = Float.MAX_VALUE
                                                        for (i in subcategories.indices) {
                                                            if (i == di) continue
                                                            val top = subTopY[i] ?: continue
                                                            val center = top + (subHeightPx[i] ?: 0f) / 2f
                                                            val dist = abs(draggedCenter - center)
                                                            if (dist < minDist) {
                                                                minDist = dist
                                                                targetIdx = i
                                                            }
                                                        }
                                                        if (targetIdx != di) onReorderSubcat(di, targetIdx)
                                                        subDragIdx = null
                                                        subDragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        subDragIdx = null
                                                        subDragOffsetY = 0f
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ThreeLineDragHandle(
                                            modifier = Modifier.size(18.dp, 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        EmojiText(sub.emoji, fontSize = 14.sp)
                                        Text(
                                            sub.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onEditSubcat(sub) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "수정",
                                                modifier = Modifier.size(14.dp), tint = PotatoBrown)
                                        }
                                        IconButton(onClick = { onDeleteSubcat(sub) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "삭제",
                                                modifier = Modifier.size(14.dp), tint = ExpenseColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (subcategories.isEmpty()) {
                        Text(
                            text = "+ 버튼으로 소분류를 추가하세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryInputDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    showDelete: Boolean = false,
    onConfirm: (name: String, emoji: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = PotatoDeep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("이모지") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PotatoBrown,
                        focusedLabelColor = PotatoBrown
                    )
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                onClick = { if (name.isNotBlank()) onConfirm(name, emoji) },
                colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
            ) { Text("확인", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (showDelete && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColor)
                    ) { Text("삭제") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
