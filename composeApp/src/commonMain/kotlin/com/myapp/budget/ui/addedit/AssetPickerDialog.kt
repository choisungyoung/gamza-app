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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myapp.budget.domain.model.Asset
import com.myapp.budget.ui.asset.AssetViewModel
import com.myapp.budget.ui.components.EmojiText
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.ui.theme.PotatoLight
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AssetPickerDialog(
    title: String = "결제수단 선택",
    selectedAssetName: String,
    onSelect: (Asset) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AssetViewModel = koinViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val assetsMap by viewModel.assetsMap.collectAsState()

    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PotatoBrown, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                    }
                }

                // "선택 안함" 옵션
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(Asset(name = "", groupKey = "")) }
                        .background(if (selectedAssetName.isEmpty()) PotatoLight.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("선택 안함",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedAssetName.isEmpty()) PotatoBrown
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedAssetName.isEmpty()) FontWeight.SemiBold
                        else FontWeight.Normal)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 자산 목록
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groups.forEachIndexed { index, group ->
                        val assets = assetsMap[group.key] ?: emptyList()
                        val isExpanded = group.id in expandedGroups

                        item(key = "group_${group.id}") {
                            if (index > 0) HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PotatoLight.copy(alpha = 0.2f))
                                    .clickable {
                                        expandedGroups = if (isExpanded)
                                            expandedGroups - group.id
                                        else expandedGroups + group.id
                                    }
                                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EmojiText(group.emoji, fontSize = 16.sp)
                                Text(group.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PotatoDeep,
                                    modifier = Modifier.weight(1f))
                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp
                                    else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isExpanded) {
                            items(assets, key = { "asset_${it.id}" }) { asset ->
                                val isSelected = asset.name == selectedAssetName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) PotatoBrown.copy(alpha = 0.1f)
                                            else Color.Transparent
                                        )
                                        .clickable { onSelect(asset) }
                                        .padding(start = 40.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    EmojiText(asset.emoji.ifEmpty { "💰" }, fontSize = 14.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(asset.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) PotatoBrown
                                            else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.SemiBold
                                            else FontWeight.Normal)
                                        if (asset.owner.isNotEmpty()) {
                                            Text(asset.owner,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 40.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
