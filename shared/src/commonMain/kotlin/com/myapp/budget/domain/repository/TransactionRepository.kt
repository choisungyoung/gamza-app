package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    suspend fun getAllByBookId(bookId: String): List<Transaction>
    suspend fun insert(transaction: Transaction, bookId: String? = null)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): Transaction?
}
