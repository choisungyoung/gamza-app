package com.myapp.budget.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import com.myapp.budget.domain.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSettingsScreen(
    bookId: String,
    onBack: () -> Unit,
    onNavigateToEdit: (String, Boolean) -> Unit,
    onNavigateToInvite: (String) -> Unit,
    onNavigateToMembers: (String) -> Unit,
    onBookDeleted: () -> Unit,
    onBookLeft: () -> Unit = {},
) {
    val viewModel: BookViewModel = koinViewModel()
    val sessionManager: SessionManager = koinInject()
    val books by viewModel.books.collectAsState()
    val currentUser by sessionManager.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val book = books.firstOrNull { it.id == bookId }
    val isOwner = book?.ownerId == currentUser?.id

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.leftBook) {
        if (uiState.leftBook) {
            viewModel.clearState()
            onBookLeft()
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("공유 나가기") },
            text = { Text("'${book?.name}' 가계부에서 나가시겠습니까? 다시 참여하려면 초대 코드가 필요합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveBook(bookId)
                        showLeaveDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("나가기") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("취소") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("가계부 삭제") },
            text = { Text("'${book?.name}' 가계부를 삭제하면 모든 데이터가 사라집니다. 정말 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(bookId)
                        showDeleteDialog = false
                        onBookDeleted()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.name ?: "가계부 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(if (isOwner) "가계부 정보 수정" else "가계부 정보 보기") },
                supportingContent = { Text(if (isOwner) "이름, 색상, 아이콘 변경" else "이름, 색상, 아이콘 조회") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToEdit(bookId, !isOwner) },
            )
            HorizontalDivider()
            if (isOwner) {
                ListItem(
                    headlineContent = { Text("초대 코드 생성") },
                    supportingContent = { Text("코드를 공유해서 멤버를 초대하세요") },
                    leadingContent = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToInvite(bookId) },
                )
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text(if (isOwner) "멤버 관리" else "멤버 목록") },
                supportingContent = { Text(if (isOwner) "멤버 역할 변경 및 강퇴" else "공유 멤버 조회") },
                leadingContent = { Icon(Icons.Default.Group, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToMembers(bookId) },
            )
            HorizontalDivider()

            Spacer(Modifier.weight(1f))
            if (isOwner) {
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("가계부 삭제")
                }
            } else {
                Button(
                    onClick = { showLeaveDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("공유 나가기")
                }
            }
        }
    }
}
