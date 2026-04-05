package com.myapp.budget.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.Book
import com.myapp.budget.domain.model.BookMember
import com.myapp.budget.domain.model.MemberRole
import com.myapp.budget.domain.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val members: List<BookMember> = emptyList(),
    val inviteCode: String? = null,
    val joinedBook: Book? = null,
    val leftBook: Boolean = false,
)

class BookViewModel(
    private val bookRepository: BookRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {


    val books: StateFlow<List<Book>> = bookRepository.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedBook: StateFlow<Book?> = bookRepository.getSelectedBook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentUser = sessionManager.currentUser

    private val _ownerDisplayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val ownerDisplayNames: StateFlow<Map<String, String>> = _ownerDisplayNames.asStateFlow()

    init {
        // books가 바뀔 때마다 오너 닉네임 갱신 (로그인 상태에서만)
        books.onEach { bookList ->
            if (bookList.isNotEmpty() && sessionManager.currentUser.value != null) {
                runCatching { bookRepository.getOwnerDisplayNames() }
                    .onSuccess { _ownerDisplayNames.value = it }
            }
        }.launchIn(viewModelScope)
    }

    private val _uiState = MutableStateFlow(BookUiState())
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

    fun createBook(name: String, colorHex: String, iconEmoji: String) {
        viewModelScope.launch {
            _uiState.value = BookUiState(isLoading = true)
            runCatching { bookRepository.createBook(name, colorHex, iconEmoji) }
                .onSuccess { book ->
                    sessionManager.setActiveBook(book)
                    _uiState.value = BookUiState()
                }
                .onFailure { _uiState.value = BookUiState(error = it.message ?: "가계부 생성에 실패했습니다.") }
        }
    }

    fun selectBook(book: Book) {
        viewModelScope.launch {
            runCatching { bookRepository.selectBook(book.id) }
                .onSuccess {
                    sessionManager.setActiveBook(book)
                    sessionManager.notifyBookSwitched(book)
                    runCatching { bookRepository.syncBookData(book.id) }
                }
        }
    }

    fun leaveBook(bookId: String) {
        viewModelScope.launch {
            _uiState.value = BookUiState(isLoading = true)
            runCatching { bookRepository.leaveBook(bookId) }
                .onSuccess { nextBook ->
                    if (nextBook != null) {
                        sessionManager.setActiveBook(nextBook)
                        sessionManager.notifyBookSwitched(nextBook)
                    } else {
                        sessionManager.setActiveBook(null)
                    }
                    _uiState.value = BookUiState(leftBook = true)
                }
                .onFailure { _uiState.value = BookUiState(error = it.message ?: "공유 나가기에 실패했습니다.") }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            _uiState.value = BookUiState(isLoading = true)
            runCatching { bookRepository.deleteBook(bookId) }
                .onSuccess { _uiState.value = BookUiState() }
                .onFailure { _uiState.value = BookUiState(error = it.message ?: "가계부 삭제에 실패했습니다.") }
        }
    }

    fun generateInviteCode(bookId: String) {
        viewModelScope.launch {
            _uiState.value = BookUiState(isLoading = true)
            runCatching { bookRepository.generateInviteCode(bookId) }
                .onSuccess { code -> _uiState.value = BookUiState(inviteCode = code) }
                .onFailure { _uiState.value = BookUiState(error = it.message ?: "초대 코드 생성에 실패했습니다.") }
        }
    }

    fun joinByInviteCode(code: String) {
        viewModelScope.launch {
            _uiState.value = BookUiState(isLoading = true)
            runCatching { bookRepository.joinByInviteCode(code.trim().uppercase()) }
                .onSuccess { book -> _uiState.value = BookUiState(joinedBook = book) }
                .onFailure { _uiState.value = BookUiState(error = it.message ?: "참여에 실패했습니다. 코드를 확인해주세요.") }
        }
    }

    fun loadMembers(bookId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { bookRepository.getMembers(bookId) }
                .onSuccess { members -> _uiState.value = BookUiState(members = members) }
                .onFailure { _uiState.value = BookUiState(error = it.message) }
        }
    }

    fun removeMember(bookId: String, userId: String) {
        viewModelScope.launch {
            runCatching { bookRepository.removeMember(bookId, userId) }
                .onSuccess { loadMembers(bookId) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateMemberRole(bookId: String, userId: String, role: MemberRole) {
        viewModelScope.launch {
            runCatching { bookRepository.updateMemberRole(bookId, userId, role) }
                .onSuccess { loadMembers(bookId) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateBook(bookId: String, name: String, colorHex: String, iconEmoji: String) {
        viewModelScope.launch {
            _uiState.value = BookUiState(isLoading = true)
            runCatching { bookRepository.updateBook(bookId, name, colorHex, iconEmoji) }
                .onSuccess { _uiState.value = BookUiState() }
                .onFailure { _uiState.value = BookUiState(error = it.message ?: "가계부 수정에 실패했습니다.") }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearState() {
        _uiState.value = BookUiState()
    }
}
