package com.myapp.budget.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFileSaver(): FileSaverState {
    return remember {
        FileSaverState { bytes, fileName ->
            val path = "${NSTemporaryDirectory()}$fileName"
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            data.writeToFile(path, atomically = true)
            val fileUrl = NSURL.fileURLWithPath(path)
            val activityVC = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null
            )
            getRootViewController()?.presentViewController(activityVC, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(onPicked: (ByteArray) -> Unit): FilePickerState {
    val coordinatorRef = remember { mutableStateOf<DocumentPickerCoordinator?>(null) }
    return remember {
        FilePickerState {
            val coord = DocumentPickerCoordinator { nsData ->
                val len = nsData.length.toInt()
                if (len > 0) {
                    val bytes = ByteArray(len)
                    bytes.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                    }
                    onPicked(bytes)
                }
                coordinatorRef.value = null
            }
            coordinatorRef.value = coord

            @Suppress("DEPRECATION")
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf(
                    "org.openxmlformats.spreadsheetml.sheet",
                    "com.microsoft.excel.xls",
                    "public.spreadsheet"
                ),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
            )
            picker.delegate = coord
            getRootViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@Composable
actual fun OnBackPressed(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no hardware back button — no-op
}

@Composable
actual fun BackPressExitHandler(enabled: Boolean) {
    // iOS has no hardware back button — no-op
}

private fun getRootViewController(): UIViewController? =
    UIApplication.sharedApplication.keyWindow?.rootViewController

private class DocumentPickerCoordinator(
    private val onData: (NSData) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val data = NSData.create(contentsOfURL = url) ?: return
        onData(data)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
}
