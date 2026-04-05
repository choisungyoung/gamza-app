package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.data.remote.AssetGroupRemoteDto
import com.myapp.budget.data.remote.AssetRemoteDto
import com.myapp.budget.db.AssetEntity
import com.myapp.budget.db.AssetGroupEntity
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.SessionManager
import com.myapp.budget.domain.model.Asset
import com.myapp.budget.domain.model.AssetGroup
import com.myapp.budget.domain.repository.AssetRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AssetRepositoryImpl(
    private val db: BudgetDatabase,
    private val sessionManager: SessionManager,
    private val supabase: SupabaseClient,
) : AssetRepository {

    private val q = db.budgetQueries

    override fun getAllGroups(): Flow<List<AssetGroup>> =
        sessionManager.activeBook.flatMapLatest { book ->
            val bookId = book?.id
            if (!bookId.isNullOrBlank()) {
                q.selectAssetGroupsByBookIdOrdered(bookId)
                    .asFlow().mapToList(Dispatchers.Default)
                    .map { it.map { e -> e.toModel() } }
            } else {
                flowOf(emptyList())
            }
        }

    override fun getAllAssets(): Flow<List<Asset>> =
        sessionManager.activeBook.flatMapLatest { book ->
            val bookId = book?.id
            if (!bookId.isNullOrBlank()) {
                q.selectAssetsByBookIdOrdered(bookId)
                    .asFlow().mapToList(Dispatchers.Default)
                    .map { it.map { e -> e.toModel() } }
            } else {
                flowOf(emptyList())
            }
        }

    override fun getAssetsByGroup(groupKey: String): Flow<List<Asset>> {
        val bookId = sessionManager.activeBookId
        return if (!bookId.isNullOrBlank()) {
            q.selectAssetsByGroupAndBook(groupKey, bookId)
                .asFlow().mapToList(Dispatchers.Default)
                .map { it.map { e -> e.toModel() } }
        } else {
            q.selectAssetsByGroup(groupKey)
                .asFlow().mapToList(Dispatchers.Default)
                .map { it.map { e -> e.toModel() } }
        }
    }

    override suspend fun insertAsset(asset: Asset) = withContext(Dispatchers.Default) {
        val bookId = sessionManager.activeBookId ?: ""
        val maxOrder = if (bookId.isNotBlank()) {
            q.maxAssetSortOrderByBook(asset.groupKey, bookId).executeAsOne()
        } else {
            q.maxAssetSortOrder(asset.groupKey).executeAsOne()
        }
        if (bookId.isNotBlank()) {
            q.insertAssetWithBook(asset.name, asset.emoji, asset.owner, asset.initialBalance,
                asset.groupKey, maxOrder + 1, bookId)
            val localId = q.lastInsertRowId().executeAsOne()
            runCatching {
                val dto = supabase.postgrest.from("assets").insert(
                    AssetRemoteDto(bookId = bookId, name = asset.name, emoji = asset.emoji,
                        owner = asset.owner, initialBalance = asset.initialBalance,
                        groupKey = asset.groupKey, sortOrder = (maxOrder + 1).toInt())
                ) { select() }.decodeSingle<AssetRemoteDto>()
                q.updateAssetRemoteId(dto.id, localId)
            }
        } else {
            q.insertAsset(asset.name, asset.emoji, asset.owner, asset.initialBalance,
                asset.groupKey, maxOrder + 1)
        }
        Unit
    }

    override suspend fun updateAsset(id: Long, name: String, emoji: String,
                                     owner: String, initialBalance: Long) =
        withContext(Dispatchers.Default) {
            q.updateAsset(name, emoji, owner, initialBalance, id)
            val remoteId = q.selectAssetRemoteId(id).executeAsOneOrNull()
            if (!remoteId.isNullOrBlank()) {
                runCatching {
                    supabase.postgrest.from("assets").update({
                        set("name", name); set("emoji", emoji)
                        set("owner", owner); set("initial_balance", initialBalance)
                    }) { filter { eq("id", remoteId) } }
                }
            }
            Unit
        }

    override suspend fun deleteAsset(id: Long) = withContext(Dispatchers.Default) {
        val remoteId = q.selectAssetRemoteId(id).executeAsOneOrNull()
        q.deleteAsset(id)
        if (!remoteId.isNullOrBlank()) {
            runCatching {
                supabase.postgrest.from("assets").delete { filter { eq("id", remoteId) } }
            }
        }
        Unit
    }

    override suspend fun updateGroup(id: Long, name: String, emoji: String) =
        withContext(Dispatchers.Default) {
            q.updateAssetGroup(name, emoji, id)
            val remoteId = q.selectAssetGroupRemoteId(id).executeAsOneOrNull()
            if (!remoteId.isNullOrBlank()) {
                runCatching {
                    supabase.postgrest.from("asset_groups").update({
                        set("name", name); set("emoji", emoji)
                    }) { filter { eq("id", remoteId) } }
                }
            }
            Unit
        }

    override suspend fun moveGroupUp(id: Long) = withContext(Dispatchers.Default) {
        val groups = getAllGroups().first()
        val index = groups.indexOfFirst { it.id == id }
        if (index <= 0) return@withContext
        val current = groups[index]; val above = groups[index - 1]
        q.updateAssetGroupOrder(above.sortOrder.toLong(), current.id)
        q.updateAssetGroupOrder(current.sortOrder.toLong(), above.id)
    }

    override suspend fun moveGroupDown(id: Long) = withContext(Dispatchers.Default) {
        val groups = getAllGroups().first()
        val index = groups.indexOfFirst { it.id == id }
        if (index < 0 || index >= groups.size - 1) return@withContext
        val current = groups[index]; val below = groups[index + 1]
        q.updateAssetGroupOrder(below.sortOrder.toLong(), current.id)
        q.updateAssetGroupOrder(current.sortOrder.toLong(), below.id)
    }

    override suspend fun moveAssetUp(id: Long, groupKey: String) =
        withContext(Dispatchers.Default) {
            val assets = getAssetsByGroup(groupKey).first()
            val index = assets.indexOfFirst { it.id == id }
            if (index <= 0) return@withContext
            val current = assets[index]; val above = assets[index - 1]
            q.updateAssetOrder(above.sortOrder.toLong(), current.id)
            q.updateAssetOrder(current.sortOrder.toLong(), above.id)
        }

    override suspend fun moveAssetDown(id: Long, groupKey: String) =
        withContext(Dispatchers.Default) {
            val assets = getAssetsByGroup(groupKey).first()
            val index = assets.indexOfFirst { it.id == id }
            if (index < 0 || index >= assets.size - 1) return@withContext
            val current = assets[index]; val below = assets[index + 1]
            q.updateAssetOrder(below.sortOrder.toLong(), current.id)
            q.updateAssetOrder(current.sortOrder.toLong(), below.id)
        }

}

private fun AssetGroupEntity.toModel() = AssetGroup(
    id = id, name = name, emoji = emoji, key = key,
    sortOrder = sort_order.toInt(), isLiability = is_liability != 0L
)

private fun AssetEntity.toModel() = Asset(
    id = id, name = name, emoji = emoji, owner = owner,
    initialBalance = initial_balance, groupKey = group_key,
    sortOrder = sort_order.toInt()
)
