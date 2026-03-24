package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.FixedExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface FixedExpenseRepository {
    fun getAll(): Flow<List<FixedExpense>>
    suspend fun insert(fixedExpense: FixedExpense): Long
    suspend fun delete(id: Long)
    suspend fun countLinkedTransactions(id: Long): Long
    suspend fun autoRegisterPending(today: LocalDate)
}
