package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.DbTableData

interface DbViewerRepository {
    suspend fun getAllTableData(): List<DbTableData>
}
