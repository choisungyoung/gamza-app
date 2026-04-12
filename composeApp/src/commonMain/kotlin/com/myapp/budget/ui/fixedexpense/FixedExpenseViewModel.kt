package com.myapp.budget.ui.fixedexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FixedExpenseUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

class FixedExpenseViewModel(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val fixedExpenses: StateFlow<List<FixedExpense>> = fixedExpenseRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(FixedExpenseUiState())
    val uiState: StateFlow<FixedExpenseUiState> = _uiState.asStateFlow()

    fun delete(id: Long, remoteId: String, keepTransactions: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = FixedExpenseUiState(isLoading = true)
            runCatching {
                if (keepTransactions) {
                    transactionRepository.detachFixedExpense(id)
                } else {
                    // 로컬 거래 삭제 (Supabase 거래는 repository.delete() 내부에서 FK 해소 목적으로 처리)
                    transactionRepository.deleteByFixedExpenseId(id)
                }
                fixedExpenseRepository.delete(id, remoteId)
            }.onSuccess {
                _uiState.value = FixedExpenseUiState()
                onSuccess()
            }.onFailure { e ->
                _uiState.value = FixedExpenseUiState(error = e.message ?: "삭제에 실패했습니다.")
            }
        }
    }

    fun countLinkedTransactions(id: Long, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            onResult(fixedExpenseRepository.countLinkedTransactions(id))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, isLoading = false)
    }
}
