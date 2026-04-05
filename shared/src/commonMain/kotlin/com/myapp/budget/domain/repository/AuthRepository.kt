package com.myapp.budget.domain.repository

import com.myapp.budget.domain.model.LocalUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun authStateFlow(): Flow<LocalUser?>
    suspend fun currentUser(): LocalUser?
    suspend fun signUp(email: String, password: String, displayName: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
}
