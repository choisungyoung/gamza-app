package com.myapp.budget.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TransactionRemoteDto(
    val id: String = "",
    @SerialName("book_id") val bookId: String,
    val title: String,
    val amount: Long,
    val type: String,
    val category: String,
    val date: String,
    @SerialName("tx_time") val time: String = "00:00:00",
    val note: String = "",
    val asset: String = "",
    @SerialName("to_asset") val toAsset: String = "",
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("category_emoji") val categoryEmoji: String = "",
    // null일 때 JSON 직렬화에서 제외 → Supabase 컬럼 미존재 시 에러 방지
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("fixed_expense_id") val fixedExpenseId: String? = null,
)

@Serializable
data class FixedExpenseRemoteDto(
    val id: String = "",
    @SerialName("book_id") val bookId: String,
    val title: String,
    val amount: Long,
    val category: String,
    val asset: String = "",
    @SerialName("day_of_month") val dayOfMonth: Int,
    @SerialName("start_year") val startYear: Int,
    @SerialName("start_month") val startMonth: Int,
    val note: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class ParentCategoryRemoteDto(
    val id: String = "",
    @SerialName("book_id") val bookId: String,
    val name: String,
    val emoji: String = "",
    val type: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class UserCategoryRemoteDto(
    val id: String = "",
    @SerialName("book_id") val bookId: String,
    val name: String,
    val emoji: String = "",
    @SerialName("parent_remote_id") val parentRemoteId: String?,
    val type: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class AssetGroupRemoteDto(
    val id: String = "",
    @SerialName("book_id") val bookId: String,
    val name: String,
    val emoji: String = "",
    @SerialName("grp_key") val key: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_liability") val isLiability: Boolean = false,
)

@Serializable
data class AssetRemoteDto(
    val id: String = "",
    @SerialName("book_id") val bookId: String,
    val name: String,
    val emoji: String = "",
    val owner: String = "",
    @SerialName("initial_balance") val initialBalance: Long = 0,
    @SerialName("group_key") val groupKey: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)
