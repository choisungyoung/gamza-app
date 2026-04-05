package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.data.remote.TransactionRemoteDto
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.TransactionRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TransactionRepositoryImpl(
    private val database: BudgetDatabase,
    private val sessionManager: SessionManager,
    private val supabase: SupabaseClient,
) : TransactionRepository {

    private val queries = database.budgetQueries

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAll(): Flow<List<Transaction>> =
        sessionManager.activeBook.flatMapLatest { book ->
            val bookId = book?.id ?: ""
            if (bookId.isNotBlank()) {
                queries.selectByBookId(bookId).asFlow().mapToList(Dispatchers.Default)
            } else {
                queries.selectAll().asFlow().mapToList(Dispatchers.Default)
            }
        }.map { list -> list.map { it.toModel() } }

    override suspend fun insert(transaction: Transaction) {
        val bookId = sessionManager.activeBookId ?: ""
        val createdBy = sessionManager.currentUser.value?.id ?: ""
        if (bookId.isNotBlank()) {
            queries.insertWithBookAndCreator(
                title = transaction.title,
                amount = transaction.amount,
                type = transaction.type.name,
                category = transaction.category,
                category_emoji = transaction.categoryEmoji,
                date = transaction.date.toString(),
                time = transaction.time.toString(),
                note = transaction.note,
                asset = transaction.asset,
                to_asset = transaction.toAsset,
                fixed_expense_id = transaction.fixedExpenseId,
                book_id = bookId,
                created_by = createdBy,
            )
            val localId = queries.lastInsertRowId().executeAsOne()
            runCatching {
                val dto = supabase.postgrest.from("transactions").insert(
                    TransactionRemoteDto(bookId = bookId, title = transaction.title,
                        amount = transaction.amount, type = transaction.type.name,
                        category = transaction.category, date = transaction.date.toString(),
                        time = transaction.time.toString(), note = transaction.note,
                        asset = transaction.asset, toAsset = transaction.toAsset, createdBy = createdBy,
                        categoryEmoji = transaction.categoryEmoji)
                ) { select() }.decodeSingle<TransactionRemoteDto>()
                queries.updateTransactionRemoteId(dto.id, localId)
            }
        } else if (createdBy.isNotBlank()) {
            queries.insertWithCreator(
                title = transaction.title,
                amount = transaction.amount,
                type = transaction.type.name,
                category = transaction.category,
                category_emoji = transaction.categoryEmoji,
                date = transaction.date.toString(),
                time = transaction.time.toString(),
                note = transaction.note,
                asset = transaction.asset,
                to_asset = transaction.toAsset,
                fixed_expense_id = transaction.fixedExpenseId,
                created_by = createdBy,
            )
        } else {
            queries.insert(
                title = transaction.title,
                amount = transaction.amount,
                type = transaction.type.name,
                category = transaction.category,
                category_emoji = transaction.categoryEmoji,
                date = transaction.date.toString(),
                time = transaction.time.toString(),
                note = transaction.note,
                asset = transaction.asset,
                to_asset = transaction.toAsset,
                fixed_expense_id = transaction.fixedExpenseId
            )
        }
    }

    override suspend fun update(transaction: Transaction) {
        queries.update(
            title = transaction.title,
            amount = transaction.amount,
            type = transaction.type.name,
            category = transaction.category,
            category_emoji = transaction.categoryEmoji,
            date = transaction.date.toString(),
            time = transaction.time.toString(),
            note = transaction.note,
            asset = transaction.asset,
            to_asset = transaction.toAsset,
            fixed_expense_id = transaction.fixedExpenseId,
            id = transaction.id
        )
        val remoteId = queries.selectTransactionRemoteId(transaction.id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            // 이미 서버에 있는 경우: UPDATE
            runCatching {
                supabase.postgrest.from("transactions").update({
                    set("title", transaction.title)
                    set("amount", transaction.amount)
                    set("type", transaction.type.name)
                    set("category", transaction.category)
                    set("date", transaction.date.toString())
                    set("tx_time", transaction.time.toString())
                    set("note", transaction.note)
                    set("asset", transaction.asset)
                    set("to_asset", transaction.toAsset)
                }) { filter { eq("id", remoteId) } }
            }
        } else {
            // remoteId 없음 = 오프라인 중 생성된 거래 → 서버에 새로 INSERT
            val bookId = sessionManager.activeBookId ?: return
            val createdBy = sessionManager.currentUser.value?.id ?: return
            runCatching {
                val dto = supabase.postgrest.from("transactions").insert(
                    TransactionRemoteDto(
                        bookId = bookId, title = transaction.title,
                        amount = transaction.amount, type = transaction.type.name,
                        category = transaction.category, date = transaction.date.toString(),
                        time = transaction.time.toString(), note = transaction.note,
                        asset = transaction.asset, toAsset = transaction.toAsset,
                        createdBy = createdBy, categoryEmoji = transaction.categoryEmoji,
                    )
                ) { select() }.decodeSingle<TransactionRemoteDto>()
                queries.updateTransactionRemoteId(dto.id, transaction.id)
            }
        }
    }

    override suspend fun delete(id: Long) {
        val remoteId = queries.selectTransactionRemoteId(id).executeAsOneOrNull()
        queries.deleteById(id)
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("transactions").delete { filter { eq("id", remoteId) } }
            }
        }
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
        fixedExpenseId = fixed_expense_id,
        createdBy = created_by,
        categoryEmoji = category_emoji,
    )
}
