package com.myapp.budget.ui.dbviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.DbTableData
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoCream
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.ui.theme.PotatoLight
import org.koin.compose.viewmodel.koinViewModel

private val CELL_WIDTH = 120.dp
private val CELL_PADDING = 6.dp
private val HEADER_BG = PotatoBrown
private val ROW_ODD_BG = Color.White
private val ROW_EVEN_BG = PotatoLight.copy(alpha = 0.25f)
private val BORDER_COLOR = PotatoBrown.copy(alpha = 0.2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbViewerScreen(
    onBack: () -> Unit,
    viewModel: DbViewerViewModel = koinViewModel()
) {
    val tables by viewModel.tables.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(PotatoCream)) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        "🔒 DB 데이터 조회",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        "관리자 전용",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { viewModel.loadData() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "새로고침", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = PotatoDark
            )
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PotatoBrown)
            }
            return@Column
        }

        if (tables.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("데이터 없음", color = PotatoDeep)
            }
            return@Column
        }

        // 탭: 테이블 이름
        ScrollableTabRow(
            selectedTabIndex = selectedTab.coerceAtMost(tables.lastIndex),
            containerColor = PotatoDark,
            contentColor = Color.White,
            edgePadding = 0.dp
        ) {
            tables.forEachIndexed { index, table ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            table.tableName.replace("Entity", ""),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        val currentTable = tables.getOrNull(selectedTab) ?: return@Column
        TableContent(table = currentTable)
    }
}

@Composable
private fun TableContent(table: DbTableData) {
    val horizontalScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 행 수 표시
        Text(
            text = "${table.tableName}  |  ${table.rows.size}행",
            style = MaterialTheme.typography.labelSmall,
            color = PotatoDeep.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // 테이블 (가로 스크롤)
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState)) {
            LazyColumn {
                // 헤더
                item {
                    TableRow(
                        cells = table.columns,
                        isHeader = true,
                        isEven = false
                    )
                }
                // 데이터 행
                itemsIndexed(table.rows) { index, row ->
                    TableRow(
                        cells = row,
                        isHeader = false,
                        isEven = index % 2 == 0
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean, isEven: Boolean) {
    val bgColor = when {
        isHeader -> HEADER_BG
        isEven -> ROW_EVEN_BG
        else -> ROW_ODD_BG
    }
    val textColor = if (isHeader) Color.White else PotatoDeep

    Row(
        modifier = Modifier
            .background(bgColor)
            .border(width = 0.5.dp, color = BORDER_COLOR)
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(CELL_WIDTH)
                    .border(width = 0.5.dp, color = BORDER_COLOR)
                    .padding(horizontal = CELL_PADDING, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
