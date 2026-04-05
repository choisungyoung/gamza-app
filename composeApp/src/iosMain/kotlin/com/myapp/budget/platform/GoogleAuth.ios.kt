package com.myapp.budget.platform

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import org.koin.compose.koinInject

@Composable
actual fun GoogleSignInButton(
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val supabase: SupabaseClient = koinInject()
    val state = supabase.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            when (result) {
                is NativeSignInResult.Success -> onSuccess()
                is NativeSignInResult.Error -> onError(result.message)
                is NativeSignInResult.ClosedByUser -> {}
                is NativeSignInResult.NetworkError -> onError("네트워크 오류가 발생했습니다.")
            }
        }
    )
    OutlinedButton(
        onClick = { state.startFlow() },
        modifier = modifier,
        enabled = enabled,
    ) {
        Text("Google로 로그인")
    }
}
