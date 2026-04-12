package com.myapp.budget.data.repository

import com.myapp.budget.data.export.XlsxBuilder
import com.myapp.budget.data.export.XlsxParser
import com.myapp.budget.domain.model.FixedExpense
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.DataExportRepository
import com.myapp.budget.domain.repository.FixedExpenseRepository
import com.myapp.budget.domain.repository.ImportResult
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class DataExportRepositoryImpl(
    private val transactionRepo: TransactionRepository,
    private val fixedExpenseRepo: FixedExpenseRepository
) : DataExportRepository {

    // ── 내보내기 ──────────────────────────────────────────────────────────
    override suspend fun exportToExcel(): ByteArray {
        val transactions = transactionRepo.getAll().first()
        val fixedExpenses = fixedExpenseRepo.getAllIncludingInactive().first()

        val sheets = listOf(
            buildTransactionsSheet(transactions),
            buildFixedExpensesSheet(fixedExpenses)
        )
        return XlsxBuilder.build(sheets)
    }

    private fun buildTransactionsSheet(list: List<Transaction>): XlsxBuilder.SheetData {
        val header = listOf("ID", "제목", "금액", "유형", "카테고리", "날짜", "시간", "메모", "자산", "이체대상", "고정지출ID")
        val rows = mutableListOf(header)
        for (t in list) {
            rows.add(listOf(
                t.id.toString(), t.title, t.amount.toString(), t.type.name,
                t.category, t.date.toString(), t.time.toString(),
                t.note, t.asset, t.toAsset, t.fixedExpenseId?.toString() ?: ""
            ))
        }
        return XlsxBuilder.SheetData("거래내역", rows)
    }

    private fun buildFixedExpensesSheet(list: List<FixedExpense>): XlsxBuilder.SheetData {
        val header = listOf("ID", "제목", "금액", "카테고리", "자산", "일", "시작연도", "시작월", "메모")
        val rows = mutableListOf(header)
        for (f in list) {
            rows.add(listOf(
                f.id.toString(), f.title, f.amount.toString(), f.category,
                f.asset, f.dayOfMonth.toString(), f.startYear.toString(),
                f.startMonth.toString(), f.note
            ))
        }
        return XlsxBuilder.SheetData("고정지출", rows)
    }

    // ── 가져오기 ──────────────────────────────────────────────────────────
    override suspend fun importFromExcel(xlsxBytes: ByteArray): ImportResult {
        val sheets = XlsxParser.parse(xlsxBytes)
        var txCount = 0; var feCount = 0; var failed = 0

        for (sheet in sheets) {
            when (sheet.name) {
                "거래내역" -> {
                    val (ok, err) = importTransactions(sheet.rows)
                    txCount = ok; failed += err
                }
                "고정지출" -> {
                    val (ok, err) = importFixedExpenses(sheet.rows)
                    feCount = ok; failed += err
                }
            }
        }
        return ImportResult(txCount, feCount, failed)
    }

    private suspend fun importTransactions(rows: List<List<String>>): Pair<Int, Int> {
        if (rows.size < 2) return 0 to 0
        var ok = 0; var failed = 0
        for (row in rows.drop(1)) {
            try {
                val title = row.getOrElse(1) { "" }
                if (title.isBlank()) { failed++; continue }
                val amount = row.getOrElse(2) { "" }.toLongOrNull()
                if (amount == null) { failed++; continue }
                val typeStr = row.getOrElse(3) { "EXPENSE" }
                val type = runCatching { TransactionType.valueOf(typeStr) }.getOrDefault(TransactionType.EXPENSE)
                val dateStr = row.getOrElse(5) { "" }
                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                if (date == null) { failed++; continue }
                val timeStr = row.getOrElse(6) { "00:00:00" }
                val time = runCatching { LocalTime.parse(timeStr) }.getOrDefault(LocalTime(0, 0))

                transactionRepo.insert(
                    Transaction(
                        title = title,
                        amount = amount,
                        type = type,
                        category = row.getOrElse(4) { "" },
                        date = date,
                        time = time,
                        note = row.getOrElse(7) { "" },
                        asset = row.getOrElse(8) { "" },
                        toAsset = row.getOrElse(9) { "" }
                    )
                )
                ok++
            } catch (_: Exception) { failed++ }
        }
        return ok to failed
    }

    private suspend fun importFixedExpenses(rows: List<List<String>>): Pair<Int, Int> {
        if (rows.size < 2) return 0 to 0
        var ok = 0; var failed = 0
        for (row in rows.drop(1)) {
            try {
                val title = row.getOrElse(1) { "" }
                if (title.isBlank()) { failed++; continue }
                val amount = row.getOrElse(2) { "" }.toLongOrNull()
                if (amount == null) { failed++; continue }
                val day = row.getOrElse(5) { "1" }.toIntOrNull() ?: 1
                val startYear = row.getOrElse(6) { "2024" }.toIntOrNull()
                if (startYear == null) { failed++; continue }
                val startMonth = row.getOrElse(7) { "1" }.toIntOrNull()
                if (startMonth == null) { failed++; continue }

                fixedExpenseRepo.insert(
                    FixedExpense(
                        title = title,
                        amount = amount,
                        category = row.getOrElse(3) { "" },
                        asset = row.getOrElse(4) { "" },
                        dayOfMonth = day.coerceIn(1, 31),
                        startYear = startYear,
                        startMonth = startMonth.coerceIn(1, 12),
                        note = row.getOrElse(8) { "" }
                    )
                )
                ok++
            } catch (_: Exception) { failed++ }
        }
        return ok to failed
    }
}
