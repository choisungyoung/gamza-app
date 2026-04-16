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
                is NativeSignInResult.ClosedByUser -> onError("Google 로그인을 사용할 수 없습니다. Google 계정 설정을 확인해주세요.")
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
