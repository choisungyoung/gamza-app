package com.myapp.budget.data.repository

import com.myapp.budget.data.remote.AssetGroupRemoteDto
import com.myapp.budget.data.remote.AssetRemoteDto
import com.myapp.budget.data.remote.FixedExpenseRemoteDto
import com.myapp.budget.data.remote.ParentCategoryRemoteDto
import com.myapp.budget.data.remote.SupabaseClientProvider
import com.myapp.budget.data.remote.TransactionRemoteDto
import com.myapp.budget.data.remote.UserCategoryRemoteDto
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.model.Book
import com.myapp.budget.domain.model.BookMember
import com.myapp.budget.domain.model.MemberRole
import com.myapp.budget.domain.repository.BookRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers

class BookRepositoryImpl(
    private val database: BudgetDatabase,
) : BookRepository {

    private val supabase get() = SupabaseClientProvider.client
    private val queries get() = database.budgetQueries

    // ── DTOs (Supabase 직렬화용) ────────────────────────────────────────────

    @Serializable
    private data class BookDto(
        val id: String = "",
        val name: String,
        @SerialName("color_hex") val colorHex: String = "#A0522D",
        @SerialName("icon_emoji") val iconEmoji: String = "📒",
        @SerialName("owner_id") val ownerId: String,
    )

    @Serializable
    private data class BookMemberDto(
        val id: String = "",
        @SerialName("book_id") val bookId: String,
        @SerialName("user_id") val userId: String,
        val role: String = "VIEWER",
        @SerialName("joined_at") val joinedAt: String = "",
    )

    @Serializable
    private data class InviteCodeDto(
        val code: String,
        @SerialName("book_id") val bookId: String,
        @SerialName("created_by") val createdBy: String,
    )

    @Serializable
    private data class MemberWithNameDto(
        val id: String = "",
        @SerialName("book_id") val bookId: String,
        @SerialName("user_id") val userId: String,
        val role: String = "VIEWER",
        @SerialName("joined_at") val joinedAt: String = "",
        @SerialName("display_name") val displayName: String = "",
    )

    @Serializable
    private data class OwnerNameDto(
        @SerialName("book_id") val bookId: String,
        @SerialName("owner_display_name") val ownerDisplayName: String = "",
    )

    // ── Flow queries (SQLite 캐시 기반) ─────────────────────────────────────

    override fun getAllBooks(): Flow<List<Book>> =
        queries.selectAllBooks().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toBook() }
        }

    override fun getSelectedBook(): Flow<Book?> =
        queries.selectSelectedBook().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toBook() }

    // ── CRUD ────────────────────────────────────────────────────────────────

    override suspend fun createBook(name: String, colorHex: String, iconEmoji: String): Book {
        // security definer 함수 호출 (RLS 우회, 함수 내부에서 auth.uid() 검증)
        val created = supabase.postgrest.rpc(
            "create_book",
            buildJsonObject {
                put("p_name", name)
                put("p_color_hex", colorHex)
                put("p_icon_emoji", iconEmoji)
            }
        ).decodeAs<BookDto>()

        val now = Clock.System.now().toString()
        val isFirst = queries.countBooks().executeAsOne() == 0L
        queries.upsertBook(
            id = created.id,
            name = created.name,
            color_hex = created.colorHex,
            icon_emoji = created.iconEmoji,
            owner_id = created.ownerId,
            is_selected = if (isFirst) 1L else 0L,
            synced_at = now,
        )
        return Book(
            id = created.id,
            name = created.name,
            colorHex = created.colorHex,
            iconEmoji = created.iconEmoji,
            ownerId = created.ownerId,
            isSelected = isFirst,
        )
    }

    override suspend fun selectBook(bookId: String) {
        queries.setSelectedBook(bookId)
    }

    override suspend fun updateBook(bookId: String, name: String, colorHex: String, iconEmoji: String) {
        supabase.postgrest.from("books").update({
            set("name", name)
            set("color_hex", colorHex)
            set("icon_emoji", iconEmoji)
        }) {
            filter { eq("id", bookId) }
        }
        val existing = queries.selectAllBooks().executeAsList().firstOrNull { it.id == bookId }
            ?: return
        queries.upsertBook(
            id = bookId, name = name, color_hex = colorHex, icon_emoji = iconEmoji,
            owner_id = existing.owner_id, is_selected = existing.is_selected,
            synced_at = Clock.System.now().toString(),
        )
    }

    override suspend fun deleteBook(bookId: String) {
        // 로컬 연관 데이터 먼저 삭제
        queries.deleteTransactionsByBookId(bookId)
        queries.deleteFixedExpensesByBookId(bookId)
        queries.deleteUserCategoriesByBookId(bookId)
        queries.deleteParentCategoriesByBookId(bookId)
        queries.deleteAssetsByBookId(bookId)
        queries.deleteAssetGroupsByBookId(bookId)
        queries.deleteBookMembers(bookId)
        queries.deleteBook(bookId)
        // Supabase: books ON DELETE CASCADE로 연관 테이블 자동 삭제
        supabase.postgrest.from("books").delete { filter { eq("id", bookId) } }
    }

    // ── 초대 코드 ───────────────────────────────────────────────────────────

    override suspend fun generateInviteCode(bookId: String): String {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        // 초대 코드 생성 전, 가계부 데이터를 Supabase에 업로드
        pushBookData(bookId)
        val code = generateRandomCode()
        supabase.postgrest.from("invite_codes").insert(
            InviteCodeDto(code = code, bookId = bookId, createdBy = userId)
        )
        return code
    }

    override suspend fun joinByInviteCode(code: String): Book {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")

        // 코드 조회 및 유효성 확인
        val invite = supabase.postgrest.from("invite_codes")
            .select { filter { eq("code", code) } }
            .decodeSingle<InviteCodeDto>()

        // book_members에 추가
        supabase.postgrest.from("book_members").insert(
            BookMemberDto(bookId = invite.bookId, userId = userId, role = "EDITOR")
        )

        // 코드 사용됨 표시
        supabase.postgrest.from("invite_codes").update({ set("used", true) }) {
            filter { eq("code", code) }
        }

        // 가계부 정보 가져와서 로컬 캐시
        val bookDto = supabase.postgrest.from("books")
            .select { filter { eq("id", invite.bookId) } }
            .decodeSingle<BookDto>()

        val isFirst = queries.countBooks().executeAsOne() == 0L
        queries.upsertBook(
            id = bookDto.id, name = bookDto.name, color_hex = bookDto.colorHex,
            icon_emoji = bookDto.iconEmoji, owner_id = bookDto.ownerId,
            is_selected = if (isFirst) 1L else 0L,
            synced_at = Clock.System.now().toString(),
        )
        // 참여한 가계부의 데이터를 로컬로 다운로드
        pullBookData(bookDto.id)
        return Book(
            id = bookDto.id, name = bookDto.name, colorHex = bookDto.colorHex,
            iconEmoji = bookDto.iconEmoji, ownerId = bookDto.ownerId, isSelected = isFirst,
        )
    }

    // ── 멤버 관리 ───────────────────────────────────────────────────────────

    override suspend fun getMembers(bookId: String): List<BookMember> {
        val dtos = runCatching {
            supabase.postgrest.rpc(
                "get_members_with_names",
                buildJsonObject { put("p_book_id", bookId) }
            ).decodeList<MemberWithNameDto>()
        }.getOrElse {
            // RPC 실패 시 기본 조회로 폴백
            supabase.postgrest.from("book_members")
                .select { filter { eq("book_id", bookId) } }
                .decodeList<BookMemberDto>()
                .map { MemberWithNameDto(it.id, it.bookId, it.userId, it.role, it.joinedAt) }
        }

        queries.deleteBookMembers(bookId)
        dtos.forEach { dto ->
            queries.upsertBookMember(
                id = dto.id, book_id = dto.bookId, user_id = dto.userId,
                role = dto.role, joined_at = dto.joinedAt,
            )
        }
        // 현재 로그인된 사용자 정보를 닉네임 폴백으로 활용
        val localUser = queries.selectLocalUser().executeAsOneOrNull()
        return dtos.mapIndexed { index, dto ->
            val displayName = dto.displayName.ifBlank {
                // 현재 사용자의 경우 로컬 캐시의 닉네임 사용
                if (localUser != null && dto.userId == localUser.id && localUser.display_name.isNotBlank()) {
                    localUser.display_name
                } else {
                    "멤버 ${index + 1}"
                }
            }
            BookMember(
                id = dto.id, bookId = dto.bookId, userId = dto.userId,
                role = MemberRole.valueOf(dto.role), joinedAt = dto.joinedAt,
                displayName = displayName,
            )
        }
    }

    override suspend fun getOwnerDisplayNames(): Map<String, String> =
        runCatching {
            supabase.postgrest.rpc("get_my_books_with_owner_names")
                .decodeList<OwnerNameDto>()
                .associate { it.bookId to it.ownerDisplayName }
        }.getOrElse { emptyMap() }

    override suspend fun leaveBook(bookId: String): Book? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        supabase.postgrest.from("book_members").delete {
            filter {
                eq("book_id", bookId)
                eq("user_id", userId)
            }
        }
        // 로컬 연관 데이터 모두 삭제
        queries.deleteTransactionsByBookId(bookId)
        queries.deleteFixedExpensesByBookId(bookId)
        queries.deleteUserCategoriesByBookId(bookId)
        queries.deleteParentCategoriesByBookId(bookId)
        queries.deleteAssetsByBookId(bookId)
        queries.deleteAssetGroupsByBookId(bookId)
        queries.deleteBookMembers(bookId)
        queries.deleteBook(bookId)
        // 남은 가계부 중 첫 번째를 선택
        val nextBook = queries.selectAllBooks().executeAsList().firstOrNull()
        if (nextBook != null) queries.setSelectedBook(nextBook.id)
        return nextBook?.toBook()
    }

    override suspend fun removeMember(bookId: String, userId: String) {
        supabase.postgrest.from("book_members").delete {
            filter {
                eq("book_id", bookId)
                eq("user_id", userId)
            }
        }
        queries.deleteBookMember(bookId, userId)
    }

    override suspend fun updateMemberRole(bookId: String, userId: String, role: MemberRole) {
        supabase.postgrest.from("book_members").update({ set("role", role.name) }) {
            filter {
                eq("book_id", bookId)
                eq("user_id", userId)
            }
        }
    }

    // ── 동기화 ──────────────────────────────────────────────────────────────

    override suspend fun syncBookData(bookId: String) {
        // 공유 가계부 이름/색상 변경 등 메타데이터도 동기화
        runCatching {
            val bookDto = supabase.postgrest.from("books")
                .select { filter { eq("id", bookId) } }
                .decodeSingle<BookDto>()
            val existing = queries.selectAllBooks().executeAsList().firstOrNull { it.id == bookId }
            if (existing != null) {
                queries.upsertBook(
                    id = bookId, name = bookDto.name, color_hex = bookDto.colorHex,
                    icon_emoji = bookDto.iconEmoji, owner_id = bookDto.ownerId,
                    is_selected = existing.is_selected,
                    synced_at = Clock.System.now().toString(),
                )
            }
        }
        pullBookData(bookId)
    }

    override suspend fun syncBooks() {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val memberRows = supabase.postgrest.from("book_members")
            .select { filter { eq("user_id", userId) } }
            .decodeList<BookMemberDto>()

        val bookIds = memberRows.map { it.bookId }
        if (bookIds.isEmpty()) return

        val books = supabase.postgrest.from("books")
            .select()
            .decodeList<BookDto>()
            .filter { it.id in bookIds }

        val currentSelected = queries.selectSelectedBook().executeAsOneOrNull()
        books.forEach { dto ->
            val isSelected = currentSelected?.id == dto.id
            queries.upsertBook(
                id = dto.id, name = dto.name, color_hex = dto.colorHex,
                icon_emoji = dto.iconEmoji, owner_id = dto.ownerId,
                is_selected = if (isSelected) 1L else 0L,
                synced_at = Clock.System.now().toString(),
            )
        }

        // 선택된 책이 없으면 첫 번째 선택
        if (queries.selectSelectedBook().executeAsOneOrNull() == null) {
            books.firstOrNull()?.let { queries.setSelectedBook(it.id) }
        }
    }

    // ── 공유 데이터 Push (로컬 → Supabase) ──────────────────────────────────

    private suspend fun pushBookData(bookId: String) {
        // 트랜잭션
        queries.selectByBookId(bookId).executeAsList().forEach { t ->
            if (t.remote_id.isBlank()) {
                runCatching {
                    val dto = supabase.postgrest.from("transactions").insert(
                        TransactionRemoteDto(bookId = bookId, title = t.title, amount = t.amount,
                            type = t.type, category = t.category, date = t.date, time = t.time,
                            note = t.note, asset = t.asset, toAsset = t.to_asset, createdBy = t.created_by,
                            categoryEmoji = t.category_emoji)
                    ) { select() }.decodeSingle<TransactionRemoteDto>()
                    queries.updateTransactionRemoteId(dto.id, t.id)
                }
            }
        }

        // 고정지출
        queries.selectAllFixedExpensesIncludingInactiveByBookId(bookId).executeAsList().forEach { fe ->
            if (fe.remote_id.isBlank()) {
                runCatching {
                    val dto = supabase.postgrest.from("fixed_expenses").insert(
                        FixedExpenseRemoteDto(bookId = bookId, title = fe.title, amount = fe.amount,
                            category = fe.category, asset = fe.asset, dayOfMonth = fe.day_of_month.toInt(),
                            startYear = fe.start_year.toInt(), startMonth = fe.start_month.toInt(),
                            note = fe.note, isActive = fe.is_active != 0L)
                    ) { select() }.decodeSingle<FixedExpenseRemoteDto>()
                    queries.updateFixedExpenseRemoteId(dto.id, fe.id)
                }
            }
        }

        // 상위 카테고리
        queries.selectParentCategoriesByBookId(bookId).executeAsList().forEach { p ->
            if (p.remote_id.isBlank()) {
                runCatching {
                    val dto = supabase.postgrest.from("parent_categories").insert(
                        ParentCategoryRemoteDto(bookId = bookId, name = p.name, emoji = p.emoji,
                            type = p.type, sortOrder = p.sort_order.toInt())
                    ) { select() }.decodeSingle<ParentCategoryRemoteDto>()
                    queries.updateParentCategoryRemoteId(dto.id, p.id)
                }
            }
        }

        // 하위 카테고리 (상위의 remote_id 참조)
        queries.selectUserCategoriesByBookId(bookId).executeAsList().forEach { uc ->
            if (uc.remote_id.isBlank()) {
                val parentRemoteId = queries.selectParentCategoryRemoteId(uc.parent_id)
                    .executeAsOneOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
                runCatching {
                    val dto = supabase.postgrest.from("user_categories").insert(
                        UserCategoryRemoteDto(bookId = bookId, name = uc.name, emoji = uc.emoji,
                            parentRemoteId = parentRemoteId, type = uc.type, sortOrder = uc.sort_order.toInt())
                    ) { select() }.decodeSingle<UserCategoryRemoteDto>()
                    queries.updateUserCategoryRemoteId(dto.id, uc.id)
                }
            }
        }

        // 자산 그룹
        queries.selectAssetGroupsByBookId(bookId).executeAsList().forEach { g ->
            if (g.remote_id.isBlank()) {
                runCatching {
                    val dto = supabase.postgrest.from("asset_groups").insert(
                        AssetGroupRemoteDto(bookId = bookId, name = g.name, emoji = g.emoji,
                            key = g.key, sortOrder = g.sort_order.toInt(), isLiability = g.is_liability != 0L)
                    ) { select() }.decodeSingle<AssetGroupRemoteDto>()
                    queries.updateAssetGroupRemoteId(dto.id, g.id)
                }
            }
        }

        // 자산
        queries.selectAssetsByBookId(bookId).executeAsList().forEach { a ->
            if (a.remote_id.isBlank()) {
                runCatching {
                    val dto = supabase.postgrest.from("assets").insert(
                        AssetRemoteDto(bookId = bookId, name = a.name, emoji = a.emoji,
                            owner = a.owner, initialBalance = a.initial_balance,
                            groupKey = a.group_key, sortOrder = a.sort_order.toInt())
                    ) { select() }.decodeSingle<AssetRemoteDto>()
                    queries.updateAssetRemoteId(dto.id, a.id)
                }
            }
        }
    }

    // ── 공유 데이터 Pull (Supabase → 로컬) ──────────────────────────────────

    private suspend fun pullBookData(bookId: String) {
        // 기존 로컬 데이터 초기화 (클린 슬레이트)
        queries.deleteTransactionsByBookId(bookId)
        queries.deleteFixedExpensesByBookId(bookId)
        queries.deleteUserCategoriesByBookId(bookId)
        queries.deleteParentCategoriesByBookId(bookId)
        queries.deleteAssetsByBookId(bookId)
        queries.deleteAssetGroupsByBookId(bookId)

        // 상위 카테고리 (하위 카테고리보다 먼저)
        val remoteToLocalParent = mutableMapOf<String, Long>()
        supabase.postgrest.from("parent_categories")
            .select { filter { eq("book_id", bookId) } }
            .decodeList<ParentCategoryRemoteDto>().forEach { p ->
                queries.insertParentWithBook(p.name, p.emoji, p.type, p.sortOrder.toLong(), bookId)
                val localId = queries.lastInsertRowId().executeAsOne()
                queries.updateParentCategoryRemoteId(p.id, localId)
                remoteToLocalParent[p.id] = localId
            }

        // 하위 카테고리
        supabase.postgrest.from("user_categories")
            .select { filter { eq("book_id", bookId) } }
            .decodeList<UserCategoryRemoteDto>().forEach { uc ->
                val localParentId = remoteToLocalParent[uc.parentRemoteId] ?: return@forEach
                queries.insertUserCategoryWithBook(uc.name, uc.emoji, localParentId, uc.type, uc.sortOrder.toLong(), bookId)
                val localId = queries.lastInsertRowId().executeAsOne()
                queries.updateUserCategoryRemoteId(uc.id, localId)
            }

        // 자산 그룹
        supabase.postgrest.from("asset_groups")
            .select { filter { eq("book_id", bookId) } }
            .decodeList<AssetGroupRemoteDto>().forEach { g ->
                queries.insertAssetGroupWithBook(g.name, g.emoji, g.key, g.sortOrder.toLong(),
                    if (g.isLiability) 1L else 0L, bookId)
                val localId = queries.lastInsertRowId().executeAsOne()
                queries.updateAssetGroupRemoteId(g.id, localId)
            }

        // 자산
        supabase.postgrest.from("assets")
            .select { filter { eq("book_id", bookId) } }
            .decodeList<AssetRemoteDto>().forEach { a ->
                queries.insertAssetWithBook(a.name, a.emoji, a.owner, a.initialBalance,
                    a.groupKey, a.sortOrder.toLong(), bookId)
                val localId = queries.lastInsertRowId().executeAsOne()
                queries.updateAssetRemoteId(a.id, localId)
            }

        // 고정지출
        supabase.postgrest.from("fixed_expenses")
            .select { filter { eq("book_id", bookId) } }
            .decodeList<FixedExpenseRemoteDto>().forEach { fe ->
                queries.insertFixedExpenseWithBookFull(fe.title, fe.amount, fe.category, fe.asset,
                    fe.dayOfMonth.toLong(), fe.startYear.toLong(), fe.startMonth.toLong(),
                    fe.note, if (fe.isActive) 1L else 0L, bookId)
                val localId = queries.lastInsertRowId().executeAsOne()
                queries.updateFixedExpenseRemoteId(fe.id, localId)
            }

        // 트랜잭션
        supabase.postgrest.from("transactions")
            .select { filter { eq("book_id", bookId) } }
            .decodeList<TransactionRemoteDto>().forEach { t ->
                queries.insertWithBookAndCreator(t.title, t.amount, t.type, t.category, t.categoryEmoji, t.date,
                    t.time, t.note, t.asset, t.toAsset, null, bookId, t.createdBy)
                val localId = queries.lastInsertRowId().executeAsOne()
                queries.updateTransactionRemoteId(t.id, localId)
            }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun com.myapp.budget.db.BookEntity.toBook() = Book(
        id = id, name = name, colorHex = color_hex, iconEmoji = icon_emoji,
        ownerId = owner_id, isSelected = is_selected == 1L,
    )

    private fun BookMemberDto.toMember() = BookMember(
        id = id, bookId = bookId, userId = userId,
        role = MemberRole.valueOf(role), joinedAt = joinedAt,
    )
}
