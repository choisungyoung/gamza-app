package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.data.remote.FixedExpenseRemoteDto
import com.myapp.budget.data.remote.TransactionRemoteDto
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.repository.FixedExpenseRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class FixedExpenseRepositoryImpl(
    private val database: BudgetDatabase,
    private val sessionManager: SessionManager,
    private val supabase: SupabaseClient,
) : FixedExpenseRepository {

    private val queries = database.budgetQueries

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAll(): Flow<List<FixedExpense>> =
        sessionManager.activeBook.flatMapLatest { book ->
            val bookId = book?.id ?: ""
            if (bookId.isNotBlank()) {
                queries.selectFixedExpensesByBookId(bookId).asFlow().mapToList(Dispatchers.Default)
            } else {
                flowOf(emptyList())
            }
        }.map { list -> list.map { it.toModel() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllIncludingInactive(): Flow<List<FixedExpense>> =
        sessionManager.activeBook.flatMapLatest { book ->
            val bookId = book?.id ?: ""
            if (bookId.isNotBlank()) {
                queries.selectAllFixedExpensesIncludingInactiveByBookId(bookId).asFlow().mapToList(Dispatchers.Default)
            } else {
                flowOf(emptyList())
            }
        }.map { list -> list.map { it.toModel() } }

    override suspend fun insert(fixedExpense: FixedExpense): Long {
        val bookId = sessionManager.activeBookId
            ?: error("활성화된 가계부가 없습니다. 로그인이 필요합니다.")

        // 서버에 먼저 저장
        val dto = supabase.postgrest.from("fixed_expenses").insert(
            FixedExpenseRemoteDto(
                bookId = bookId, title = fixedExpense.title,
                amount = fixedExpense.amount, category = fixedExpense.category,
                asset = fixedExpense.asset, dayOfMonth = fixedExpense.dayOfMonth,
                startYear = fixedExpense.startYear, startMonth = fixedExpense.startMonth,
                note = fixedExpense.note, isActive = true
            )
        ) { select() }.decodeSingle<FixedExpenseRemoteDto>()

        // 로컬 캐시 업데이트
        queries.insertFixedExpenseWithBook(
            title = fixedExpense.title, amount = fixedExpense.amount,
            category = fixedExpense.category, asset = fixedExpense.asset,
            day_of_month = fixedExpense.dayOfMonth.toLong(),
            start_year = fixedExpense.startYear.toLong(),
            start_month = fixedExpense.startMonth.toLong(),
            note = fixedExpense.note, book_id = bookId,
        )
        val localId = queries.lastFixedExpenseRowId().executeAsOne()
        queries.updateFixedExpenseRemoteId(dto.id, localId)
        return localId
    }

    override suspend fun update(id: Long, title: String, amount: Long, dayOfMonth: Int, note: String) {
        queries.updateFixedExpense(
            title = title, amount = amount,
            day_of_month = dayOfMonth.toLong(), note = note, id = id
        )
        val remoteId = queries.selectFixedExpenseRemoteId(id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("fixed_expenses").update({
                    set("title", title); set("amount", amount)
                    set("day_of_month", dayOfMonth); set("note", note)
                }) { filter { eq("id", remoteId) } }
            }
        }
    }

    override suspend fun delete(id: Long) {
        val remoteId = queries.selectFixedExpenseRemoteId(id).executeAsOneOrNull()
        queries.deleteFixedExpense(id)
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("fixed_expenses").delete { filter { eq("id", remoteId) } }
            }
        }
    }

    override suspend fun countLinkedTransactions(id: Long): Long =
        queries.countByFixedExpenseId(id).executeAsOne()

    override suspend fun deactivate(id: Long) {
        queries.deactivateFixedExpense(id)
        val remoteId = queries.selectFixedExpenseRemoteId(id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("fixed_expenses").update({ set("is_active", false) }) {
                    filter { eq("id", remoteId) }
                }
            }
        }
    }

    override suspend fun detachFromDate(id: Long, fromDateStr: String) {
        queries.detachFixedExpenseFromDate(id, fromDateStr)
    }

    override suspend fun autoRegisterPending(today: LocalDate) {
        val bookId = sessionManager.activeBookId ?: return
        val allFixed = queries.selectFixedExpensesByBookId(bookId).executeAsList()

        for (fe in allFixed) {
            val existingMonthKeys = queries.selectByFixedExpenseId(fe.id)
                .executeAsList()
                .map { it.date.substring(0, 7) }

            val (startYear, startMonth) = if (existingMonthKeys.isEmpty()) {
                fe.start_year.toInt() to fe.start_month.toInt()
            } else {
                val lastKey = existingMonthKeys.max()
                val lYear = lastKey.substring(0, 4).toInt()
                val lMonth = lastKey.substring(5, 7).toInt()
                if (lMonth == 12) lYear + 1 to 1 else lYear to lMonth + 1
            }

            var year = startYear
            var month = startMonth

            while (year < today.year || (year == today.year && month <= today.monthNumber)) {
                val monthKey = "$year-${month.toString().padStart(2, '0')}"
                val day = clampDay(year, month, fe.day_of_month.toInt())
                val dateStr = "$monthKey-${day.toString().padStart(2, '0')}"

                // 서버에 먼저 저장
                runCatching {
                    val dto = supabase.postgrest.from("transactions").insert(
                        TransactionRemoteDto(
                            bookId = bookId, title = fe.title, amount = fe.amount,
                            type = "EXPENSE", category = fe.category, date = dateStr,
                            time = "00:00:00", note = fe.note, asset = fe.asset, createdBy = ""
                        )
                    ) { select() }.decodeSingle<TransactionRemoteDto>()

                    // 로컬 캐시 업데이트
                    queries.insertWithBook(
                        title = fe.title, amount = fe.amount, type = "EXPENSE",
                        category = fe.category, category_emoji = "", date = dateStr,
                        time = "00:00:00", note = fe.note, asset = fe.asset, to_asset = "",
                        fixed_expense_id = fe.id, book_id = bookId,
                    )
                    val localId = queries.lastInsertRowId().executeAsOne()
                    queries.updateTransactionRemoteId(dto.id, localId)
                }

                if (month == 12) { year++; month = 1 } else month++
            }
        }
    }

    private fun clampDay(year: Int, month: Int, day: Int): Int {
        val firstOfNext = if (month == 12) LocalDate(year + 1, 1, 1)
                          else LocalDate(year, month + 1, 1)
        val maxDay = firstOfNext.minus(DatePeriod(days = 1)).dayOfMonth
        return minOf(day, maxDay)
    }

    private fun com.myapp.budget.db.FixedExpenseEntity.toModel() = FixedExpense(
        id = id,
        title = title,
        amount = amount,
        category = category,
        asset = asset,
        dayOfMonth = day_of_month.toInt(),
        startYear = start_year.toInt(),
        startMonth = start_month.toInt(),
        note = note,
        isActive = is_active != 0L
    )
}
