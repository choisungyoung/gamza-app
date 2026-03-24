package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getByParent(parent: String): Flow<List<UserCategory>>
    fun getByType(type: TransactionType): Flow<List<UserCategory>>
    fun getParentsByType(type: TransactionType): Flow<List<ParentCategory>>
    suspend fun insert(category: UserCategory)
    suspend fun insertParent(name: String, emoji: String, type: TransactionType)
    suspend fun update(id: Long, name: String, emoji: String)
    suspend fun delete(id: Long)
    suspend fun updateParent(id: Long, name: String, emoji: String)
    suspend fun moveParentUp(id: Long, type: TransactionType)
    suspend fun moveParentDown(id: Long, type: TransactionType)
    suspend fun moveSubcategoryUp(id: Long, parentKey: String)
    suspend fun moveSubcategoryDown(id: Long, parentKey: String)
    suspend fun ensureDefaults()
}
