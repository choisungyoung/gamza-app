package com.myapp.budget.domain.model

data class BookMember(
    val id: String,
    val bookId: String,
    val userId: String,
    val role: MemberRole,
    val joinedAt: String = "",
    val displayName: String = "",
)

enum class MemberRole {
    OWNER, EDITOR, VIEWER;

    fun canWrite(): Boolean = this == OWNER || this == EDITOR
    fun isOwner(): Boolean = this == OWNER
}
