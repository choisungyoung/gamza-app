package com.myapp.budget.db

/**
 * DB 최초 생성 시 기본 카테고리·자산 그룹·자산을 삽입합니다.
 * 데이터가 이미 존재하면 아무것도 하지 않습니다.
 */
object BudgetDatabaseSeeder {

    fun seedIfNeeded(db: BudgetDatabase) {
        val q = db.budgetQueries
        seedCategories(q)
    }

    fun seedAssetsForBook(db: BudgetDatabase, bookId: String) {
        val q = db.budgetQueries
        if (q.countAssetGroupsByBookId(bookId).executeAsOne() > 0L) return

        assetGroupData.forEachIndexed { i, g ->
            q.insertAssetGroupWithBook(g.name, g.emoji, g.key, i.toLong(), if (g.isLiability) 1L else 0L, bookId)
        }

        // 기본 계좌: 우리은행
        q.insertAssetWithBook("우리은행 월급 계좌", "🏦", "", 0L, "ACCOUNT", 0L, bookId)
    }

    // ── 카테고리 ────────────────────────────────────────────────────────────

    private data class ParentDef(
        val name: String,
        val emoji: String,
        val type: String,
        val subs: List<Pair<String, String>>,
    )

    private val categoryData = listOf(
        // 지출
        ParentDef("식비", "🍚", "EXPENSE", listOf(
            "아침식사" to "🍳", "점심식사" to "🍱", "저녁식사" to "🍽️",
            "간식" to "🍪", "카페" to "☕", "배달음식" to "🛵",
        )),
        ParentDef("교통", "🚌", "EXPENSE", listOf(
            "대중교통" to "🚇", "택시" to "🚕", "주유" to "⛽", "주차" to "🅿️",
        )),
        ParentDef("생활", "🏠", "EXPENSE", listOf(
            "월세" to "🏠", "관리비" to "🔧", "인터넷" to "📶", "공과금" to "💡", "통신비" to "📞",
        )),
        ParentDef("쇼핑", "🛍️", "EXPENSE", listOf(
            "의류" to "👕", "생활용품" to "🛒", "전자기기" to "💻", "온라인쇼핑" to "📦",
        )),
        ParentDef("의료", "💊", "EXPENSE", listOf(
            "병원" to "🏥", "약국" to "💊", "운동" to "🏃",
        )),
        ParentDef("문화", "🎬", "EXPENSE", listOf(
            "영화" to "🎬", "OTT" to "🎥", "게임" to "🎮", "여행" to "✈️", "독서" to "📖",
        )),
        ParentDef("교육", "📚", "EXPENSE", listOf(
            "책" to "📚", "강의" to "🖥️", "학원" to "🏫",
        )),
        ParentDef("기타 지출", "💸", "EXPENSE", listOf(
            "기타지출" to "💸",
        )),
        // 수입
        ParentDef("급여", "💼", "INCOME", listOf(
            "본급여" to "💰", "상여금" to "🎁",
        )),
        ParentDef("부업", "💡", "INCOME", listOf(
            "프리랜서" to "💼", "알바" to "👷",
        )),
        ParentDef("투자", "📈", "INCOME", listOf(
            "주식" to "📈", "이자" to "💹", "부동산" to "🏢",
        )),
        ParentDef("용돈", "🎁", "INCOME", listOf(
            "용돈" to "🎁",
        )),
        ParentDef("기타 수입", "💰", "INCOME", listOf(
            "기타수입" to "💰",
        )),
        // 이체 (서브카테고리 없음)
        ParentDef("내계좌이체", "🔄", "TRANSFER", emptyList()),
        ParentDef("저축",      "🏦", "TRANSFER", emptyList()),
        ParentDef("현금",      "💵", "TRANSFER", emptyList()),
        ParentDef("투자",      "📈", "TRANSFER", emptyList()),
        ParentDef("대출",      "💸", "TRANSFER", emptyList()),
    )

    private fun seedCategories(q: BudgetQueries) {
        if (q.countParents().executeAsOne() > 0L) return

        categoryData.forEachIndexed { i, parent ->
            q.insertParent(parent.name, parent.emoji, parent.type, i.toLong())
            val parentId = q.lastInsertRowId().executeAsOne()
            parent.subs.forEachIndexed { j, (name, emoji) ->
                q.insertUserCategory(name, emoji, parentId, parent.type, j.toLong())
            }
        }
    }

    // ── 자산 ────────────────────────────────────────────────────────────────

    private data class GroupDef(
        val key: String,
        val name: String,
        val emoji: String,
        val isLiability: Boolean = false,
    )

    private val assetGroupData = listOf(
        GroupDef("ACCOUNT",    "계좌/현금", "🏦"),
        GroupDef("PAY_MONEY",  "페이머니",  "📱"),
        GroupDef("CARD",       "카드",      "💳"),
        GroupDef("LOAN",       "대출",      "📊", isLiability = true),
        GroupDef("INVESTMENT", "투자",      "📈"),
    )

}
