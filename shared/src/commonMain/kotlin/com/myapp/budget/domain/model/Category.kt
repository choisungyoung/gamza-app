package com.myapp.budget.domain.model

enum class Category(
    val displayName: String,
    val emoji: String,
    val type: TransactionType
) {
    // 지출
    FOOD("식비", "🍚", TransactionType.EXPENSE),
    TRANSPORT("교통", "🚌", TransactionType.EXPENSE),
    HOUSING("생활", "🏠", TransactionType.EXPENSE),
    SHOPPING("쇼핑", "🛍️", TransactionType.EXPENSE),
    HEALTH("의료", "💊", TransactionType.EXPENSE),
    CULTURE("문화", "🎬", TransactionType.EXPENSE),
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
        /** "식비/아침식사" 또는 "식비" 형식에서 부모 Category를 반환 */
        fun fromCategoryStr(str: String): Category {
            val parentStr = str.substringBefore("/")
            return entries.firstOrNull { it.displayName == parentStr } ?: EXPENSE_OTHER
        }
        /** "식비/아침식사" → "아침식사", "식비" → null */
        fun subcategoryOf(str: String): String? = str.substringAfter("/", "").takeIf { it.isNotEmpty() }
    }
}
