package com.myapp.budget.data.export

import kotlinx.cinterop.*
import platform.zlib.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun parseZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val result = mutableMapOf<String, ByteArray>()
    var i = 0

    fun le16(o: Int) = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)
    fun le32(o: Int) = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or
            ((bytes[o + 2].toInt() and 0xFF) shl 16) or ((bytes[o + 3].toInt() and 0xFF) shl 24)

    while (i + 4 <= bytes.size) {
        when (le32(i)) {
            0x04034b50 -> {
                if (i + 30 > bytes.size) break
                val flags          = le16(i + 6)
                val method         = le16(i + 8)
                val compressedSize = le32(i + 18)
                val uncompSize     = le32(i + 22)
                val nameLen        = le16(i + 26)
                val extraLen       = le16(i + 28)
                val hasDataDesc    = (flags and 0x08) != 0
                val dataStart      = i + 30 + nameLen + extraLen
                if (dataStart > bytes.size) break

                val name = bytes.copyOfRange(i + 30, i + 30 + nameLen).decodeToString()

                if (!hasDataDesc && !name.endsWith("/")) {
                    val dataEnd = dataStart + compressedSize
                    if (dataEnd <= bytes.size) {
                        val compressed = bytes.copyOfRange(dataStart, dataEnd)
                        val entry = when (method) {
                            0 -> compressed
                            8 -> inflateRaw(compressed, uncompSize)
                            else -> null
                        }
                        if (entry != null) result[name] = entry
                    }
                    i = dataStart + compressedSize
                } else {
                    i = dataStart + (if (hasDataDesc) 0 else compressedSize)
                }
            }
            0x02014b50, 0x06054b50 -> break
            else -> i++
        }
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun inflateRaw(compressed: ByteArray, uncompressedSize: Int): ByteArray? {
    if (uncompressedSize <= 0 || compressed.isEmpty()) return null
    return try {
        val output = ByteArray(uncompressedSize)
        compressed.usePinned { src ->
            output.usePinned { dst ->
                memScoped {
                    val stream = alloc<z_stream>()
                    // alloc zeroes memory → zalloc/zfree/opaque are already null
                    stream.next_in = src.addressOf(0).reinterpret()
                    stream.avail_in = compressed.size.convert()
                    stream.next_out = dst.addressOf(0).reinterpret()
                    stream.avail_out = uncompressedSize.convert()
                    inflateInit2(stream.ptr, -15) // -15 = raw DEFLATE
                    inflate(stream.ptr, Z_FINISH)
                    inflateEnd(stream.ptr)
                }
            }
        }
        output
    } catch (_: Exception) { null }
}
