package com.myapp.budget.platform

import androidx.compose.runtime.Composable

/** 파일 저장·공유 상태 */
data class FileSaverState(val save: (bytes: ByteArray, fileName: String) -> Unit)

/** 파일 선택 상태 */
data class FilePickerState(val pick: () -> Unit)

@Composable
expect fun rememberFileSaver(): FileSaverState

@Composable
expect fun rememberFilePicker(onPicked: (ByteArray) -> Unit): FilePickerState

/** 메인 화면에서 두 번 눌러 앱 종료 처리 (Android 전용, iOS는 no-op) */
@Composable
expect fun BackPressExitHandler(enabled: Boolean)

/** 뒤로가기 처리 (Android: BackHandler, iOS: no-op) */
@Composable
expect fun OnBackPressed(enabled: Boolean, onBack: () -> Unit)
