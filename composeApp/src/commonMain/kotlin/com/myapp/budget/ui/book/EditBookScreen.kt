package com.myapp.budget.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.ui.util.toComposeColor
import org.koin.compose.viewmodel.koinViewModel

private val editPresetColors = listOf(
    "#A0522D", "#E07B54", "#E8B86D", "#6BAF92",
    "#5B8EC5", "#8B72BE", "#C97B84", "#7A7A7A",
)
private val editPresetIcons = listOf("📒", "💰", "🏠", "✈️", "🍜", "🎮", "💼", "❤️")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditBookScreen(
    bookId: String,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: BookViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val books by viewModel.books.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val book = books.firstOrNull { it.id == bookId }

    var name by remember(book) { mutableStateOf(book?.name ?: "") }
    var selectedColor by remember(book) { mutableStateOf(book?.colorHex ?: editPresetColors[0]) }
    var selectedIcon by remember(book) { mutableStateOf(book?.iconEmoji ?: editPresetIcons[0]) }

    // 저장 성공 시 이전 화면으로 이동
    var wasSaving by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (wasSaving && !uiState.isLoading && uiState.error == null) {
            onSaved()
        }
        if (uiState.isLoading) wasSaving = true
    }

    uiState.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("오류", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .height(120.dp),
                    )
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(errorMessage)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("오류 메시지 복사")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) { Text("확인") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (readOnly) "가계부 정보" else "가계부 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(selectedColor.toComposeColor().copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(selectedIcon, fontSize = 40.sp)
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (!readOnly) name = it },
                label = { Text("가계부 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = readOnly,
            )

            Text("색상", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editPresetColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor())
                            .then(
                                if (color == selectedColor)
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .then(if (!readOnly) Modifier.clickable { selectedColor = color } else Modifier),
                    )
                }
            }

            Text("아이콘", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editPresetIcons.forEach { icon ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (icon == selectedIcon) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(if (!readOnly) Modifier.clickable { selectedIcon = icon } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, fontSize = 24.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (!readOnly) {
                Button(
                    onClick = { viewModel.updateBook(bookId, name, selectedColor, selectedIcon) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !uiState.isLoading && name.isNotBlank(),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("저장")
                    }
                }
            }
        }
    }
}
