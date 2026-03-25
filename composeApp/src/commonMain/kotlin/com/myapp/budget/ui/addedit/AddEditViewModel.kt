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
import com.myapp.budget.domain.repository.AssetRepository
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
    private val assetRepository: AssetRepository,
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

    private val _parentKeyFlow = MutableStateFlow("")

    val subcategories: StateFlow<List<UserCategory>> = _parentKeyFlow
        .flatMapLatest { key ->
            if (key.isEmpty()) flow { emit(emptyList()) }
            else categoryRepository.getByParent(key)
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
        date = Clock.System.todayIn(TimeZone.currentSystemDefault())
        time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.let { LocalTime(it.hour, it.minute) }
        transactionType = TransactionType.EXPENSE
        selectedSubcategory = null
        selectedParent = null
        _parentKeyFlow.value = ""

        viewModelScope.launch {
            categoryRepository.ensureDefaults()
            assetRepository.ensureDefaults()

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

                    val parts = transaction.category.split("/", limit = 2)
                    val parentKey = parts[0]
                    val parents = categoryRepository.getParentsByType(transaction.type).first()
                    val parent = parents.firstOrNull { it.key == parentKey }
                    if (parent != null) {
                        selectParent(parent)
                        if (parts.size > 1) {
                            val subs = categoryRepository.getByParent(parentKey).first()
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
        _parentKeyFlow.value = ""
        selectedAsset = ""
        toAsset = ""
        if (newType != TransactionType.EXPENSE) saveAsFixed = false
    }

    fun selectParent(parent: ParentCategory) {
        selectedParent = parent
        _parentKeyFlow.value = parent.key
        selectedSubcategory = null
    }

    fun addSubcategory(name: String, emoji: String) {
        val parent = selectedParent ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.insert(
                UserCategory(name = name.trim(), emoji = emoji.ifBlank { "📌" },
                    parent = parent.key, type = transactionType)
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
            val categoryStr = if (transactionType == TransactionType.TRANSFER) {
                selectedParent!!.key
            } else {
                "${selectedParent!!.key}/${selectedSubcategory!!.name}"
            }

            val fixedExpenseId: Long? = if (saveAsFixed && transactionType == TransactionType.EXPENSE && editingId == null) {
                val newId = fixedExpenseRepository.insert(
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
                // 시작 월 이후 누락된 월 자동 등록
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                fixedExpenseRepository.autoRegisterPending(today)
                newId
            } else null

            // 고정 지출로 저장한 경우 autoRegisterPending이 이미 시작 월 거래를 생성했으므로 별도 insert 불필요
            if (fixedExpenseId == null) {
                val transaction = Transaction(
                    id = editingId ?: 0,
                    title = title.trim(),
                    amount = amount,
                    type = transactionType,
                    category = categoryStr,
                    date = date,
                    time = time,
                    note = note.trim(),
                    asset = selectedAsset,
                    toAsset = toAsset,
                    fixedExpenseId = null
                )
                if (editingId != null) repository.update(transaction)
                else repository.insert(transaction)
            }
            onSuccess()
        }
    }

    fun delete(onSuccess: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch { repository.delete(id); onSuccess() }
    }
}
