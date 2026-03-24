package com.myapp.budget.domain.model

enum class Category(
    val displayName: String,
    val emoji: String,
    val type: TransactionType
) {
    // 지출
    FOOD("식비", "🍚", TransactionType.EXPENSE),
    TRANSPORT("교통/차량", "🚌", TransactionType.EXPENSE),
    HOUSING("주거/통신", "🏠", TransactionType.EXPENSE),
    SHOPPING("쇼핑", "🛍️", TransactionType.EXPENSE),
    HEALTH("의료/건강", "💊", TransactionType.EXPENSE),
    CULTURE("문화/여가", "🎬", TransactionType.EXPENSE),
    EDUCATION("교육", "📚", TransactionType.EXPENSE),
    EXPENSE_OTHER("기타 지출", "💸", TransactionType.EXPENSE),

    // 수입
    SALARY("급여", "💼", TransactionType.INCOME),
    SIDE_JOB("부업", "💡", TransactionType.INCOME),
    INVESTMENT("투자", "📈", TransactionType.INCOME),
    ALLOWANCE("용돈", "🎁", TransactionType.INCOME),
    INCOME_OTHER("기타 수입", "💰", TransactionType.INCOME),

    // 이체 (1-depth only)
    TRANSFER_INTERNAL("내계좌이체", "🔄", TransactionType.TRANSFER),
    TRANSFER_SAVING("저축", "🏦", TransactionType.TRANSFER),
    TRANSFER_CASH("현금", "💵", TransactionType.TRANSFER),
    TRANSFER_INVESTMENT("투자", "📈", TransactionType.TRANSFER),
    TRANSFER_LOAN("대출", "💸", TransactionType.TRANSFER);

    companion object {
        fun forType(type: TransactionType): List<Category> = entries.filter { it.type == type }
        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: EXPENSE_OTHER
        /** "FOOD/아침식사" 또는 "FOOD" 형식에서 부모 Category를 반환 */
        fun fromCategoryStr(str: String): Category = fromName(str.substringBefore("/"))
        /** "FOOD/아침식사" → "아침식사", "FOOD" → null */
        fun subcategoryOf(str: String): String? = str.substringAfter("/", "").takeIf { it.isNotEmpty() }
    }
}
