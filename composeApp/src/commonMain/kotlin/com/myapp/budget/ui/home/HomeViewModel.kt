package com.myapp.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.data.remote.RealtimeManager
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.Category
import com.myapp.budget.domain.model.MonthlySummary
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.BookRepository
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class SyncState(
    val isSyncing: Boolean = false,
    val syncSuccess: Boolean = false,
    val error: String? = null,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val summary: MonthlySummary = MonthlySummary(),
    val recentTransactions: List<Transaction> = emptyList(),
    val fixedExpenseTransactions: List<Transaction> = emptyList(),
    val currentYear: Int = 0,
    val currentMonthNum: Int = 0,
    val currentMonth: String = ""
)

class HomeViewModel(
    private val repository: TransactionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val sessionManager: SessionManager,
    private val bookRepository: BookRepository,
    private val realtimeManager: RealtimeManager,
) : ViewModel() {

    val activeBook = sessionManager.activeBook
    val bookSwitchedEvent = sessionManager.bookSwitched
    val currentUser = sessionManager.currentUser

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun refreshCurrentBook() {
        val bookId = sessionManager.activeBookId ?: return
        viewModelScope.launch {
            _syncState.value = SyncState(isSyncing = true)
            runCatching { bookRepository.syncBookData(bookId) }
                .onSuccess { _syncState.value = SyncState(syncSuccess = true) }
                .onFailure { _syncState.value = SyncState(error = it.message ?: "새로고침 실패") }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState()
    }


    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    init {
        viewModelScope.launch {
            fixedExpenseRepository.autoRegisterPending(today)
        }

        // 활성 가계부가 바뀔 때마다 Realtime 채널 갱신
        sessionManager.activeBook.onEach { book ->
            if (book != null) realtimeManager.startWatching(book.id)
            else realtimeManager.stopWatching()
        }.launchIn(viewModelScope)

        // 원격 변경 감지 시 자동 새로고침 (SQLDelight Flow가 자동으로 UI 갱신)
        realtimeManager.transactionChanges.onEach { bookId ->
            if (bookId == sessionManager.activeBookId) {
                runCatching { bookRepository.syncBookData(bookId) }
            }
        }.launchIn(viewModelScope)
    }

    private val _selectedYear = MutableStateFlow(today.year)
    private val _selectedMonth = MutableStateFlow(today.monthNumber)

    val uiState: StateFlow<HomeUiState> = combine(
        sessionManager.activeBook,
        repository.getAll(),
        _selectedYear,
        _selectedMonth
    ) { book, transactions, year, month ->
        if (book == null) {
            return@combine HomeUiState(
                isLoading = true,
                currentYear = year,
                currentMonthNum = month,
                currentMonth = "${year}년 ${month}월"
            )
        }
        val thisMonth = transactions.filter {
            it.date.year == year && it.date.monthNumber == month
        }
        val income = thisMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = thisMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val breakdown = thisMonth
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { tx ->
                val parent = Category.fromCategoryStr(tx.category)
                val sub = Category.subcategoryOf(tx.category)
                if (sub != null) "${parent.emoji} $sub" else "${parent.emoji} ${parent.displayName}"
            }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .associate { it.key to it.value }

        HomeUiState(
            isLoading = false,
            summary = MonthlySummary(income, expense, income - expense, breakdown),
            recentTransactions = transactions.take(5),
            fixedExpenseTransactions = thisMonth
                .filter { it.fixedExpenseId != null }
                .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.time }),
            currentYear = year,
            currentMonthNum = month,
            currentMonth = "${year}년 ${month}월"
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(
            isLoading = true,
            currentYear = today.year,
            currentMonthNum = today.monthNumber,
            currentMonth = "${today.year}년 ${today.monthNumber}월"
        )
    )

    fun previousMonth() {
        val prev = LocalDate(_selectedYear.value, _selectedMonth.value, 1).minus(DatePeriod(months = 1))
        _selectedYear.value = prev.year
        _selectedMonth.value = prev.monthNumber
    }

    fun nextMonth() {
        val next = LocalDate(_selectedYear.value, _selectedMonth.value, 1).plus(DatePeriod(months = 1))
        _selectedYear.value = next.year
        _selectedMonth.value = next.monthNumber
    }
}
