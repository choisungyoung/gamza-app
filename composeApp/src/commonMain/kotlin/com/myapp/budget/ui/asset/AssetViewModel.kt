package com.myapp.budget.ui.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.budget.domain.model.Asset
import com.myapp.budget.domain.model.AssetGroup
import com.myapp.budget.domain.model.TransactionType
import com.myapp.budget.domain.repository.AssetRepository
import com.myapp.budget.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetViewModel(
    private val repo: AssetRepository,
    private val txRepo: TransactionRepository
) : ViewModel() {

    val groups: StateFlow<List<AssetGroup>> =
        repo.getAllGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assetsMap: StateFlow<Map<String, List<Asset>>> =
        repo.getAllAssets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            .let { flow ->
                combine(flow, groups) { assets, _ ->
                    assets.groupBy { it.groupKey }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
            }

    /** asset name → 실제 현재 잔액 = initialBalance + 수입 - 지출 - 이체출금 + 이체입금 */
    val assetBalances: StateFlow<Map<String, Long>> =
        combine(assetsMap, txRepo.getAll()) { aMap, txList ->
            aMap.values.flatten().associate { asset ->
                val assetTxs = txList.filter { it.asset == asset.name }
                val toAssetTxs = txList.filter { it.toAsset == asset.name && it.type == TransactionType.TRANSFER }
                val income = assetTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val expense = assetTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val transferOut = assetTxs.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
                val transferIn = toAssetTxs.sumOf { it.amount }
                asset.name to (asset.initialBalance + income - expense - transferOut + transferIn)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 총 자산 = 일반 그룹 실제잔액 합계 - 부채(대출) 그룹 실제잔액 합계 */
    val totalAssets: StateFlow<Long> =
        combine(groups, assetsMap, assetBalances) { grpList, aMap, balances ->
            grpList.sumOf { group ->
                val subtotal = aMap[group.key]?.sumOf { asset ->
                    balances[asset.name] ?: asset.initialBalance
                } ?: 0L
                if (group.isLiability) -subtotal else subtotal
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        viewModelScope.launch { repo.ensureDefaults() }
    }

    fun updateGroup(id: Long, name: String, emoji: String) {
        viewModelScope.launch { repo.updateGroup(id, name, emoji) }
    }

    fun reorderGroups(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val list = groups.value
            if (fromIndex == toIndex) return@launch
            val id = list.getOrNull(fromIndex)?.id ?: return@launch
            val steps = toIndex - fromIndex
            if (steps > 0) repeat(steps) { repo.moveGroupDown(id) }
            else repeat(-steps) { repo.moveGroupUp(id) }
        }
    }

    fun addAsset(name: String, emoji: String, owner: String,
                 initialBalance: Long, groupKey: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.insertAsset(
                Asset(name = name.trim(), emoji = emoji.ifBlank { "💰" },
                    owner = owner.trim(), initialBalance = initialBalance, groupKey = groupKey)
            )
        }
    }

    fun updateAsset(id: Long, name: String, emoji: String,
                    owner: String, initialBalance: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.updateAsset(id, name.trim(), emoji.ifBlank { "💰" }, owner.trim(), initialBalance)
        }
    }

    fun deleteAsset(id: Long) {
        viewModelScope.launch { repo.deleteAsset(id) }
    }

    fun reorderAssets(fromIndex: Int, toIndex: Int, groupKey: String) {
        viewModelScope.launch {
            val assets = assetsMap.value[groupKey] ?: return@launch
            if (fromIndex == toIndex) return@launch
            val id = assets.getOrNull(fromIndex)?.id ?: return@launch
            val steps = toIndex - fromIndex
            if (steps > 0) repeat(steps) { repo.moveAssetDown(id, groupKey) }
            else repeat(-steps) { repo.moveAssetUp(id, groupKey) }
        }
    }
}
