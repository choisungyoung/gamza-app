package com.myapp.budget.data.repository

import com.myapp.budget.data.export.XlsxBuilder
import com.myapp.budget.data.export.XlsxParser
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.Transaction
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import com.myapp.budget.domain.repository.CategoryRepository
import com.myapp.budget.domain.repository.DataExportRepository
import com.myapp.budget.domain.repository.ImportResult
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

private const val DEFAULT_EMOJI = "💸"

class DataExportRepositoryImpl(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
) : DataExportRepository {

    // ── 내보내기 ──────────────────────────────────────────────────────────
    override suspend fun exportToExcel(): ByteArray {
        val transactions = transactionRepo.getAll().first()
        return XlsxBuilder.build(listOf(buildTransactionsSheet(transactions)))
    }

    private fun buildTransactionsSheet(list: List<Transaction>): XlsxBuilder.SheetData {
        val header = listOf("제목", "금액", "유형", "카테고리", "날짜", "시간", "메모", "자산", "이체대상", "고정여부")
        val rows = mutableListOf(header)
        for (t in list) {
            rows.add(listOf(
                t.title, t.amount.toString(), t.type.name,
                t.category, t.date.toString(), t.time.toString(),
                t.note, t.asset, t.toAsset, if (t.isFixed) "Y" else "N"
            ))
        }
        return XlsxBuilder.SheetData("거래내역", rows)
    }

    // ── 가져오기 ──────────────────────────────────────────────────────────
    override suspend fun importFromExcel(xlsxBytes: ByteArray, bookId: String?): ImportResult {
        val sheets = XlsxParser.parse(xlsxBytes)
        var txCount = 0; var failed = 0

        for (sheet in sheets) {
            if (sheet.name == "거래내역") {
                val (ok, err) = importTransactions(sheet.rows, bookId)
                txCount = ok; failed += err
            }
        }
        return ImportResult(txCount, failed)
    }

    /**
     * 카테고리 문자열("부모/세부" 또는 "부모")에 대해 DB에 없으면 자동 생성하고
     * 해당 부모 카테고리의 emoji를 반환한다.
     */
    private suspend fun ensureCategory(categoryStr: String, type: TransactionType, bookId: String? = null): String {
        if (categoryStr.isBlank()) return DEFAULT_EMOJI

        val parts = categoryStr.split("/", limit = 2)
        val parentName = parts[0].trim()
        val subName = if (parts.size > 1) parts[1].trim() else null

        // 부모 카테고리 조회 또는 생성
        val parents = categoryRepo.getParentsByType(type).first()
        val parent: ParentCategory = parents.firstOrNull { it.name == parentName }
            ?: run {
                categoryRepo.insertParent(parentName, DEFAULT_EMOJI, type, bookId)
                categoryRepo.getParentsByType(type).first().firstOrNull { it.name == parentName }
                    ?: error("부모 카테고리 생성 후 조회 실패: $parentName")
            }

        // 세부 카테고리 조회 또는 생성 (이체 타입은 1-depth이므로 생략)
        // syncBookData 전에 로컬 생성 → syncBookData 내 pushBookData가 Supabase에 올려줌
        if (subName != null && type != TransactionType.TRANSFER) {
            val subs = categoryRepo.getByParent(parent.id).first()
            if (subs.none { it.name == subName }) {
                categoryRepo.insert(
                    UserCategory(name = subName, emoji = DEFAULT_EMOJI, parentId = parent.id, type = type),
                    bookId
                )
            }
        }

        return parent.emoji
    }

    private suspend fun importTransactions(rows: List<List<String>>, bookId: String?): Pair<Int, Int> {
        if (rows.size < 2) return 0 to 0

        // 기존 거래내역을 (제목, 금액, 날짜, 시간) 키로 인덱싱
        // bookId가 있으면 sessionManager 의존 없이 직접 조회 (파일 피커 후 세션 일시 clear 대비)
        val existing = if (bookId != null) transactionRepo.getAllByBookId(bookId)
                       else transactionRepo.getAll().first()
        val existingMap = existing.associateBy { Triple(it.title, it.amount, it.date.toString() + "T" + it.time.toString()) }

        var ok = 0; var failed = 0
        for (row in rows.drop(1)) {
            try {
                val title = row.getOrElse(0) { "" }
                if (title.isBlank()) { failed++; continue }
                val amount = row.getOrElse(1) { "" }.toLongOrNull()
                if (amount == null) { failed++; continue }
                val typeStr = row.getOrElse(2) { "EXPENSE" }
                val type = runCatching { TransactionType.valueOf(typeStr) }.getOrDefault(TransactionType.EXPENSE)
                val dateStr = row.getOrElse(4) { "" }
                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                    ?: dateStr.toIntOrNull()?.let { excelSerialToLocalDate(it) }
                if (date == null) { failed++; continue }
                val timeStr = row.getOrElse(5) { "00:00:00" }
                val time = runCatching { LocalTime.parse(timeStr) }.getOrDefault(LocalTime(0, 0))
                val categoryStr = row.getOrElse(3) { "" }
                val isFixed = row.getOrElse(9) { "N" }.uppercase() == "Y"

                val categoryEmoji = ensureCategory(categoryStr, type, bookId)

                val key = Triple(title, amount, date.toString() + "T" + time.toString())
                val matched = existingMap[key]

                if (matched != null) {
                    transactionRepo.update(
                        matched.copy(
                            type = type,
                            category = categoryStr.ifBlank { matched.category },
                            categoryEmoji = categoryEmoji,
                            note = row.getOrElse(6) { matched.note },
                            asset = row.getOrElse(7) { matched.asset },
                            toAsset = row.getOrElse(8) { matched.toAsset },
                            isFixed = isFixed
                        )
                    )
                } else {
                    transactionRepo.insert(
                        Transaction(
                            title = title,
                            amount = amount,
                            type = type,
                            category = categoryStr,
                            categoryEmoji = categoryEmoji,
                            date = date,
                            time = time,
                            note = row.getOrElse(6) { "" },
                            asset = row.getOrElse(7) { "" },
                            toAsset = row.getOrElse(8) { "" },
                            isFixed = isFixed
                        ),
                        bookId
                    )
                }
                ok++
            } catch (_: Exception) { failed++ }
        }
        return ok to failed
    }
}

/** Excel 날짜 시리얼 숫자 → LocalDate 변환. 기준일: 1899-12-30 (Excel 1900년 윤년 버그 보정) */
private fun excelSerialToLocalDate(serial: Int): LocalDate? = runCatching {
    LocalDate(1899, 12, 30).plus(serial, DateTimeUnit.DAY)
}.getOrNull()
