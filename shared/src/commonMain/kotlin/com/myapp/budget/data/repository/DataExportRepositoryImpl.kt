package com.myapp.budget.data.repository

import com.myapp.budget.data.export.XlsxBuilder
import com.myapp.budget.data.export.XlsxParser
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.DataExportRepository
import com.myapp.budget.domain.repository.ImportResult
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class DataExportRepositoryImpl(
    private val transactionRepo: TransactionRepository,
) : DataExportRepository {

    // ── 내보내기 ──────────────────────────────────────────────────────────
    override suspend fun exportToExcel(): ByteArray {
        val transactions = transactionRepo.getAll().first()
        val fixedTransactions = transactionRepo.getAllFixed().first()

        val sheets = listOf(
            buildTransactionsSheet(transactions),
            buildFixedTransactionsSheet(fixedTransactions)
        )
        return XlsxBuilder.build(sheets)
    }

    private fun buildTransactionsSheet(list: List<Transaction>): XlsxBuilder.SheetData {
        val header = listOf("ID", "제목", "금액", "유형", "카테고리", "날짜", "시간", "메모", "자산", "이체대상", "고정여부")
        val rows = mutableListOf(header)
        for (t in list) {
            rows.add(listOf(
                t.id.toString(), t.title, t.amount.toString(), t.type.name,
                t.category, t.date.toString(), t.time.toString(),
                t.note, t.asset, t.toAsset, if (t.isFixed) "Y" else "N"
            ))
        }
        return XlsxBuilder.SheetData("거래내역", rows)
    }

    private fun buildFixedTransactionsSheet(list: List<Transaction>): XlsxBuilder.SheetData {
        val header = listOf("ID", "제목", "금액", "카테고리", "자산", "날짜", "메모")
        val rows = mutableListOf(header)
        for (t in list) {
            rows.add(listOf(
                t.id.toString(), t.title, t.amount.toString(),
                t.category, t.asset, t.date.toString(), t.note
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
                    // 고정지출 시트는 참조용으로만 유지, 가져오기 미지원
                    feCount = 0
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
                val isFixed = row.getOrElse(10) { "N" }.uppercase() == "Y"

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
                        toAsset = row.getOrElse(9) { "" },
                        isFixed = isFixed
                    )
                )
                ok++
            } catch (_: Exception) { failed++ }
        }
        return ok to failed
    }
}
