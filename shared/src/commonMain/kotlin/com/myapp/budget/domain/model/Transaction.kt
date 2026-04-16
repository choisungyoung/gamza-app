package com.myapp.budget.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

@Serializable
data class Transaction(
    val id: Long = 0,
    val title: String,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val date: LocalDate,
    val time: LocalTime = LocalTime(0, 0),
    val note: String = "",
    val asset: String = "",    // 지출: 결제수단 / 수입: 입금계좌 / 이체: 출금계좌
    val toAsset: String = "",  // 이체: 입금계좌 (지출/수입은 빈 문자열)
    val isFixed: Boolean = false,
    val createdBy: String = "",
    val categoryEmoji: String = "",
)
