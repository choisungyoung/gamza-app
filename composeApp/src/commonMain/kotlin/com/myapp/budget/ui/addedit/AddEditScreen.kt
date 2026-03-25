package com.myapp.budget.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.ui.components.EmojiText
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.IncomeColor
import com.myapp.budget.ui.theme.TransferColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.util.dayOfWeekKo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    transactionId: Long?,
    onBack: () -> Unit,
    viewModel: AddEditViewModel = koinViewModel(key = "add_edit_${transactionId}")
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAssetPicker by remember { mutableStateOf(false) }

    var titleFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var noteFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(transactionId) { viewModel.init(transactionId) }
    LaunchedEffect(viewModel.title) {
        if (viewModel.title != titleFieldValue.text)
            titleFieldValue = TextFieldValue(viewModel.title, TextRange(viewModel.title.length))
    }
    LaunchedEffect(viewModel.note) {
        if (viewModel.note != noteFieldValue.text)
            noteFieldValue = TextFieldValue(viewModel.note, TextRange(viewModel.note.length))
    }
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorMessage = null
        }
    }

    val accentColor = when (viewModel.transactionType) {
        TransactionType.EXPENSE -> ExpenseColor
        TransactionType.INCOME -> IncomeColor
        TransactionType.TRANSFER -> TransferColor
    }
    var showToAssetPicker by remember { mutableStateOf(false) }

    // ── DatePicker ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.date = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC).date
                        }
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("확인", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
                titleContentColor = PotatoDeep,
                headlineContentColor = PotatoBrown,
                weekdayContentColor = PotatoDark,
                selectedDayContainerColor = PotatoBrown,
                selectedDayContentColor = Color.White,
                todayContentColor = PotatoBrown,
                todayDateBorderColor = PotatoBrown
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "날짜 선택",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                },
                headline = {
                    val headlineDate = datePickerState.selectedDateMillis?.let { millis ->
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    }
                    Text(
                        text = if (headlineDate != null)
                            "${headlineDate.year}년 ${headlineDate.monthNumber}월 ${headlineDate.dayOfMonth}일"
                        else "날짜를 선택하세요",
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

    // ── TimePicker ──
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = viewModel.time.hour,
            initialMinute = viewModel.time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("시간 선택", fontWeight = FontWeight.Bold) },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = PotatoBrown.copy(alpha = 0.08f),
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = PotatoDeep,
                        selectorColor = PotatoBrown,
                        timeSelectorSelectedContainerColor = PotatoBrown.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = PotatoBrown,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.time = LocalTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("확인", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("취소") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Auto Register Dialog ──
    if (viewModel.showAutoRegisterDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.skipAutoRegister() },
            title = { Text("이전 달 거래 자동 추가", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${viewModel.pendingAutoRegisterCount}개월치 누락된 거래가 있습니다.\n자동으로 추가할까요?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmAutoRegister() },
                    colors = ButtonDefaults.textButtonColors(contentColor = PotatoBrown)
                ) { Text("추가", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipAutoRegister() }) { Text("건너뛰기") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Delete Dialog ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("거래 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("이 거래를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.delete { onBack() } },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseColor)
                ) { Text("삭제", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Category Picker Dialog ──
    if (showCategoryPicker) {
        if (viewModel.transactionType == TransactionType.TRANSFER) {
            TransferCategoryPickerDialog(
                parents = viewModel.transferParents.collectAsState().value,
                selectedParentId = viewModel.selectedParent?.id,
                accentColor = TransferColor,
                onSelect = { parent ->
                    viewModel.selectParent(parent)
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        } else {
            CategoryPickerDialog(
                transactionType = viewModel.transactionType,
                selectedParentId = viewModel.selectedParent?.id,
                selectedSubcategoryId = viewModel.selectedSubcategory?.id,
                onSelect = { parent, sub ->
                    viewModel.selectParent(parent)
                    viewModel.selectedSubcategory = sub
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        }
    }

    // ── Asset Picker Dialog (출금계좌 / 결제수단 / 수입계좌) ──
    if (showAssetPicker) {
        val assetDialogTitle = when (viewModel.transactionType) {
            TransactionType.EXPENSE -> "결제수단 선택"
            TransactionType.TRANSFER -> "출금계좌 선택"
            else -> "입금계좌 선택"
        }
        AssetPickerDialog(
            title = assetDialogTitle,
            selectedAssetName = viewModel.selectedAsset,
            onSelect = { asset ->
                viewModel.selectedAsset = asset.name
                showAssetPicker = false
            },
            onDismiss = { showAssetPicker = false }
        )
    }

    // ── Asset Picker Dialog (이체 입금계좌) ──
    if (showToAssetPicker) {
        AssetPickerDialog(
            title = "입금계좌 선택",
            selectedAssetName = viewModel.toAsset,
            onSelect = { asset ->
                viewModel.toAsset = asset.name
                showToAssetPicker = false
            },
            onDismiss = { showToAssetPicker = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (transactionId != null) "거래 수정" else "거래 추가",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PotatoBrown,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (viewModel.isEditing) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("삭제", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { viewModel.save { onBack() } },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = if (viewModel.isEditing) "수정 완료" else "저장",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {

            // ────────────────────────────────
            // 폼 리스트
            // ────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(Color.White)
            ) {

                // 분류
                FormRow(label = "분류") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeChip(
                            label = "지출",
                            selected = viewModel.transactionType == TransactionType.EXPENSE,
                            color = ExpenseColor,
                            onClick = { viewModel.updateType(TransactionType.EXPENSE) }
                        )
                        TypeChip(
                            label = "수입",
                            selected = viewModel.transactionType == TransactionType.INCOME,
                            color = IncomeColor,
                            onClick = { viewModel.updateType(TransactionType.INCOME) }
                        )
                        TypeChip(
                            label = "이체",
                            selected = viewModel.transactionType == TransactionType.TRANSFER,
                            color = TransferColor,
                            onClick = { viewModel.updateType(TransactionType.TRANSFER) }
                        )
                    }
                }
                RowDivider()

                // 금액
                FormRow(label = "금액 (원)") {
                    val displayAmount = remember(viewModel.rawAmount) {
                        val formatted = viewModel.rawAmount.toLongOrNull()
                            ?.formatWithCommas() ?: viewModel.rawAmount
                        TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BasicTextField(
                            value = displayAmount,
                            onValueChange = { viewModel.rawAmount = it.text.filter(Char::isDigit) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            cursorBrush = SolidColor(PotatoBrown),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (viewModel.rawAmount.isEmpty()) {
                                        Text(
                                            "0",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Text(
                            "원",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RowDivider()

                // 카테고리
                if (viewModel.transactionType == TransactionType.TRANSFER) {
                    // 이체: 1-depth 카테고리 선택 (다이얼로그 없이 직접 선택)
                    val transferParents by viewModel.transferParents.collectAsState()
                    FormRow(label = "카테고리", onClick = { showCategoryPicker = true }) {
                        Text(
                            text = viewModel.selectedParent?.let { "${it.emoji} ${it.name}" } ?: "선택하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.selectedParent == null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val categoryLabel: String? = when {
                        viewModel.selectedParent == null -> null
                        viewModel.selectedSubcategory == null ->
                            "${viewModel.selectedParent!!.emoji} ${viewModel.selectedParent!!.name}"
                        else ->
                            "${viewModel.selectedParent!!.emoji} ${viewModel.selectedParent!!.name} / ${viewModel.selectedSubcategory!!.name}"
                    }
                    FormRow(
                        label = "카테고리",
                        onClick = { showCategoryPicker = true }
                    ) {
                        Text(
                            text = categoryLabel ?: "선택하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (categoryLabel == null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RowDivider()

                // 제목
                FormRow(label = "제목") {
                    InlineTextField(
                        value = titleFieldValue,
                        onValueChange = {
                            titleFieldValue = it
                            viewModel.title = it.text
                        },
                        placeholder = "입력하세요",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                RowDivider()

                // 날짜 + 시간
                FormRow(label = "날짜") {
                    Text(
                        text = with(viewModel.date) {
                            "${year}년 ${monthNumber}월 ${dayOfMonth}일 (${dayOfWeekKo()})"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true }
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${viewModel.time.hour.toString().padStart(2, '0')}:${viewModel.time.minute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                RowDivider()

                // 결제수단 / 입금계좌 / 이체 계좌
                if (viewModel.transactionType == TransactionType.TRANSFER) {
                    // 출금계좌
                    FormRow(label = "출금계좌", onClick = { showAssetPicker = true }) {
                        Text(
                            text = viewModel.selectedAsset.ifEmpty { "선택하세요" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.selectedAsset.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RowDivider()
                    // 입금계좌
                    FormRow(label = "입금계좌", onClick = { showToAssetPicker = true }) {
                        Text(
                            text = viewModel.toAsset.ifEmpty { "선택하세요" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.toAsset.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val assetLabel = if (viewModel.transactionType == TransactionType.EXPENSE)
                        "결제수단" else "입금계좌"
                    FormRow(label = assetLabel, onClick = { showAssetPicker = true }) {
                        Text(
                            text = viewModel.selectedAsset.ifEmpty { "선택하세요" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.selectedAsset.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RowDivider()

                // 메모
                FormRow(label = "메모") {
                    InlineTextField(
                        value = noteFieldValue,
                        onValueChange = {
                            noteFieldValue = it
                            viewModel.note = it.text
                        },
                        placeholder = "입력하세요",
                        singleLine = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                RowDivider()

                // 고정 지출 토글 (신규 지출 입력 시에만 표시)
                if (viewModel.transactionType == TransactionType.EXPENSE && !viewModel.isEditing) {
                    FormRow(label = "고정 지출") {
                        Switch(
                            checked = viewModel.saveAsFixed,
                            onCheckedChange = { viewModel.saveAsFixed = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PotatoBrown,
                                checkedTrackColor = PotatoBrown.copy(alpha = 0.4f)
                            )
                        )
                    }
                    RowDivider()
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Helper Composables
// ────────────────────────────────────────────────────────

/** 라벨 + 값 형태의 폼 행 */
@Composable
private fun FormRow(
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        content()
    }
}

/** 행 사이 구분선 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** 수입/지출 타입 선택 칩 */
@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .background(if (selected) color.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 테두리 없는 인라인 텍스트 입력 (BasicTextField 기반) */
@Composable
private fun InlineTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = singleLine,
            cursorBrush = SolidColor(PotatoBrown),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 이체 카테고리 1-depth 선택 다이얼로그 */
@Composable
private fun TransferCategoryPickerDialog(
    parents: List<ParentCategory>,
    selectedParentId: Long?,
    accentColor: Color,
    onSelect: (ParentCategory) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "이체 카테고리",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                TextButton(onClick = onDismiss) {
                    Text("닫기", color = Color.White)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(parents) { parent ->
                    val isSelected = parent.id == selectedParentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(parent) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EmojiText(text = parent.emoji, fontSize = 24.sp)
                        Text(
                            text = parent.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text("✓", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
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
