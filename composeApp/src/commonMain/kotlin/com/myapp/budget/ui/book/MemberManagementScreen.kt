package com.myapp.budget.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.BookMember
import com.myapp.budget.domain.model.MemberRole
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberManagementScreen(
    bookId: String,
    onBack: () -> Unit,
) {
    val viewModel: BookViewModel = koinViewModel()
    val sessionManager: SessionManager = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by sessionManager.currentUser.collectAsState()
    val books by viewModel.books.collectAsState()
    val book = books.firstOrNull { it.id == bookId }
    val isOwner = book?.ownerId == currentUser?.id

    LaunchedEffect(bookId) { viewModel.loadMembers(bookId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOwner) "멤버 관리" else "멤버 목록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(uiState.members) { member ->
                    MemberItem(
                        member = member,
                        isOwner = isOwner,
                        isCurrentUser = member.userId == currentUser?.id,
                        onRemove = { viewModel.removeMember(bookId, member.userId) },
                        onRoleChange = { role -> viewModel.updateMemberRole(bookId, member.userId, role) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MemberItem(
    member: BookMember,
    isOwner: Boolean,
    isCurrentUser: Boolean,
    onRemove: () -> Unit,
    onRoleChange: (MemberRole) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("멤버 강퇴") },
            text = { Text("이 멤버를 강퇴하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { onRemove(); showRemoveDialog = false }) { Text("강퇴") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("취소") }
            },
        )
    }

    val roleLabel = when (member.role) {
        MemberRole.OWNER -> "오너"
        MemberRole.EDITOR -> "편집자"
        MemberRole.VIEWER -> "뷰어"
    }

    ListItem(
        headlineContent = { Text(member.displayName.ifBlank { member.userId.take(8) + "..." }) },
        supportingContent = { Text(roleLabel) },
        trailingContent = {
            if (isOwner && !isCurrentUser && member.role != MemberRole.OWNER) {
                Column {
                    TextButton(onClick = { showMenu = true }) { Text("관리") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("편집자로 변경") },
                            onClick = { onRoleChange(MemberRole.EDITOR); showMenu = false },
                            enabled = member.role != MemberRole.EDITOR,
                        )
                        DropdownMenuItem(
                            text = { Text("뷰어로 변경") },
                            onClick = { onRoleChange(MemberRole.VIEWER); showMenu = false },
                            enabled = member.role != MemberRole.VIEWER,
                        )
                        DropdownMenuItem(
                            text = { Text("강퇴", color = MaterialTheme.colorScheme.error) },
                            onClick = { showRemoveDialog = true; showMenu = false },
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
