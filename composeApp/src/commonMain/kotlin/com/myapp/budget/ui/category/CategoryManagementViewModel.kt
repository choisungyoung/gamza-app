package com.myapp.budget.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import com.myapp.budget.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryManagementViewModel(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val selectedType = MutableStateFlow(TransactionType.EXPENSE)

    val expenseParents: StateFlow<List<ParentCategory>> =
        categoryRepository.getParentsByType(TransactionType.EXPENSE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeParents: StateFlow<List<ParentCategory>> =
        categoryRepository.getParentsByType(TransactionType.INCOME)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transferParents: StateFlow<List<ParentCategory>> =
        categoryRepository.getParentsByType(TransactionType.TRANSFER)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseCategories: StateFlow<Map<Long, List<UserCategory>>> =
        categoryRepository.getByType(TransactionType.EXPENSE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            .let { flow ->
                kotlinx.coroutines.flow.combine(flow, expenseParents) { subs, _ ->
                    subs.groupBy { it.parentId }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
            }

    val incomeCategories: StateFlow<Map<Long, List<UserCategory>>> =
        categoryRepository.getByType(TransactionType.INCOME)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            .let { flow ->
                kotlinx.coroutines.flow.combine(flow, incomeParents) { subs, _ ->
                    subs.groupBy { it.parentId }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
            }

    init {}

    fun updateParent(id: Long, name: String, emoji: String) {
        viewModelScope.launch { categoryRepository.updateParent(id, name, emoji) }
    }

    fun moveParentUp(id: Long) {
        viewModelScope.launch { categoryRepository.moveParentUp(id, selectedType.value) }
    }

    fun moveParentDown(id: Long) {
        viewModelScope.launch { categoryRepository.moveParentDown(id, selectedType.value) }
    }

    fun reorderParents(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val parents = when (selectedType.value) {
                TransactionType.EXPENSE -> expenseParents.value
                TransactionType.INCOME -> incomeParents.value
                TransactionType.TRANSFER -> transferParents.value
            }
            if (fromIndex == toIndex) return@launch
            val id = parents.getOrNull(fromIndex)?.id ?: return@launch
            val steps = toIndex - fromIndex
            if (steps > 0) {
                repeat(steps) { categoryRepository.moveParentDown(id, selectedType.value) }
            } else {
                repeat(-steps) { categoryRepository.moveParentUp(id, selectedType.value) }
            }
        }
    }

    fun addParent(name: String, emoji: String) {
        if (name.isBlank()) return
        viewModelScope.launch { categoryRepository.insertParent(name, emoji, selectedType.value) }
    }

    fun addSubcategory(name: String, emoji: String, parentId: Long, type: TransactionType) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.insert(
                UserCategory(name = name.trim(), emoji = emoji.ifBlank { "📌" }, parentId = parentId, type = type)
            )
        }
    }

    fun updateSubcategory(id: Long, name: String, emoji: String) {
        if (name.isBlank()) return
        viewModelScope.launch { categoryRepository.update(id, name.trim(), emoji.ifBlank { "📌" }) }
    }

    fun deleteSubcategory(id: Long) {
        viewModelScope.launch { categoryRepository.delete(id) }
    }

    fun moveSubcategoryUp(id: Long, parentId: Long) {
        viewModelScope.launch { categoryRepository.moveSubcategoryUp(id, parentId) }
    }

    fun moveSubcategoryDown(id: Long, parentId: Long) {
        viewModelScope.launch { categoryRepository.moveSubcategoryDown(id, parentId) }
    }

    fun reorderSubcategories(fromIndex: Int, toIndex: Int, parentId: Long) {
        viewModelScope.launch {
            val subs = (when (selectedType.value) {
                TransactionType.EXPENSE -> expenseCategories.value
                TransactionType.INCOME -> incomeCategories.value
                TransactionType.TRANSFER -> emptyMap()
            })[parentId] ?: return@launch
            if (fromIndex == toIndex) return@launch
            val id = subs.getOrNull(fromIndex)?.id ?: return@launch
            val steps = toIndex - fromIndex
            if (steps > 0) {
                repeat(steps) { categoryRepository.moveSubcategoryDown(id, parentId) }
            } else {
                repeat(-steps) { categoryRepository.moveSubcategoryUp(id, parentId) }
            }
        }
    }
}
