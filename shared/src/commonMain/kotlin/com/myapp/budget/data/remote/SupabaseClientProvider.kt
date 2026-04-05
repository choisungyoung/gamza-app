package com.myapp.budget.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    private var _client: SupabaseClient? = null

    val client: SupabaseClient
        get() = _client ?: error("SupabaseClient not initialized. Call configure() first.")

    fun configure(url: String, anonKey: String, googleWebClientId: String = "") {
        _client = createSupabaseClient(url, anonKey) {
            install(Auth)
            install(Postgrest)
            install(ComposeAuth) {
                if (googleWebClientId.isNotBlank()) {
                    googleNativeLogin(serverClientId = googleWebClientId)
                }
            }
        }
    }
}
