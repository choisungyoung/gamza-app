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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

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
                note = fixedExpense.note
            )
        ) { select() }.decodeSingle<FixedExpenseRemoteDto>()

        // pullBookData가 이미 실행됐을 수 있으므로 remote_id로 중복 체크
        val existingLocalId = queries.selectFixedExpenseIdByRemoteId(dto.id).executeAsOneOrNull()
        if (existingLocalId != null) return existingLocalId

        // 로컬 캐시 업데이트: insert + lastRowId + updateRemoteId를 원자적 트랜잭션으로 묶음
        // (autoRegisterPending 등 병렬 코루틴이 lastRowId를 오염시키는 race condition 방지)
        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                // 트랜잭션 진입 후 한 번 더 중복 체크
                val doubleCheck = queries.selectFixedExpenseIdByRemoteId(dto.id).executeAsOneOrNull()
                if (doubleCheck != null) return@transactionWithResult doubleCheck

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
                localId
            }
        }
    }

    override suspend fun update(id: Long, title: String, amount: Long, dayOfMonth: Int, note: String) {
        // Supabase 먼저 → 성공 후 로컬 반영 (순서 역전 시 pull에서 덮어씌워짐)
        val remoteId = queries.selectFixedExpenseRemoteId(id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            supabase.postgrest.from("fixed_expenses").update({
                set("title", title); set("amount", amount)
                set("day_of_month", dayOfMonth); set("note", note)
            }) { filter { eq("id", remoteId) } }
        }
        queries.updateFixedExpense(
            title = title, amount = amount,
            day_of_month = dayOfMonth.toLong(), note = note, id = id
        )
    }

    override suspend fun delete(id: Long, remoteId: String) {
        val bookId = sessionManager.activeBookId ?: error("활성화된 가계부가 없습니다.")

        val fe = queries.selectFixedExpenseById(id).executeAsOneOrNull()

        // 로컬 DB의 remote_id를 우선 사용 (파라미터가 stale하거나 blank일 수 있음)
        val effectiveRemoteId = fe?.remote_id?.takeIf { it.isNotBlank() }
            ?: remoteId.takeIf { it.isNotBlank() }

        // FK 제약 해소: transactions.fixed_expense_id가 이 고정지출을 참조하면
        // 먼저 Supabase 거래를 삭제해야 fixed_expense DELETE가 통과됨
        if (effectiveRemoteId != null) {
            supabase.postgrest.from("transactions").delete {
                filter { eq("fixed_expense_id", effectiveRemoteId) }
            }
        }

        if (fe != null) {
            // 비즈니스 키로 Supabase 고정지출 삭제 (remote_id에 의존하지 않음)
            supabase.postgrest.from("fixed_expenses").delete {
                filter {
                    eq("book_id", bookId)
                    eq("title", fe.title)
                    eq("amount", fe.amount)
                    eq("day_of_month", fe.day_of_month)
                    eq("start_year", fe.start_year)
                    eq("start_month", fe.start_month)
                }
            }
            // PostgREST는 RLS가 막아도 HTTP 200을 반환 → SELECT로 실제 삭제 여부 확인
            val stillExists = supabase.postgrest.from("fixed_expenses")
                .select {
                    filter {
                        eq("book_id", bookId)
                        eq("title", fe.title)
                        eq("amount", fe.amount)
                        eq("day_of_month", fe.day_of_month)
                        eq("start_year", fe.start_year)
                        eq("start_month", fe.start_month)
                    }
                }
                .decodeList<FixedExpenseRemoteDto>()
                .isNotEmpty()
            if (stillExists) error("고정지출을 삭제할 수 없습니다. 권한을 확인하거나 다시 시도해주세요.")
        } else if (effectiveRemoteId != null) {
            // 로컬에 없는 엣지 케이스: remote_id로 폴백
            supabase.postgrest.from("fixed_expenses").delete {
                filter { eq("id", effectiveRemoteId) }
            }
        }

        queries.deleteFixedExpense(id)
    }

    override suspend fun countLinkedTransactions(id: Long): Long =
        queries.countByFixedExpenseId(id).executeAsOne()

    override suspend fun getRemoteId(localId: Long): String =
        queries.selectFixedExpenseRemoteId(localId).executeAsOneOrNull() ?: ""

    override suspend fun detachFromDate(id: Long, fromDateStr: String) {
        queries.detachFixedExpenseFromDate(id, fromDateStr)
    }

    override suspend fun autoRegisterPending(today: LocalDate) {
        val bookId = sessionManager.activeBookId ?: return
        val userId = sessionManager.currentUser.value?.id ?: return

        // VIEWER는 자동 등록 스킵 (쓰기 권한 없음)
        val memberRole = queries.selectMemberRoleByBookAndUser(bookId, userId)
            .executeAsOneOrNull()
            ?.let { runCatching { com.myapp.budget.domain.model.MemberRole.valueOf(it) }.getOrNull() }
        // BookMemberEntity에 없으면 가계부 소유자 → 쓰기 허용
        val isOwner = memberRole == null
        if (!isOwner && memberRole?.canWrite() != true) return

        val allFixed = queries.selectFixedExpensesByBookId(bookId).executeAsList()

        for (fe in allFixed) {
            // 이미 등록된 월 키 Set으로 수집 (중간 공백 포함 전체 체크)
            val existingMonthKeys = queries.selectByFixedExpenseId(fe.id)
                .executeAsList()
                .mapTo(mutableSetOf()) { it.date.substring(0, 7) }

            var year = fe.start_year.toInt()
            var month = fe.start_month.toInt()

            while (year < today.year || (year == today.year && month <= today.monthNumber)) {
                val monthKey = "$year-${month.toString().padStart(2, '0')}"

                if (monthKey !in existingMonthKeys) {
                    val day = clampDay(year, month, fe.day_of_month.toInt())
                    val dateStr = "$monthKey-${day.toString().padStart(2, '0')}"
                    val feRemoteId = fe.remote_id.takeIf { it.isNotBlank() }

                    // Supabase에 먼저 저장 (unique 제약으로 중복 방지)
                    runCatching {
                        val dto = supabase.postgrest.from("transactions").insert(
                            TransactionRemoteDto(
                                bookId = bookId, title = fe.title, amount = fe.amount,
                                type = "EXPENSE", category = fe.category, date = dateStr,
                                time = "00:00:00", note = fe.note, asset = fe.asset,
                                createdBy = userId, fixedExpenseId = feRemoteId
                            )
                        ) { select() }.decodeSingle<TransactionRemoteDto>()

                        // 로컬 캐시 업데이트
                        queries.insertWithBookAndCreator(
                            title = fe.title, amount = fe.amount, type = "EXPENSE",
                            category = fe.category, category_emoji = "", date = dateStr,
                            time = "00:00:00", note = fe.note, asset = fe.asset, to_asset = "",
                            fixed_expense_id = fe.id, book_id = bookId, created_by = userId,
                        )
                        val localId = queries.lastInsertRowId().executeAsOne()
                        queries.updateTransactionRemoteId(dto.id, localId)
                    }
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
        remoteId = remote_id
    )
}
