package com.myapp.budget.domain.repository

data class ImportResult(
    val transactions: Int,
    val failed: Int = 0
)

interface DataExportRepository {
    suspend fun exportToExcel(): ByteArray
    suspend fun importFromExcel(xlsxBytes: ByteArray, bookId: String? = null): ImportResult
}
