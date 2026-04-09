package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.FixedExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface FixedExpenseRepository {
    fun getAll(): Flow<List<FixedExpense>>
    fun getAllIncludingInactive(): Flow<List<FixedExpense>>
    suspend fun insert(fixedExpense: FixedExpense): Long
    suspend fun update(id: Long, title: String, amount: Long, dayOfMonth: Int, note: String)
    suspend fun delete(id: Long, remoteId: String)
    suspend fun getRemoteId(localId: Long): String
    suspend fun countLinkedTransactions(id: Long): Long
    suspend fun autoRegisterPending(today: LocalDate)
    suspend fun detachFromDate(id: Long, fromDateStr: String)
}
