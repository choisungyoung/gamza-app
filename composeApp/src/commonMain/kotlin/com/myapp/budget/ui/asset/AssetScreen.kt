package com.myapp.budget.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.Asset
import com.myapp.budget.domain.model.AssetGroup
import com.myapp.budget.ui.components.EmojiText
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.ui.theme.PotatoLight
import com.myapp.budget.util.formatAsWon
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetScreen(
    onMenuClick: () -> Unit = {},
    canWrite: Boolean = true,
    viewModel: AssetViewModel = koinViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val assetsMap by viewModel.assetsMap.collectAsState()
    val totalAssets by viewModel.totalAssets.collectAsState()
    val assetBalances by viewModel.assetBalances.collectAsState()

    var isEditModeInternal by remember { mutableStateOf(false) }
    val isEditMode = isEditModeInternal && canWrite

    // 대분류 편집 다이얼로그
    var editingGroup by remember { mutableStateOf<AssetGroup?>(null) }
    var editGroupName by remember { mutableStateOf("") }
    var editGroupEmoji by remember { mutableStateOf("") }

    // 자산 추가/편집 다이얼로그
    var addAssetForGroup by remember { mutableStateOf<AssetGroup?>(null) }
    var editingAsset by remember { mutableStateOf<Asset?>(null) }
    var editAssetName by remember { mutableStateOf("") }
    var editAssetEmoji by remember { mutableStateOf("") }
    var editAssetOwner by remember { mutableStateOf("") }
    var editAssetBalance by remember { mutableStateOf(TextFieldValue("")) }

    // 삭제 확인
    var deletingAsset by remember { mutableStateOf<Asset?>(null) }

    // ── 대분류 편집 다이얼로그 ──
    editingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { editingGroup = null },
            title = { Text("대분류 편집", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editGroupEmoji, onValueChange = { editGroupEmoji = it },
                        label = { Text("이모지") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editGroupName, onValueChange = { editGroupName = it },
                        label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGroup(group.id, editGroupName, editGroupEmoji)
                        editingGroup = null
                    },
                    enabled = editGroupName.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("수정", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editingGroup = null }) { Text("취소") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── 자산 추가/편집 다이얼로그 ──
    val showAssetDialog = addAssetForGroup != null || editingAsset != null
    if (showAssetDialog) {
        val dialogTitle = if (editingAsset != null) "자산 편집" else "자산 추가"
        AlertDialog(
            onDismissRequest = { addAssetForGroup = null; editingAsset = null },
            title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editAssetName, onValueChange = { editAssetName = it },
                        label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editAssetOwner, onValueChange = { editAssetOwner = it },
                        label = { Text("주인 (예: 엄마, 공용)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = editAssetBalance,
                        onValueChange = { newValue ->
                            val digits = newValue.text.filter(Char::isDigit)
                            val formatted = digits.toLongOrNull()?.formatWithCommas() ?: digits
                            val cursorPos = newValue.selection.end
                            val digitsBeforeCursor = newValue.text.take(cursorPos).count(Char::isDigit)
                            var newCursor = formatted.length
                            var digitCount = 0
                            for (i in formatted.indices) {
                                if (digitCount == digitsBeforeCursor) { newCursor = i; break }
                                if (formatted[i].isDigit()) digitCount++
                            }
                            editAssetBalance = TextFieldValue(text = formatted, selection = TextRange(newCursor))
                        },
                        label = { Text("초기 잔액 (원)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val balance = editAssetBalance.text.filter(Char::isDigit).toLongOrNull() ?: 0L
                        if (editingAsset != null) {
                            viewModel.updateAsset(editingAsset!!.id, editAssetName,
                                "", editAssetOwner, balance)
                            editingAsset = null
                        } else {
                            viewModel.addAsset(editAssetName, "",
                                editAssetOwner, balance, addAssetForGroup!!.key)
                            addAssetForGroup = null
                        }
                    },
                    enabled = editAssetName.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text(if (editingAsset != null) "수정" else "추가", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { addAssetForGroup = null; editingAsset = null }) { Text("취소") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── 삭제 확인 다이얼로그 ──
    deletingAsset?.let { asset ->
        AlertDialog(
            onDismissRequest = { deletingAsset = null },
            title = { Text("자산 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("'${asset.name}'을(를) 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAsset(asset.id); deletingAsset = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColor)
                ) { Text("삭제", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deletingAsset = null }) { Text("취소") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text("자산", color = Color.White, fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (canWrite) {
                        TextButton(onClick = { isEditModeInternal = !isEditModeInternal }) {
                            Text(
                                if (isEditMode) "완료" else "편집",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "메뉴", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PotatoBrown)
            )
        }
    ) { padding ->
        val totalAssetCount = assetsMap.values.sumOf { it.size }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── 총 자산 카드 ──
            item {
                TotalAssetsCard(totalAssets = totalAssets)
                Spacer(Modifier.height(8.dp))
            }

            // ── 자산 없을 때 안내 ──
            if (totalAssetCount == 0 && groups.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🏦", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "아직 자산이 없어요",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PotatoDeep
                        )
                        Text(
                            "우측 상단 '편집' 버튼을 누르면\n각 그룹에 자산을 추가할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── 그룹별 자산 목록 ──
            groups.forEach { group ->
                val assets = assetsMap[group.key] ?: emptyList()
                val groupSubtotal = assets.sumOf { assetBalances[it.name] ?: it.initialBalance }

                item(key = "group_${group.id}") {
                    AssetGroupHeader(
                        group = group,
                        subtotal = groupSubtotal,
                        isEditMode = isEditMode,
                        onEdit = {
                            editGroupName = group.name
                            editGroupEmoji = group.emoji
                            editingGroup = group
                        }
                    )
                }

                itemsIndexed(assets, key = { _, a -> "asset_${a.id}" }) { _, asset ->
                    AssetRow(
                        asset = asset,
                        currentBalance = assetBalances[asset.name] ?: asset.initialBalance,
                        isEditMode = isEditMode,
                        isLiability = group.isLiability,
                        onEdit = {
                            editAssetName = asset.name
                            editAssetOwner = asset.owner
                            editAssetBalance = TextFieldValue(asset.initialBalance.formatWithCommas())
                            editingAsset = asset
                        },
                        onDelete = { deletingAsset = asset }
                    )
                }

                if (isEditMode) {
                    item(key = "add_${group.key}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editAssetName = ""
                                    editAssetOwner = ""; editAssetBalance = TextFieldValue("")
                                    addAssetForGroup = group
                                }
                                .padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = PotatoBrown)
                            Text("자산 추가", style = MaterialTheme.typography.bodySmall,
                                color = PotatoBrown)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TotalAssetsCard(totalAssets: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(PotatoBrown, PotatoDark)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    Text("총 자산", style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = totalAssets.formatAsWon(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetGroupHeader(
    group: AssetGroup,
    subtotal: Long,
    isEditMode: Boolean,
    onEdit: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PotatoLight.copy(alpha = 0.3f))
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmojiText(group.emoji, fontSize = 18.sp)
            Text(group.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                color = PotatoDeep)
            Text(
                text = (if (group.isLiability) -subtotal else subtotal).formatAsWon(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (group.isLiability) ExpenseColor else PotatoDark
            )
            if (isEditMode) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "편집",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun AssetRow(
    asset: Asset,
    currentBalance: Long,
    isEditMode: Boolean,
    isLiability: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = 40.dp, end = if (isEditMode) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(asset.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                if (asset.owner.isNotEmpty()) {
                    Text(asset.owner, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = (if (isLiability) -currentBalance else currentBalance).formatAsWon(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isLiability) ExpenseColor else PotatoDark
            )
            if (isEditMode) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "편집",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제",
                        modifier = Modifier.size(14.dp),
                        tint = ExpenseColor.copy(alpha = 0.7f))
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 40.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

private fun Long.formatWithCommas(): String {
    val str = toString()
    return buildString {
        str.forEachIndexed { index, c ->
            if (index > 0 && (str.length - index) % 3 == 0) append(',')
            append(c)
        }
    }
}
