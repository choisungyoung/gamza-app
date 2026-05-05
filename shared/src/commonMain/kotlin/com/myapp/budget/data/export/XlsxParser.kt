package com.myapp.budget.data.export

/**
 * XLSX 파서. STORE·DEFLATE 압축, 공유 문자열(sharedStrings), 날짜 시리얼 숫자 모두 지원.
 */
internal object XlsxParser {

    data class ParsedSheet(val name: String, val rows: List<List<String>>)

    fun parse(xlsxBytes: ByteArray): List<ParsedSheet> {
        val zip = parseZipEntries(xlsxBytes)
        val workbookXml = zip["xl/workbook.xml"]?.decodeToString() ?: return emptyList()
        // 공유 문자열 테이블 로드 (외부 Excel 파일에서 문자열은 여기에 저장됨)
        val sharedStrings = parseSharedStrings(zip["xl/sharedStrings.xml"]?.decodeToString() ?: "")
        val sheetMeta = parseSheetMeta(workbookXml)
        return sheetMeta.map { (name, id) ->
            val xml = zip["xl/worksheets/sheet$id.xml"]?.decodeToString()
                ?: return@map ParsedSheet(name, emptyList())
            ParsedSheet(name, parseWorksheet(xml, sharedStrings))
        }
    }

    // ── 공유 문자열 테이블 파싱 ──────────────────────────────────────────
    // Excel/Google Sheets는 문자열을 셀에 직접 넣지 않고 이 테이블에 모은 뒤
    // 셀에는 인덱스만 저장함. <c t="s"><v>22</v></c> → sharedStrings[22]
    private fun parseSharedStrings(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val siRe = Regex("""<si>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
        val tRe  = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
        for (m in siRe.findAll(xml)) {
            // rich-text 셀은 <t> 여러 개 → 모두 이어 붙임
            val text = tRe.findAll(m.groupValues[1]).joinToString("") { it.groupValues[1] }
            result.add(unescapeXml(text))
        }
        return result
    }

    // ── workbook.xml에서 시트 이름/ID 추출 ───────────────────────────────
    private fun parseSheetMeta(xml: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val re = Regex("""<sheet\b[^>]+\bname="([^"]+)"[^>]+\bsheetId="([^"]+)"""")
        for (m in re.findAll(xml)) result.add(m.groupValues[1] to m.groupValues[2])
        return result.sortedBy { it.second.toIntOrNull() ?: 0 }
    }

    // ── worksheet XML 파싱 ───────────────────────────────────────────────
    private fun parseWorksheet(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows   = mutableListOf<List<String>>()
        val rowRe  = Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
        // <c 태그 전체 속성 캡처 후 r= / t= 를 별도 추출 (속성 순서 무관)
        val cellRe   = Regex("""<c\b([^>]*)>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL)
        val refRe    = Regex("""\br="([A-Z]+)(\d+)"""")
        val inlineRe = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
        val numRe    = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
        val typeRe   = Regex("""\bt="([^"]+)"""")

        for (rowM in rowRe.findAll(xml)) {
            val rowContent = rowM.groupValues[1]
            val cellMap = mutableMapOf<Int, String>()
            for (cm in cellRe.findAll(rowContent)) {
                val attrs = cm.groupValues[1]
                val refMatch = refRe.find(attrs) ?: continue
                val col   = colToIndex(refMatch.groupValues[1])
                val inner = cm.groupValues[2]
                val cellType = typeRe.find(attrs)?.groupValues?.get(1)

                val value = when (cellType) {
                    "s" -> {
                        // 공유 문자열 인덱스 → 실제 문자열로 변환
                        val idx = numRe.find(inner)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        sharedStrings.getOrElse(idx) { "" }
                    }
                    "inlineStr" -> {
                        // 인라인 문자열 (여러 <t> 이어 붙임)
                        inlineRe.findAll(inner).joinToString("") { it.groupValues[1] }
                    }
                    else -> {
                        // 숫자·날짜 시리얼·일반 텍스트 → raw 값 그대로 반환
                        inlineRe.find(inner)?.groupValues?.get(1)
                            ?: numRe.find(inner)?.groupValues?.get(1)
                            ?: ""
                    }
                }
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
}
