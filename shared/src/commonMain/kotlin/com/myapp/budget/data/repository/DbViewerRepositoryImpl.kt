package com.myapp.budget.data.repository

import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.model.DbTableData
import com.myapp.budget.domain.repository.DbViewerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DbViewerRepositoryImpl(
    private val database: BudgetDatabase
) : DbViewerRepository {

    override suspend fun deleteTable(tableName: String) = withContext(Dispatchers.Default) {
        val q = database.budgetQueries
        when (tableName) {
            "TransactionEntity" -> q.deleteAllTransactions()
            "FixedExpenseEntity" -> q.deleteAllFixedExpenses()
            "UserCategoryEntity" -> q.deleteAllUserCategories()
            "ParentCategoryEntity" -> q.deleteAllParentCategories()
            "AssetGroupEntity" -> q.deleteAllAssetGroups()
            "AssetEntity" -> q.deleteAllAssets()
        }
    }

    override suspend fun getAllTableData(): List<DbTableData> = withContext(Dispatchers.Default) {
        val q = database.budgetQueries
        listOf(
            DbTableData(
                tableName = "TransactionEntity",
                columns = listOf("id", "title", "amount", "type", "category", "date", "time", "note", "asset", "to_asset", "fixed_expense_id"),
                rows = q.selectAll().executeAsList().map { e ->
                    listOf(
                        e.id.toString(), e.title, e.amount.toString(), e.type,
                        e.category, e.date, e.time, e.note, e.asset, e.to_asset,
                        e.fixed_expense_id?.toString() ?: ""
                    )
                }
            ),
            DbTableData(
                tableName = "FixedExpenseEntity",
                columns = listOf("id", "title", "amount", "category", "asset", "day", "s_year", "s_month", "note", "active"),
                rows = q.selectAllFixedExpensesIncludingInactive().executeAsList().map { e ->
                    listOf(
                        e.id.toString(), e.title, e.amount.toString(), e.category,
                        e.asset, e.day_of_month.toString(), e.start_year.toString(),
                        e.start_month.toString(), e.note, e.is_active.toString()
                    )
                }
            ),
            DbTableData(
                tableName = "UserCategoryEntity",
                columns = listOf("id", "name", "emoji", "parent_id", "type", "sort_order"),
                rows = (q.selectUserCategoriesByType("EXPENSE").executeAsList() +
                        q.selectUserCategoriesByType("INCOME").executeAsList()).map { e ->
                    listOf(e.id.toString(), e.name, e.emoji, e.parent_id.toString(), e.type, e.sort_order.toString())
                }
            ),
            DbTableData(
                tableName = "ParentCategoryEntity",
                columns = listOf("id", "name", "emoji", "type", "sort_order"),
                rows = (q.selectParentsByType("EXPENSE").executeAsList() +
                        q.selectParentsByType("INCOME").executeAsList()).map { e ->
                    listOf(e.id.toString(), e.name, e.emoji, e.type, e.sort_order.toString())
                }
            ),
            DbTableData(
                tableName = "AssetGroupEntity",
                columns = listOf("id", "name", "emoji", "key", "sort_order", "is_liability"),
                rows = q.selectAllAssetGroups().executeAsList().map { e ->
                    listOf(e.id.toString(), e.name, e.emoji, e.key, e.sort_order.toString(), e.is_liability.toString())
                }
            ),
            DbTableData(
                tableName = "AssetEntity",
                columns = listOf("id", "name", "emoji", "owner", "init_balance", "group_key", "sort_order"),
                rows = q.selectAllAssets().executeAsList().map { e ->
                    listOf(
                        e.id.toString(), e.name, e.emoji, e.owner,
                        e.initial_balance.toString(), e.group_key, e.sort_order.toString()
                    )
                }
            )
        )
    }
}
