package com.myapp.budget.ui.fixedexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FixedTransactionViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val fixedTransactions: StateFlow<List<Transaction>> =
        transactionRepository.getAllFixed()
            .map { list ->
                // title+amount+category+asset 기준 최신 1개씩 대표 항목 추출
                list.groupBy { "${it.title}|${it.amount}|${it.category}|${it.asset}" }
                    .values.map { it.first() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun stopRecurring(transaction: Transaction, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                transactionRepository.update(transaction.copy(isFixed = false))
            }.onSuccess { onSuccess() }
        }
    }
}
