package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.Book
import com.myapp.budget.domain.model.BookMember
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
    suspend fun updateMemberRole(bookId: String, userId: String, role: com.myapp.budget.domain.model.MemberRole)
    suspend fun getOwnerDisplayNames(): Map<String, String>
    suspend fun syncBooks()
    suspend fun syncBookData(bookId: String)
    suspend fun backfillBookId(bookId: String)
    suspend fun migrateOfflineData(): Book?
}
