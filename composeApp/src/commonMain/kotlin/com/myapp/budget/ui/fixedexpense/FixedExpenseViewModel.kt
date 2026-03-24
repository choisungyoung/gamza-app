package com.myapp.budget.ui.fixedexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FixedExpenseViewModel(
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val fixedExpenses: StateFlow<List<FixedExpense>> = fixedExpenseRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long, keepTransactions: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (keepTransactions) {
                transactionRepository.detachFixedExpense(id)
            } else {
                transactionRepository.deleteByFixedExpenseId(id)
            }
            fixedExpenseRepository.delete(id)
            onSuccess()
        }
    }

    fun countLinkedTransactions(id: Long, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            onResult(fixedExpenseRepository.countLinkedTransactions(id))
        }
    }
}
