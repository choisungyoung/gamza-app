package com.myapp.budget.data.export

import java.util.zip.ZipInputStream

internal actual fun parseZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val result = mutableMapOf<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) result[entry.name] = zip.readBytes()
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}
