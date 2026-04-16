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
import kotlinx.coroutines.flow.flowOf
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
                flowOf(emptyList())
            }
        }.map { list -> list.map { it.toModel() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllFixed(): Flow<List<Transaction>> =
        sessionManager.activeBook.flatMapLatest { book ->
            val bookId = book?.id ?: ""
            if (bookId.isNotBlank()) {
                queries.selectFixedTransactionsByBookId(bookId).asFlow().mapToList(Dispatchers.Default)
            } else {
                flowOf(emptyList())
            }
        }.map { list -> list.map { it.toModel() } }

    override suspend fun insert(transaction: Transaction) {
        val bookId = sessionManager.activeBookId
            ?: error("활성화된 가계부가 없습니다. 로그인이 필요합니다.")
        val createdBy = sessionManager.currentUser.value?.id ?: ""

        // 서버에 먼저 저장
        val dto = supabase.postgrest.from("transactions").insert(
            TransactionRemoteDto(
                bookId = bookId, title = transaction.title,
                amount = transaction.amount, type = transaction.type.name,
                category = transaction.category, date = transaction.date.toString(),
                time = transaction.time.toString(), note = transaction.note,
                asset = transaction.asset, toAsset = transaction.toAsset,
                createdBy = createdBy, categoryEmoji = transaction.categoryEmoji,
                isFixed = transaction.isFixed
            )
        ) { select() }.decodeSingle<TransactionRemoteDto>()

        // pullBookData가 이미 실행됐을 수 있으므로 remote_id로 중복 체크
        val existingLocalId = queries.selectTransactionIdByRemoteId(dto.id).executeAsOneOrNull()
        if (existingLocalId != null) return

        // 로컬 캐시 업데이트
        queries.insertWithBookAndCreator(
            title = transaction.title, amount = transaction.amount,
            type = transaction.type.name, category = transaction.category,
            category_emoji = transaction.categoryEmoji, date = transaction.date.toString(),
            time = transaction.time.toString(), note = transaction.note,
            asset = transaction.asset, to_asset = transaction.toAsset,
            is_fixed = if (transaction.isFixed) 1L else 0L,
            book_id = bookId, created_by = createdBy,
        )
        val localId = queries.lastInsertRowId().executeAsOne()
        queries.updateTransactionRemoteId(dto.id, localId)
    }

    override suspend fun update(transaction: Transaction) {
        val bookId = sessionManager.activeBookId ?: return
        val createdBy = sessionManager.currentUser.value?.id ?: ""

        queries.update(
            title = transaction.title, amount = transaction.amount,
            type = transaction.type.name, category = transaction.category,
            category_emoji = transaction.categoryEmoji, date = transaction.date.toString(),
            time = transaction.time.toString(), note = transaction.note,
            asset = transaction.asset, to_asset = transaction.toAsset,
            is_fixed = if (transaction.isFixed) 1L else 0L, id = transaction.id
        )

        val remoteId = queries.selectTransactionRemoteId(transaction.id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            supabase.postgrest.from("transactions").update({
                set("title", transaction.title); set("amount", transaction.amount)
                set("type", transaction.type.name); set("category", transaction.category)
                set("date", transaction.date.toString()); set("tx_time", transaction.time.toString())
                set("note", transaction.note); set("asset", transaction.asset)
                set("to_asset", transaction.toAsset); set("is_fixed", transaction.isFixed)
            }) { filter { eq("id", remoteId) } }
        } else {
            // remote_id 없는 레거시 레코드 → 서버에 새로 INSERT
            val dto = supabase.postgrest.from("transactions").insert(
                TransactionRemoteDto(
                    bookId = bookId, title = transaction.title,
                    amount = transaction.amount, type = transaction.type.name,
                    category = transaction.category, date = transaction.date.toString(),
                    time = transaction.time.toString(), note = transaction.note,
                    asset = transaction.asset, toAsset = transaction.toAsset,
                    createdBy = createdBy, categoryEmoji = transaction.categoryEmoji,
                    isFixed = transaction.isFixed,
                )
            ) { select() }.decodeSingle<TransactionRemoteDto>()
            queries.updateTransactionRemoteId(dto.id, transaction.id)
        }
    }

    override suspend fun delete(id: Long) {
        val remoteId = queries.selectTransactionRemoteId(id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            supabase.postgrest.from("transactions").delete { filter { eq("id", remoteId) } }
        }
        queries.deleteById(id)
    }

    override suspend fun getById(id: Long): Transaction? =
        queries.selectById(id).executeAsOneOrNull()?.toModel()

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
        isFixed = is_fixed != 0L,
        createdBy = created_by,
        categoryEmoji = category_emoji,
    )
}
