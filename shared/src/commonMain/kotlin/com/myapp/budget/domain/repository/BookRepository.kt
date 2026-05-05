package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.Book
import com.myapp.budget.domain.model.BookMember
import com.myapp.budget.domain.model.MemberRole
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getSelectedBook(): Flow<Book?>
    suspend fun createBook(name: String, colorHex: String, iconEmoji: String): Book
    suspend fun selectBook(bookId: String)
    suspend fun updateBook(bookId: String, name: String, colorHex: String, iconEmoji: String)
    suspend fun deleteBook(bookId: String)
    suspend fun generateInviteCode(bookId: String): String
    suspend fun joinByInviteCode(code: String): Book
    suspend fun getMembers(bookId: String): List<BookMember>
    suspend fun leaveBook(bookId: String): Book?
    suspend fun removeMember(bookId: String, userId: String)
    suspend fun updateMemberRole(bookId: String, userId: String, role: MemberRole)
    suspend fun getOwnerDisplayNames(): Map<String, String>
    /** 서버 멤버십 목록과 비교해 로컬에서 제거된 공유 가계부를 삭제하고 반환한다. */
    suspend fun syncBooks(): List<Book>
    suspend fun syncBookData(bookId: String)
    /** Supabase 호출 없이 로컬 DB에서 가계부와 연관 데이터를 삭제한다. */
    suspend fun removeBookLocally(bookId: String): Book?
    /** 현재 로그인된 사용자의 특정 가계부에서의 역할을 반환한다. */
    suspend fun getCurrentUserRole(bookId: String): MemberRole?
}
