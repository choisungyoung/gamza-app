package com.myapp.budget.data.repository

import com.myapp.budget.data.remote.FcmTokenDto
import com.myapp.budget.data.remote.SupabaseClientProvider
import com.myapp.budget.db.BudgetDatabase
import com.myapp.budget.domain.model.LocalUser
import com.myapp.budget.domain.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class AuthRepositoryImpl(
    private val database: BudgetDatabase,
) : AuthRepository {

    private val supabase get() = SupabaseClientProvider.client

    override fun authStateFlow(): Flow<LocalUser?> =
        supabase.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val user = supabase.auth.currentUserOrNull()
                    user?.let {
                        val displayName = it.userMetadata
                            ?.get("display_name")?.toString()?.trim('"') ?: ""
                        val localUser = LocalUser(it.id, it.email ?: "", displayName)
                        cacheUser(localUser)
                        localUser
                    }
                }
                else -> null
            }
        }

    override suspend fun currentUser(): LocalUser? {
        val cached = database.budgetQueries.selectLocalUser().executeAsOneOrNull()
        if (cached != null) {
            return LocalUser(cached.id, cached.email, cached.display_name)
        }
        val user = supabase.auth.currentUserOrNull() ?: return null
        val displayName = user.userMetadata
            ?.get("display_name")?.toString()?.trim('"') ?: ""
        return LocalUser(user.id, user.email ?: "", displayName)
    }

    override suspend fun signUp(email: String, password: String, displayName: String) {
        try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = kotlinx.serialization.json.buildJsonObject {
                    put("display_name", kotlinx.serialization.json.JsonPrimitive(displayName))
                }
            }
        } catch (e: Exception) {
            val message = e.message ?: ""
            when {
                message.contains("user_already_exists", ignoreCase = true) ||
                message.contains("already registered", ignoreCase = true) ->
                    throw Exception("이미 가입된 이메일입니다. 로그인을 시도해보세요.")
                message.contains("weak_password", ignoreCase = true) ->
                    throw Exception("비밀번호가 너무 짧습니다. 6자 이상 입력해주세요.")
                message.contains("invalid_email", ignoreCase = true) ->
                    throw Exception("올바른 이메일 형식이 아닙니다.")
                else -> throw e
            }
        }
        // 가입 후 자동 로그인된 세션을 끊어 로그인 화면으로 유도
        supabase.auth.signOut()
    }

    override suspend fun signIn(email: String, password: String) {
        try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("invalid_credentials", ignoreCase = true)) {
                throw Exception("이메일 또는 비밀번호가 올바르지 않습니다.")
            }
            throw e
        }
    }

    override suspend fun registerFcmToken(token: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        supabase.postgrest.from("user_fcm_tokens").upsert(
            FcmTokenDto(userId = userId, token = token)
        ) {
            onConflict = "user_id"
        }
    }

    override suspend fun signOut() {
        // 로그아웃 시 로컬 DB 전체 초기화 (서버가 유일한 저장소)
        with(database.budgetQueries) {
            deleteAllTransactions()
            deleteAllUserCategories()
            deleteAllParentCategories()
            deleteAllAssetGroups()
            deleteAllAssets()
            deleteAllBookMembers()
            deleteAllBooks()
            deleteAllSyncQueue()
            deleteLocalUser()
        }
        supabase.auth.signOut()
    }

    private fun cacheUser(user: LocalUser) {
        database.budgetQueries.upsertLocalUser(
            id = user.id,
            email = user.email,
            display_name = user.displayName,
            cached_at = Clock.System.now().toString(),
        )
    }
}
