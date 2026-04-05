package com.myapp.budget.domain

import com.myapp.budget.domain.model.Book
import com.myapp.budget.domain.model.LocalUser
import com.myapp.budget.domain.model.MemberRole
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager {
    private val _currentUser = MutableStateFlow<LocalUser?>(null)
    val currentUser: StateFlow<LocalUser?> = _currentUser.asStateFlow()

    private val _activeBook = MutableStateFlow<Book?>(null)
    val activeBook: StateFlow<Book?> = _activeBook.asStateFlow()

    private val _bookSwitched = MutableSharedFlow<Book>(extraBufferCapacity = 1)
    val bookSwitched: SharedFlow<Book> = _bookSwitched.asSharedFlow()

    private val _currentRole = MutableStateFlow<MemberRole?>(null)
    val currentRole: StateFlow<MemberRole?> = _currentRole.asStateFlow()

    val activeBookId: String? get() = _activeBook.value?.id

    fun setUser(user: LocalUser?) {
        _currentUser.value = user
    }

    fun setActiveBook(book: Book?) {
        _activeBook.value = book
    }

    fun notifyBookSwitched(book: Book) {
        _bookSwitched.tryEmit(book)
    }

    fun setCurrentRole(role: MemberRole?) {
        _currentRole.value = role
    }

    fun clear() {
        _currentUser.value = null
        _activeBook.value = null
        _currentRole.value = null
    }
}
