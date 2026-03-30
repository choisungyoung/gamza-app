package com.myapp.budget.domain.model

data class DbTableData(
    val tableName: String,
    val columns: List<String>,
    val rows: List<List<String>>
)
