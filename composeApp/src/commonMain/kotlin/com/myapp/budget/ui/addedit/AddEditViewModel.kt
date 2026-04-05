package com.myapp.budget.ui.addedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import com.myapp.budget.domain.repository.CategoryRepository
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditViewModel(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val fixedExpenseRepository: FixedExpenseRepository
) : ViewModel() {

    var title by mutableStateOf("")
    var rawAmount by mutableStateOf("")
    var note by mutableStateOf("")
    var selectedAsset by mutableStateOf("")
    var toAsset by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var date by mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    var time by mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.let { LocalTime(it.hour, it.minute) })
    var saveAsFixed by mutableStateOf(false)

    var showAutoRegisterDialog by mutableStateOf(false)
        private set
    var pendingAutoRegisterCount by mutableStateOf(0)
        private set
    private var onSuccessCallback: (() -> Unit)? = null

    // 기존 거래의 고정지출 ID (수정 모드)
    private var loadedFixedExpenseId: Long? = null

    // Y→N 확인 다이얼로그
    var showRemoveFixedDialog by mutableStateOf(false)
        private set

    var transactionType by mutableStateOf(TransactionType.EXPENSE)
        private set
    var selectedParent by mutableStateOf<ParentCategory?>(null)
        private set
    var selectedSubcategory by mutableStateOf<UserCategory?>(null)

    val expenseParents: StateFlow<List<ParentCategory>> =
        categoryRepository.getParentsByType(TransactionType.EXPENSE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeParents: StateFlow<List<ParentCategory>> =
        categoryRepository.getParentsByType(TransactionType.INCOME)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transferParents: StateFlow<List<ParentCategory>> =
        categoryRepository.getParentsByType(TransactionType.TRANSFER)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _parentIdFlow = MutableStateFlow(0L)

    val subcategories: StateFlow<List<UserCategory>> = _parentIdFlow
        .flatMapLatest { id ->
            if (id == 0L) flow { emit(emptyList()) }
            else categoryRepository.getByParent(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var editingId: Long? = null
    val isEditing get() = editingId != null

    fun init(transactionId: Long?) {
        editingId = null
        title = ""
        rawAmount = ""
        note = ""
        selectedAsset = ""
        toAsset = ""
        errorMessage = null
        saveAsFixed = false
        loadedFixedExpenseId = null
        date = Clock.System.todayIn(TimeZone.currentSystemDefault())
        time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.let { LocalTime(it.hour, it.minute) }
        transactionType = TransactionType.EXPENSE
        selectedSubcategory = null
        selectedParent = null
        _parentIdFlow.value = 0L

        viewModelScope.launch {
            if (transactionId != null) {
                isLoading = true
                val transaction = repository.getById(transactionId)
                if (transaction != null) {
                    editingId = transactionId
                    title = transaction.title
                    rawAmount = transaction.amount.toString()
                    transactionType = transaction.type
                    date = transaction.date
                    note = transaction.note
                    selectedAsset = transaction.asset
                    toAsset = transaction.toAsset
                    time = transaction.time
                    loadedFixedExpenseId = transaction.fixedExpenseId
                    saveAsFixed = transaction.fixedExpenseId != null

                    val parts = transaction.category.split("/", limit = 2)
                    val parentName = parts[0]
                    val parents = categoryRepository.getParentsByType(transaction.type).first()
                    val parent = parents.firstOrNull { it.name == parentName }
                    if (parent != null) {
                        selectParent(parent)
                        if (parts.size > 1) {
                            val subs = categoryRepository.getByParent(parent.id).first()
                            selectedSubcategory = subs.firstOrNull { it.name == parts[1] }
                        }
                    }
                }
                isLoading = false
            }
        }
    }

    fun updateType(newType: TransactionType) {
        transactionType = newType
        selectedSubcategory = null
        selectedParent = null
        _parentIdFlow.value = 0L
        selectedAsset = ""
        toAsset = ""
        if (newType != TransactionType.EXPENSE) saveAsFixed = false
    }

    fun selectParent(parent: ParentCategory) {
        selectedParent = parent
        _parentIdFlow.value = parent.id
        selectedSubcategory = null
    }

    fun addSubcategory(name: String, emoji: String) {
        val parent = selectedParent ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.insert(
                UserCategory(name = name.trim(), emoji = emoji.ifBlank { "📌" },
                    parentId = parent.id, type = transactionType)
            )
        }
    }

    fun updateSubcategory(id: Long, name: String, emoji: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.update(id, name.trim(), emoji.ifBlank { "📌" })
            if (selectedSubcategory?.id == id) {
                selectedSubcategory = selectedSubcategory?.copy(name = name.trim(), emoji = emoji.ifBlank { "📌" })
            }
        }
    }

    fun deleteSubcategory(id: Long) {
        viewModelScope.launch {
            categoryRepository.delete(id)
            if (selectedSubcategory?.id == id) selectedSubcategory = null
        }
    }

    fun previousDay() { date = date.minus(DatePeriod(days = 1)) }
    fun nextDay() { date = date.plus(DatePeriod(days = 1)) }

    /**
     * 고정지출 토글 핸들러
     * - Y→N: 확인 다이얼로그 표시
     * - N→Y: 즉시 적용
     */
    fun onFixedExpenseToggled(newValue: Boolean) {
        if (saveAsFixed && !newValue && loadedFixedExpenseId != null) {
            // 기존 고정지출을 해제하려는 경우 → 확인 팝업
            showRemoveFixedDialog = true
        } else {
            saveAsFixed = newValue
        }
    }

    /** Y→N 확인: 이번달부터 고정지출 해제 */
    fun confirmRemoveFixed() {
        val feId = loadedFixedExpenseId ?: return
        viewModelScope.launch {
            // 고정지출 규칙 비활성화
            fixedExpenseRepository.deactivate(feId)
            // 이번달 1일부터 해당 고정지출 연결 해제
            val firstOfMonth = LocalDate(date.year, date.monthNumber, 1).toString()
            fixedExpenseRepository.detachFromDate(feId, firstOfMonth)
            loadedFixedExpenseId = null
            saveAsFixed = false
            showRemoveFixedDialog = false
        }
    }

    fun dismissRemoveFixedDialog() {
        showRemoveFixedDialog = false
    }

    fun save(onSuccess: () -> Unit) {
        val amount = rawAmount.toLongOrNull()
        if (title.isBlank()) { errorMessage = "제목을 입력해주세요."; return }
        if (amount == null || amount <= 0) { errorMessage = "올바른 금액을 입력해주세요."; return }
        if (selectedParent == null) { errorMessage = "카테고리를 선택해주세요."; return }

        if (transactionType == TransactionType.TRANSFER) {
            if (selectedAsset.isBlank()) { errorMessage = "출금계좌를 선택해주세요."; return }
            if (toAsset.isBlank()) { errorMessage = "입금계좌를 선택해주세요."; return }
            if (selectedAsset == toAsset) { errorMessage = "출금계좌와 입금계좌가 같습니다."; return }
        } else {
            if (selectedSubcategory == null) { errorMessage = "세부 카테고리를 선택해주세요."; return }
            if (selectedAsset.isBlank()) {
                errorMessage = if (transactionType == TransactionType.EXPENSE) "결제수단을 선택해주세요." else "입금계좌를 선택해주세요."
                return
            }
        }

        viewModelScope.launch {
            isLoading = true
            runCatching {
                val categoryStr = if (transactionType == TransactionType.TRANSFER) {
                    selectedParent!!.name
                } else {
                    "${selectedParent!!.name}/${selectedSubcategory!!.name}"
                }

                val fixedExpenseId: Long? = when {
                    // 신규 거래 + 고정지출 등록
                    saveAsFixed && transactionType == TransactionType.EXPENSE && editingId == null -> {
                        fixedExpenseRepository.insert(
                            FixedExpense(
                                title = title.trim(),
                                amount = amount,
                                category = categoryStr,
                                asset = selectedAsset,
                                dayOfMonth = date.dayOfMonth,
                                startYear = date.year,
                                startMonth = date.monthNumber,
                                note = note.trim()
                            )
                        )
                    }
                    // 수정 모드 + N→Y (새로 고정지출 등록)
                    saveAsFixed && editingId != null && loadedFixedExpenseId == null -> {
                        fixedExpenseRepository.insert(
                            FixedExpense(
                                title = title.trim(),
                                amount = amount,
                                category = categoryStr,
                                asset = selectedAsset,
                                dayOfMonth = date.dayOfMonth,
                                startYear = date.year,
                                startMonth = date.monthNumber,
                                note = note.trim()
                            )
                        )
                    }
                    // 수정 모드 + 기존 고정지출 유지
                    editingId != null && loadedFixedExpenseId != null -> loadedFixedExpenseId
                    else -> null
                }

                val transaction = Transaction(
                    id = editingId ?: 0,
                    title = title.trim(),
                    amount = amount,
                    type = transactionType,
                    category = categoryStr,
                    categoryEmoji = selectedParent!!.emoji,
                    date = date,
                    time = time,
                    note = note.trim(),
                    asset = selectedAsset,
                    toAsset = toAsset,
                    fixedExpenseId = fixedExpenseId
                )
                if (editingId != null) repository.update(transaction)
                else repository.insert(transaction)

                if (fixedExpenseId != null) {
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    val pending = countPendingMonths(date.year, date.monthNumber, today)
                    if (pending > 0) {
                        pendingAutoRegisterCount = pending
                        onSuccessCallback = onSuccess
                        showAutoRegisterDialog = true
                    } else {
                        onSuccess()
                    }
                } else {
                    onSuccess()
                }
            }.onFailure { e ->
                errorMessage = e.message ?: "저장에 실패했습니다. 네트워크 연결을 확인해주세요."
            }
            isLoading = false
        }
    }

    fun confirmAutoRegister() {
        viewModelScope.launch {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            fixedExpenseRepository.autoRegisterPending(today)
            showAutoRegisterDialog = false
            onSuccessCallback?.invoke()
            onSuccessCallback = null
        }
    }

    fun skipAutoRegister() {
        showAutoRegisterDialog = false
        onSuccessCallback?.invoke()
        onSuccessCallback = null
    }

    private fun countPendingMonths(startYear: Int, startMonth: Int, today: LocalDate): Int {
        var count = 0
        var year = startYear
        var month = startMonth + 1
        if (month > 12) { year++; month = 1 }
        while (year < today.year || (year == today.year && month <= today.monthNumber)) {
            count++
            if (month == 12) { year++; month = 1 } else month++
        }
        return count
    }

    fun delete(onSuccess: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            isLoading = true
            runCatching { repository.delete(id) }
                .onSuccess { onSuccess() }
                .onFailure { e -> errorMessage = e.message ?: "삭제에 실패했습니다. 네트워크 연결을 확인해주세요." }
            isLoading = false
        }
    }
}
