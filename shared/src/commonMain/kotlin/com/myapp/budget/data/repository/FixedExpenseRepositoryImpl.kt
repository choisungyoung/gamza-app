package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.repository.FixedExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class FixedExpenseRepositoryImpl(
    private val database: BudgetDatabase
) : FixedExpenseRepository {

    private val queries = database.budgetQueries

    override fun getAll(): Flow<List<FixedExpense>> =
        queries.selectAllFixedExpenses()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toModel() } }

    override fun getAllIncludingInactive(): Flow<List<FixedExpense>> =
        queries.selectAllFixedExpensesIncludingInactive()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toModel() } }

    override suspend fun insert(fixedExpense: FixedExpense): Long {
        queries.insertFixedExpense(
            title = fixedExpense.title,
            amount = fixedExpense.amount,
            category = fixedExpense.category,
            asset = fixedExpense.asset,
            day_of_month = fixedExpense.dayOfMonth.toLong(),
            start_year = fixedExpense.startYear.toLong(),
            start_month = fixedExpense.startMonth.toLong(),
            note = fixedExpense.note
        )
        return queries.lastFixedExpenseRowId().executeAsOne()
    }

    override suspend fun update(id: Long, title: String, amount: Long, dayOfMonth: Int, note: String) {
        queries.updateFixedExpense(
            title = title,
            amount = amount,
            day_of_month = dayOfMonth.toLong(),
            note = note,
            id = id
        )
    }

    override suspend fun delete(id: Long) {
        queries.deleteFixedExpense(id)
    }

    override suspend fun countLinkedTransactions(id: Long): Long =
        queries.countByFixedExpenseId(id).executeAsOne()

    override suspend fun deactivate(id: Long) {
        queries.deactivateFixedExpense(id)
    }

    override suspend fun detachFromDate(id: Long, fromDateStr: String) {
        queries.detachFixedExpenseFromDate(id, fromDateStr)
    }

    override suspend fun autoRegisterPending(today: LocalDate) {
        // is_active = 1 인 고정지출만 처리 (해제된 항목 제외)
        val allFixed = queries.selectAllFixedExpenses().executeAsList()
        for (fe in allFixed) {
            // 이미 등록된 거래의 "YYYY-MM" 목록 수집
            val existingMonthKeys = queries.selectByFixedExpenseId(fe.id)
                .executeAsList()
                .map { it.date.substring(0, 7) }

            // 마지막 등록된 월을 시작점으로 사용
            // 등록된 거래가 없으면 start_year/start_month 부터 시작
            val (startYear, startMonth) = if (existingMonthKeys.isEmpty()) {
                fe.start_year.toInt() to fe.start_month.toInt()
            } else {
                // "YYYY-MM" 은 사전순 비교로 최신값을 구할 수 있음
                val lastKey = existingMonthKeys.max()
                val lYear = lastKey.substring(0, 4).toInt()
                val lMonth = lastKey.substring(5, 7).toInt()
                // 마지막 등록 월의 다음 달부터 등록
                if (lMonth == 12) lYear + 1 to 1 else lYear to lMonth + 1
            }

            var year = startYear
            var month = startMonth

            while (year < today.year || (year == today.year && month <= today.monthNumber)) {
                val monthKey = "$year-${month.toString().padStart(2, '0')}"
                val day = clampDay(year, month, fe.day_of_month.toInt())
                val dateStr = "$monthKey-${day.toString().padStart(2, '0')}"
                queries.insert(
                    title = fe.title,
                    amount = fe.amount,
                    type = "EXPENSE",
                    category = fe.category,
                    date = dateStr,
                    time = "00:00:00",
                    note = fe.note,
                    asset = fe.asset,
                    to_asset = "",
                    fixed_expense_id = fe.id
                )
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
