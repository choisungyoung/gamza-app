package com.myapp.budget.platform

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.delay

@Composable
actual fun rememberFileSaver(): FileSaverState {
    val context = LocalContext.current
    return remember {
        FileSaverState { bytes, fileName ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Downloads.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                    val itemUri = resolver.insert(collection, values)
                    if (itemUri != null) {
                        resolver.openOutputStream(itemUri)?.use { it.write(bytes) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(itemUri, values, null, null)
                    }
                } else {
                    val file = File(context.cacheDir, fileName)
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "엑셀 파일 공유"))
                }
            } catch (_: Exception) { }
        }
    }
}

@Composable
actual fun rememberFilePicker(onPicked: (ByteArray) -> Unit): FilePickerState {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            bytes?.let(onPicked)
        } catch (_: Exception) { }
    }
    return remember {
        FilePickerState {
            launcher.launch("*/*")
        }
    }
}

@Composable
actual fun OnBackPressed(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled) { onBack() }
}

@Composable
actual fun BackPressExitHandler(enabled: Boolean) {
    val context = LocalContext.current
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler(enabled = enabled) {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "한 번 더 누르면 앱이 종료됩니다", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000)
            backPressedOnce = false
        }
    }
}
