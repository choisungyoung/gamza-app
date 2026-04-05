package com.myapp.budget.ui.search

import com.myapp.budget.platform.OnBackPressed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.ui.components.TransactionItem
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.util.dayOfWeekKo
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    OnBackPressed(enabled = true) { onBack() }

    val results by viewModel.results.collectAsState()
    val query by viewModel.query.collectAsState()
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    // ── From DatePicker ──
    if (showFromDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = fromDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            viewModel.setFromDate(
                                Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                            )
                        }
                        showFromDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("확인", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("취소") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
                selectedDayContainerColor = PotatoBrown,
                selectedDayContentColor = Color.White,
                todayContentColor = PotatoBrown,
                todayDateBorderColor = PotatoBrown
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            DatePicker(
                state = state,
                title = {
                    Text("시작 날짜 선택", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
                },
                headline = {
                    val d = state.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    Text(
                        text = if (d != null) "${d.year}년 ${d.monthNumber}월 ${d.dayOfMonth}일" else "날짜를 선택하세요",
                        style = MaterialTheme.typography.headlineLarge,
                        color = PotatoBrown,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = PotatoBrown,
                    selectedDayContentColor = Color.White,
                    todayContentColor = PotatoBrown,
                    todayDateBorderColor = PotatoBrown
                )
            )
        }
    }

    // ── To DatePicker ──
    if (showToDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = toDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            viewModel.setToDate(
                                Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                            )
                        }
                        showToDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("확인", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text("취소") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
                selectedDayContainerColor = PotatoBrown,
                selectedDayContentColor = Color.White,
                todayContentColor = PotatoBrown,
                todayDateBorderColor = PotatoBrown
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            DatePicker(
                state = state,
                title = {
                    Text("종료 날짜 선택", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
                },
                headline = {
                    val d = state.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    Text(
                        text = if (d != null) "${d.year}년 ${d.monthNumber}월 ${d.dayOfMonth}일" else "날짜를 선택하세요",
                        style = MaterialTheme.typography.headlineLarge,
                        color = PotatoBrown,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = PotatoBrown,
                    selectedDayContentColor = Color.White,
                    todayContentColor = PotatoBrown,
                    todayDateBorderColor = PotatoBrown
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("검색", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로",
                            tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PotatoBrown)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // ── 검색창 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(PotatoBrown),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("제목으로 검색",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )
            }

            // ── 기간 선택 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null,
                    tint = PotatoBrown, modifier = Modifier.size(18.dp))
                DateRangeChip(
                    label = "${fromDate.year}.${fromDate.monthNumber.toString().padStart(2,'0')}.${fromDate.dayOfMonth.toString().padStart(2,'0')}",
                    onClick = { showFromDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
                Text("~", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                DateRangeChip(
                    label = "${toDate.year}.${toDate.monthNumber.toString().padStart(2,'0')}.${toDate.dayOfMonth.toString().padStart(2,'0')}",
                    onClick = { showToDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── 유형 필터 ──
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchTypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick = { viewModel.typeFilter.value = filter },
                        label = {
                            Text(
                                text = when (filter) {
                                    SearchTypeFilter.ALL -> "전체"
                                    SearchTypeFilter.INCOME -> "수입"
                                    SearchTypeFilter.EXPENSE -> "지출"
                                    SearchTypeFilter.TRANSFER -> "이체"
                                },
                                fontWeight = if (typeFilter == filter) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PotatoBrown,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // ── 결과 ──
            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 40.sp)
                        Text("검색 결과가 없어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = results.groupBy { it.date }
                    grouped.entries.sortedByDescending { it.key }.forEach { (date, txList) ->
                        item(key = date.toString()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(PotatoBrown)
                                )
                                Text(
                                    text = "${date.year}년 ${date.monthNumber}월 ${date.dayOfMonth}일 (${date.dayOfWeekKo()})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PotatoDark
                                )
                            }
                        }
                        items(txList, key = { it.id }) { tx ->
                            TransactionItem(
                                transaction = tx,
                                onClick = { onTransactionClick(tx.id) }
                            )
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRangeChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, color = PotatoDeep)
    }
}
