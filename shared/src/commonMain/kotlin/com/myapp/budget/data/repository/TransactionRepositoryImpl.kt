package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TransactionRepositoryImpl(
    private val database: BudgetDatabase
) : TransactionRepository {

    private val queries = database.budgetQueries

    override fun getAll(): Flow<List<Transaction>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toModel() } }

    override suspend fun insert(transaction: Transaction) {
        queries.insert(
            title = transaction.title,
            amount = transaction.amount,
            type = transaction.type.name,
            category = transaction.category,
            date = transaction.date.toString(),
            time = transaction.time.toString(),
            note = transaction.note,
            asset = transaction.asset,
            to_asset = transaction.toAsset,
            fixed_expense_id = transaction.fixedExpenseId
        )
    }

    override suspend fun update(transaction: Transaction) {
        queries.update(
            title = transaction.title,
            amount = transaction.amount,
            type = transaction.type.name,
            category = transaction.category,
            date = transaction.date.toString(),
            time = transaction.time.toString(),
            note = transaction.note,
            asset = transaction.asset,
            to_asset = transaction.toAsset,
            fixed_expense_id = transaction.fixedExpenseId,
            id = transaction.id
        )
    }

    override suspend fun delete(id: Long) {
        queries.deleteById(id)
    }

    override suspend fun getById(id: Long): Transaction? =
        queries.selectById(id).executeAsOneOrNull()?.toModel()

    override suspend fun getByFixedExpenseId(fixedExpenseId: Long): List<Transaction> =
        queries.selectByFixedExpenseId(fixedExpenseId).executeAsList().map { it.toModel() }

    override suspend fun detachFixedExpense(fixedExpenseId: Long) {
        queries.detachFixedExpense(fixedExpenseId)
    }

    override suspend fun deleteByFixedExpenseId(fixedExpenseId: Long) {
        queries.deleteByFixedExpenseId(fixedExpenseId)
    }

    private fun com.myapp.budget.db.TransactionEntity.toModel() = Transaction(
        id = id,
        title = title,
        amount = amount,
        type = TransactionType.valueOf(type),
        category = category,
        date = LocalDate.parse(date),
        time = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime(0, 0)),
        note = note,
        asset = asset,
        toAsset = to_asset,
        fixedExpenseId = fixed_expense_id
    )
}
