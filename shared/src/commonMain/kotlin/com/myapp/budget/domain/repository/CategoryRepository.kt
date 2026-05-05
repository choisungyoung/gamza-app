package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getByParent(parentId: Long): Flow<List<UserCategory>>
    fun getByType(type: TransactionType): Flow<List<UserCategory>>
    fun getParentsByType(type: TransactionType): Flow<List<ParentCategory>>
    suspend fun insert(category: UserCategory, bookId: String? = null)
    suspend fun insertParent(name: String, emoji: String, type: TransactionType, bookId: String? = null)
    /** import 전용: Supabase 동기화 실패 시 예외를 throw해 row를 failed 처리 */
    suspend fun insertAndSync(category: UserCategory, bookId: String)
    suspend fun insertParentAndSync(name: String, emoji: String, type: TransactionType, bookId: String)
    suspend fun update(id: Long, name: String, emoji: String)
    suspend fun delete(id: Long)
    suspend fun updateParent(id: Long, name: String, emoji: String)
    suspend fun moveParentUp(id: Long, type: TransactionType)
    suspend fun moveParentDown(id: Long, type: TransactionType)
    suspend fun moveSubcategoryUp(id: Long, parentId: Long)
    suspend fun moveSubcategoryDown(id: Long, parentId: Long)
}
