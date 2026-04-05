package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.data.remote.ParentCategoryRemoteDto
import com.myapp.budget.data.remote.UserCategoryRemoteDto
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.db.ParentCategoryEntity
import com.myapp.budget.db.UserCategoryEntity
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.ParentCategory
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.model.UserCategory
import com.myapp.budget.domain.repository.CategoryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CategoryRepositoryImpl(
    private val db: BudgetDatabase,
    private val sessionManager: SessionManager,
    private val supabase: SupabaseClient,
) : CategoryRepository {

    private val q = db.budgetQueries

    override fun getByParent(parentId: Long): Flow<List<UserCategory>> =
        q.selectUserCategoriesByParentId(parentId)
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { e -> e.toModel() } }

    override fun getByType(type: TransactionType): Flow<List<UserCategory>> =
        q.selectUserCategoriesByType(type.name)
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { e -> e.toModel() } }

    override fun getParentsByType(type: TransactionType): Flow<List<ParentCategory>> =
        q.selectParentsByType(type.name)
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { e -> e.toParentModel() } }

    override suspend fun insert(category: UserCategory) = withContext(Dispatchers.Default) {
        val bookId = sessionManager.activeBookId
            ?: error("활성화된 가계부가 없습니다. 로그인이 필요합니다.")
        val maxOrder = q.maxUserCategorySortOrder(category.parentId).executeAsOne()
        q.insertUserCategoryWithBook(category.name, category.emoji, category.parentId, category.type.name, maxOrder + 1, bookId)
        val localId = q.lastInsertRowId().executeAsOne()
        val parentRemoteId = q.selectParentCategoryRemoteId(category.parentId).executeAsOneOrNull()
        if (!parentRemoteId.isNullOrBlank()) {
            runCatching {
                val dto = supabase.postgrest.from("user_categories").insert(
                    UserCategoryRemoteDto(bookId = bookId, name = category.name, emoji = category.emoji,
                        parentRemoteId = parentRemoteId, type = category.type.name, sortOrder = (maxOrder + 1).toInt())
                ) { select() }.decodeSingle<UserCategoryRemoteDto>()
                q.updateUserCategoryRemoteId(dto.id, localId)
            }
        }
        Unit
    }

    override suspend fun insertParent(name: String, emoji: String, type: TransactionType) = withContext(Dispatchers.Default) {
        val bookId = sessionManager.activeBookId
            ?: error("활성화된 가계부가 없습니다. 로그인이 필요합니다.")
        val maxOrder = q.maxParentSortOrderByType(type.name).executeAsOne()
        q.insertParentWithBook(name.trim(), emoji.ifBlank { "📌" }, type.name, maxOrder + 1, bookId)
        val localId = q.lastInsertRowId().executeAsOne()
        runCatching {
            val dto = supabase.postgrest.from("parent_categories").insert(
                ParentCategoryRemoteDto(bookId = bookId, name = name.trim(),
                    emoji = emoji.ifBlank { "📌" }, type = type.name, sortOrder = (maxOrder + 1).toInt())
            ) { select() }.decodeSingle<ParentCategoryRemoteDto>()
            q.updateParentCategoryRemoteId(dto.id, localId)
        }
        Unit
    }

    override suspend fun update(id: Long, name: String, emoji: String) = withContext(Dispatchers.Default) {
        q.updateUserCategory(name, emoji, id)
        val remoteId = q.selectUserCategoryRemoteId(id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("user_categories").update({
                    set("name", name); set("emoji", emoji)
                }) { filter { eq("id", remoteId) } }
            }
        }
        Unit
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.Default) {
        val remoteId = q.selectUserCategoryRemoteId(id).executeAsOneOrNull()
        q.deleteUserCategory(id)
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("user_categories").delete { filter { eq("id", remoteId) } }
            }
        }
        Unit
    }

    override suspend fun updateParent(id: Long, name: String, emoji: String) = withContext(Dispatchers.Default) {
        q.updateParent(name, emoji, id)
        val remoteId = q.selectParentCategoryRemoteId(id).executeAsOneOrNull()
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("parent_categories").update({
                    set("name", name); set("emoji", emoji)
                }) { filter { eq("id", remoteId) } }
            }
        }
        Unit
    }

    override suspend fun moveParentUp(id: Long, type: TransactionType) = withContext(Dispatchers.Default) {
        val parents = getParentsByType(type).first()
        val index = parents.indexOfFirst { it.id == id }
        if (index <= 0) return@withContext
        val current = parents[index]
        val above = parents[index - 1]
        q.updateParentOrder(above.sortOrder.toLong(), current.id)
        q.updateParentOrder(current.sortOrder.toLong(), above.id)
    }

    override suspend fun moveParentDown(id: Long, type: TransactionType) = withContext(Dispatchers.Default) {
        val parents = getParentsByType(type).first()
        val index = parents.indexOfFirst { it.id == id }
        if (index < 0 || index >= parents.size - 1) return@withContext
        val current = parents[index]
        val below = parents[index + 1]
        q.updateParentOrder(below.sortOrder.toLong(), current.id)
        q.updateParentOrder(current.sortOrder.toLong(), below.id)
    }

    override suspend fun moveSubcategoryUp(id: Long, parentId: Long) = withContext(Dispatchers.Default) {
        val subs = getByParent(parentId).first()
        val index = subs.indexOfFirst { it.id == id }
        if (index <= 0) return@withContext
        val current = subs[index]
        val above = subs[index - 1]
        q.updateUserCategoryOrder(above.sortOrder.toLong(), current.id)
        q.updateUserCategoryOrder(current.sortOrder.toLong(), above.id)
    }

    override suspend fun moveSubcategoryDown(id: Long, parentId: Long) = withContext(Dispatchers.Default) {
        val subs = getByParent(parentId).first()
        val index = subs.indexOfFirst { it.id == id }
        if (index < 0 || index >= subs.size - 1) return@withContext
        val current = subs[index]
        val below = subs[index + 1]
        q.updateUserCategoryOrder(below.sortOrder.toLong(), current.id)
        q.updateUserCategoryOrder(current.sortOrder.toLong(), below.id)
    }

}

private fun UserCategoryEntity.toModel() = UserCategory(
    id = id,
    name = name,
    emoji = emoji,
    parentId = parent_id,
    type = TransactionType.valueOf(type),
    sortOrder = sort_order.toInt()
)

private fun ParentCategoryEntity.toParentModel() = ParentCategory(
    id = id,
    name = name,
    emoji = emoji,
    type = TransactionType.valueOf(type),
    sortOrder = sort_order.toInt()
)
