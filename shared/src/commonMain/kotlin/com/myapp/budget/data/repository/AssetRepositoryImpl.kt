package com.myapp.budget.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.myapp.budget.db.AssetEntity
import com.myapp.budget.db.AssetGroupEntity
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.model.Asset
import com.myapp.budget.domain.model.AssetGroup
import com.myapp.budget.domain.repository.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AssetRepositoryImpl(private val db: BudgetDatabase) : AssetRepository {

    private val q = db.budgetQueries

    override fun getAllGroups(): Flow<List<AssetGroup>> =
        q.selectAllAssetGroups()
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { e -> e.toModel() } }

    override fun getAllAssets(): Flow<List<Asset>> =
        q.selectAllAssets()
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { e -> e.toModel() } }

    override fun getAssetsByGroup(groupKey: String): Flow<List<Asset>> =
        q.selectAssetsByGroup(groupKey)
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { e -> e.toModel() } }

    override suspend fun insertAsset(asset: Asset) = withContext(Dispatchers.Default) {
        val maxOrder = q.maxAssetSortOrder(asset.groupKey).executeAsOne()
        q.insertAsset(asset.name, asset.emoji, asset.owner, asset.initialBalance,
            asset.groupKey, maxOrder + 1)
    }

    override suspend fun updateAsset(id: Long, name: String, emoji: String,
                                     owner: String, initialBalance: Long) =
        withContext(Dispatchers.Default) {
            q.updateAsset(name, emoji, owner, initialBalance, id)
        }

    override suspend fun deleteAsset(id: Long) = withContext(Dispatchers.Default) {
        q.deleteAsset(id)
    }

    override suspend fun updateGroup(id: Long, name: String, emoji: String) =
        withContext(Dispatchers.Default) {
            q.updateAssetGroup(name, emoji, id)
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

    override suspend fun ensureDefaults() = withContext(Dispatchers.Default) {
        if (q.countAssetGroups().executeAsOne() != 0L) return@withContext

        defaultGroups.forEachIndexed { i, g ->
            q.insertAssetGroup(g.name, g.emoji, g.key, i.toLong(), if (g.isLiability) 1L else 0L)
        }

        if (q.countAssets().executeAsOne() != 0L) return@withContext

        defaultAssets.forEach { a ->
            val maxOrder = q.maxAssetSortOrder(a.groupKey).executeAsOne()
            q.insertAsset(a.name, a.emoji, "", 0L, a.groupKey, maxOrder + 1)
        }
    }

    private data class GroupDef(val key: String, val name: String,
                                val emoji: String, val isLiability: Boolean = false)
    private data class AssetDef(val groupKey: String, val name: String, val emoji: String)

    private val defaultGroups = listOf(
        GroupDef("ACCOUNT",    "계좌/현금", "🏦"),
        GroupDef("PAY_MONEY",  "페이머니",  "📱"),
        GroupDef("CARD",       "카드",      "💳"),
        GroupDef("LOAN",       "대출",      "📊", isLiability = true),
        GroupDef("INVESTMENT", "투자",      "📈")
    )

    private val defaultAssets = listOf(
        AssetDef("ACCOUNT",    "우리은행 월급 계좌", "🏦"),
        AssetDef("PAY_MONEY",  "네이버 페이",        "📱"),
        AssetDef("PAY_MONEY",  "토스 포인트",        "💸"),
        AssetDef("CARD",       "우리카드",           "💳"),
        AssetDef("CARD",       "롯데카드",           "🟡"),
        AssetDef("INVESTMENT", "키움증권 계좌",      "📈")
    )
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
