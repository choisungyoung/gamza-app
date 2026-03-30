package com.myapp.budget.ui.datamanagement

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.budget.platform.rememberFilePicker
import com.myapp.budget.platform.rememberFileSaver
import com.myapp.budget.ui.theme.ExpenseColor
import com.myapp.budget.ui.theme.IncomeColor
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoCream
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.ui.theme.PotatoLight
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    viewModel: DataManagementViewModel = koinViewModel()
) {
    BackHandler { onBack() }

    val state by viewModel.uiState.collectAsState()
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val fileSaver = rememberFileSaver()
    val filePicker = rememberFilePicker { bytes ->
        pendingImportBytes = bytes
        showImportConfirm = true
    }

    // 내보내기 준비 이벤트 수신 → 플랫폼 공유 다이얼로그 표시
    LaunchedEffect(Unit) {
        viewModel.exportReady.collect { (bytes, fileName) ->
            fileSaver.save(bytes, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "데이터 관리",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로",
                            tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PotatoBrown,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 안내 카드
            InfoCard()

            // 내보내기 카드
            ActionCard(
                title = "데이터 내보내기",
                description = "거래내역과 고정지출을 엑셀 파일(.xlsx)로 저장합니다.\n저장 후 공유 앱을 통해 원하는 위치에 저장하세요.",
                buttonLabel = "엑셀로 내보내기",
                buttonIcon = Icons.Default.FileDownload,
                buttonColor = PotatoBrown,
                isLoading = state.isExporting,
                onClick = { viewModel.export() }
            )

            // 가져오기 카드
            ActionCard(
                title = "데이터 가져오기",
                description = "감자 가계부에서 내보낸 엑셀 파일을 가져옵니다.\n기존 데이터는 유지되고 새 데이터가 추가됩니다.",
                buttonLabel = "엑셀에서 가져오기",
                buttonIcon = Icons.Default.FileUpload,
                buttonColor = PotatoDark,
                isLoading = state.isImporting,
                onClick = { filePicker.pick() }
            )

            // 결과 메시지
            AnimatedVisibility(
                visible = state.message != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                state.message?.let { msg ->
                    ResultMessage(
                        message = msg,
                        isSuccess = state.isSuccess == true,
                        onDismiss = { viewModel.clearMessage() }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 가져오기 확인 다이얼로그
    if (showImportConfirm) {
        ImportConfirmDialog(
            onConfirm = {
                showImportConfirm = false
                pendingImportBytes?.let { viewModel.import(it) }
                pendingImportBytes = null
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportBytes = null
            }
        )
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PotatoLight.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📊", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "엑셀 데이터 관리",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PotatoDeep
                )
                Text(
                    "거래내역과 고정지출을 엑셀 파일로 백업하거나 복원할 수 있습니다. 내보낸 파일을 PC에서 열어 편집 후 다시 가져올 수도 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PotatoDeep.copy(alpha = 0.7f),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    buttonLabel: String,
    buttonIcon: ImageVector,
    buttonColor: Color,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PotatoDeep
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
            Button(
                onClick = onClick,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResultMessage(message: String, isSuccess: Boolean, onDismiss: () -> Unit) {
    val bgColor = if (isSuccess) IncomeColor.copy(alpha = 0.1f) else ExpenseColor.copy(alpha = 0.1f)
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error
    val iconColor = if (isSuccess) IncomeColor else ExpenseColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = PotatoDeep,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("확인", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ImportConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("데이터 가져오기", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "선택한 파일의 거래내역과 고정지출이 앱에 추가됩니다.\n계속하시겠습니까?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PotatoBrown)
            ) { Text("가져오기") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        },
        containerColor = PotatoCream,
        shape = RoundedCornerShape(20.dp)
    )
}
