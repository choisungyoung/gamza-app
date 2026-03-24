package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    suspend fun insert(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): Transaction?
    suspend fun getByFixedExpenseId(fixedExpenseId: Long): List<Transaction>
    suspend fun detachFixedExpense(fixedExpenseId: Long)
    suspend fun deleteByFixedExpenseId(fixedExpenseId: Long)
}
