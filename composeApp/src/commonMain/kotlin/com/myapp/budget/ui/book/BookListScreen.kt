package com.myapp.budget.ui.book

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.domain.model.Book
import com.myapp.budget.domain.model.LocalUser
import com.myapp.budget.platform.OnBackPressed
import com.myapp.budget.ui.util.toComposeColor
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    onBack: () -> Unit,
    onNavigateToCreate: () -> Unit,
    onNavigateToSettings: (String) -> Unit,
    onBookSelected: () -> Unit,
) {
    OnBackPressed(enabled = true, onBack = onBack)

    val viewModel: BookViewModel = koinViewModel()
    val books by viewModel.books.collectAsState()
    val selectedBook by viewModel.selectedBook.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val ownerDisplayNames by viewModel.ownerDisplayNames.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState.joinedBook) {
        if (uiState.joinedBook != null) {
            showJoinDialog = false
            inviteCodeInput = ""
            viewModel.clearState()
            onBookSelected()
        }
    }

    uiState.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("참여 실패") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(errorMessage, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(errorMessage)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("오류 메시지 복사")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("확인") }
            },
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinDialog = false
                inviteCodeInput = ""
                viewModel.clearError()
            },
            title = { Text("초대 코드 입력") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase() },
                        label = { Text("6자리 코드") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (inviteCodeInput.length == 6) {
                                viewModel.joinByInviteCode(inviteCodeInput)
                            }
                        }),
                    )
                    if (uiState.isLoading) {
                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.joinByInviteCode(inviteCodeInput) },
                    enabled = inviteCodeInput.length == 6 && !uiState.isLoading,
                ) { Text("참여하기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinDialog = false
                    inviteCodeInput = ""
                    viewModel.clearError()
                }) { Text("취소") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("가계부 선택") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { showJoinDialog = true }) {
                        Icon(Icons.Default.VpnKey, contentDescription = "초대 코드로 참여")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreate) {
                Icon(Icons.Default.Add, contentDescription = "가계부 만들기")
            }
        },
    ) { padding ->
        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📒", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("가계부가 없습니다.", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "새 가계부를 만들거나 초대 코드로 참여해보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(books) { book ->
                    val myId = currentUser?.id
                    val isShared = myId != null && book.ownerId.isNotBlank() && book.ownerId != myId
                    val ownerName = if (isShared) ownerDisplayNames[book.id] else null
                    BookItem(
                        book = book,
                        isSelected = book.id == selectedBook?.id,
                        isShared = isShared,
                        ownerName = ownerName,
                        onClick = {
                            viewModel.selectBook(book)
                            onBookSelected()
                        },
                        onSettingsClick = { onNavigateToSettings(book.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookItem(
    book: Book,
    isSelected: Boolean,
    isShared: Boolean,
    ownerName: String?,
    onClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(book.colorHex.toComposeColor().copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(book.iconEmoji, fontSize = 24.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (isShared) {
                Text(
                    text = if (ownerName != null) "공유 가계부 · ${ownerName}님" else "공유 가계부",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "설정",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
