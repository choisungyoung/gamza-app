package com.myapp.budget.platform

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.myapp.budget.signInWithGoogleIdToken
import com.myapp.budget.startNativeGoogleSignIn
import kotlinx.coroutines.launch

@Composable
actual fun GoogleSignInButton(
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            if (isLoading) return@OutlinedButton
            isLoading = true

            startNativeGoogleSignIn { idToken, rawNonce, error ->
                if (idToken != null && rawNonce != null) {
                    scope.launch {
                        runCatching {
                            signInWithGoogleIdToken(idToken, rawNonce)
                            onSuccess()
                        }.onFailure {
                            onError(it.message ?: "Unknown error")
                        }
                        isLoading = false
                    }
                } else {
                    scope.launch {
                        isLoading = false
                        if (error != null) onError(error)
                    }
                }
            }
        },
        modifier = modifier,
        enabled = enabled && !isLoading,
    ) {
        Text(if (isLoading) "로그인 중..." else "Google로 로그인")
    }
}
