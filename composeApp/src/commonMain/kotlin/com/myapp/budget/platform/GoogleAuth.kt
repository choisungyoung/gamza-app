package com.myapp.budget.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Google 로그인 버튼 (Android: Credential Manager, iOS: OAuth redirect) */
@Composable
expect fun GoogleSignInButton(
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
)
