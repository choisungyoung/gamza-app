package com.myapp.budget.domain.repository

data class ImportResult(
    val transactions: Int,
    val fixedExpenses: Int,
    val failed: Int = 0
)

interface DataExportRepository {
    suspend fun exportToExcel(): ByteArray
    suspend fun importFromExcel(xlsxBytes: ByteArray): ImportResult
}
