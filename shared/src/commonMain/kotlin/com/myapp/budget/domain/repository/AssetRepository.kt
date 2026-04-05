package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.Asset
import com.myapp.budget.domain.model.AssetGroup
import kotlinx.coroutines.flow.Flow

interface AssetRepository {
    fun getAllGroups(): Flow<List<AssetGroup>>
    fun getAllAssets(): Flow<List<Asset>>
    fun getAssetsByGroup(groupKey: String): Flow<List<Asset>>
    suspend fun insertAsset(asset: Asset)
    suspend fun updateAsset(id: Long, name: String, emoji: String, owner: String, initialBalance: Long)
    suspend fun deleteAsset(id: Long)
    suspend fun updateGroup(id: Long, name: String, emoji: String)
    suspend fun moveGroupUp(id: Long)
    suspend fun moveGroupDown(id: Long)
    suspend fun moveAssetUp(id: Long, groupKey: String)
    suspend fun moveAssetDown(id: Long, groupKey: String)
}
