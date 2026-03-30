package com.myapp.budget.data.export

/**
 * 순수 Kotlin으로 구현한 XLSX(ZIP STORE) 파서.
 * 감자 가계부에서 내보낸 파일(STORE 방식) 전용.
 */
internal object XlsxParser {

    data class ParsedSheet(val name: String, val rows: List<List<String>>)

    fun parse(xlsxBytes: ByteArray): List<ParsedSheet> {
        val zip = parseZip(xlsxBytes)
        val workbookXml = zip["xl/workbook.xml"]?.decodeToString() ?: return emptyList()
        val sheetMeta = parseSheetMeta(workbookXml)
        return sheetMeta.map { (name, id) ->
            val xml = zip["xl/worksheets/sheet$id.xml"]?.decodeToString() ?: return@map ParsedSheet(name, emptyList())
            ParsedSheet(name, parseWorksheet(xml))
        }
    }

    // ── ZIP 파싱 (STORE 방식) ────────────────────────────────────────────
    private fun parseZip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        var i = 0
        while (i + 4 <= bytes.size) {
            val sig = readInt32LE(bytes, i)
            when (sig) {
                0x04034b50 -> {
                    if (i + 30 > bytes.size) break
                    val flags = readInt16LE(bytes, i + 6)
                    val method = readInt16LE(bytes, i + 8)
                    val compressedSize = readInt32LE(bytes, i + 18)
                    val nameLen = readInt16LE(bytes, i + 26)
                    val extraLen = readInt16LE(bytes, i + 28)
                    val dataDescriptor = (flags and 0x08) != 0
                    val nameStart = i + 30
                    val dataStart = nameStart + nameLen + extraLen
                    if (dataStart > bytes.size) break
                    val name = bytes.copyOfRange(nameStart, nameStart + nameLen).decodeToString()
                    if (!dataDescriptor && method == 0 && !name.endsWith("/")) {
                        val dataEnd = dataStart + compressedSize
                        if (dataEnd <= bytes.size) result[name] = bytes.copyOfRange(dataStart, dataEnd)
                        i = dataEnd
                    } else {
                        val skip = if (dataDescriptor) 0 else compressedSize
                        i = dataStart + skip
                    }
                }
                0x02014b50, 0x06054b50 -> break
                else -> i++
            }
        }
        return result
    }

    // ── workbook.xml에서 시트 이름/ID 추출 ───────────────────────────────
    private fun parseSheetMeta(xml: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        // <sheet name="..." sheetId="..." r:id="..."/>
        val re = Regex("""<sheet\b[^>]+\bname="([^"]+)"[^>]+\bsheetId="([^"]+)"""")
        for (m in re.findAll(xml)) result.add(m.groupValues[1] to m.groupValues[2])
        // sheetId 순서 보장이 안 될 수 있으니 name·id 순으로 정렬
        return result.sortedBy { it.second.toIntOrNull() ?: 0 }
    }

    // ── worksheet XML 파싱 ───────────────────────────────────────────────
    private fun parseWorksheet(xml: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRe = Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
        val cellRe = Regex("""<c\b[^>]+\br="([A-Z]+)(\d+)"[^>]*>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL)
        val inlineRe = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
        val numRe = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)

        for (rowM in rowRe.findAll(xml)) {
            val rowContent = rowM.groupValues[1]
            val cellMap = mutableMapOf<Int, String>()
            for (cm in cellRe.findAll(rowContent)) {
                val col = colToIndex(cm.groupValues[1])
                val inner = cm.groupValues[3]
                val value = inlineRe.find(inner)?.groupValues?.get(1)
                    ?: numRe.find(inner)?.groupValues?.get(1)
                    ?: ""
                cellMap[col] = unescapeXml(value)
            }
            if (cellMap.isNotEmpty()) {
                val max = cellMap.keys.max()
                rows.add((0..max).map { cellMap[it] ?: "" })
            }
        }
        return rows
    }

    private fun colToIndex(col: String): Int {
        var r = 0
        for (c in col) r = r * 26 + (c - 'A' + 1)
        return r - 1
    }

    private fun unescapeXml(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    private fun readInt16LE(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun readInt32LE(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
