package com.myapp.budget.data.export

/**
 * 순수 Kotlin으로 구현한 XLSX(ZIP STORE) 빌더.
 * 외부 라이브러리 없이 KMP commonMain에서 동작.
 */
internal object XlsxBuilder {

    data class SheetData(val name: String, val rows: List<List<String>>)

    // ── CRC-32 ──────────────────────────────────────────────────────────────
    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    private fun crc32(data: ByteArray): Long {
        var crc = -1
        for (b in data) crc = CRC_TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return (crc.toLong() xor 0xFFFFFFFFL) and 0xFFFFFFFFL
    }

    // ── 바이트 빌더 헬퍼 ────────────────────────────────────────────────────
    private fun MutableList<Byte>.writeInt16LE(v: Int) {
        add((v and 0xFF).toByte()); add(((v shr 8) and 0xFF).toByte())
    }
    private fun MutableList<Byte>.writeInt32LE(v: Long) {
        add((v and 0xFF).toByte()); add(((v shr 8) and 0xFF).toByte())
        add(((v shr 16) and 0xFF).toByte()); add(((v shr 24) and 0xFF).toByte())
    }
    private fun MutableList<Byte>.writeBytes(b: ByteArray) { b.forEach { add(it) } }

    // ── 공개 API ─────────────────────────────────────────────────────────────
    fun build(sheets: List<SheetData>): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        entries["[Content_Types].xml"] = contentTypes(sheets.size).encodeToByteArray()
        entries["_rels/.rels"] = rootRels().encodeToByteArray()
        entries["xl/workbook.xml"] = workbook(sheets).encodeToByteArray()
        entries["xl/_rels/workbook.xml.rels"] = workbookRels(sheets.size).encodeToByteArray()
        entries["xl/styles.xml"] = styles().encodeToByteArray()
        sheets.forEachIndexed { i, s ->
            entries["xl/worksheets/sheet${i + 1}.xml"] = worksheet(s.rows).encodeToByteArray()
        }
        return buildZip(entries)
    }

    // ── ZIP (STORE 방식) ────────────────────────────────────────────────────
    private fun buildZip(entries: Map<String, ByteArray>): ByteArray {
        val output = mutableListOf<Byte>()
        val central = mutableListOf<Byte>()
        var count = 0

        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = output.size.toLong()

            // Local file header
            output.writeInt32LE(0x04034b50L)
            output.writeInt16LE(20); output.writeInt16LE(0); output.writeInt16LE(0)
            output.writeInt16LE(0); output.writeInt16LE(0)
            output.writeInt32LE(crc)
            output.writeInt32LE(data.size.toLong()); output.writeInt32LE(data.size.toLong())
            output.writeInt16LE(nameBytes.size); output.writeInt16LE(0)
            output.writeBytes(nameBytes); output.writeBytes(data)

            // Central directory entry
            central.writeInt32LE(0x02014b50L)
            central.writeInt16LE(20); central.writeInt16LE(20)
            central.writeInt16LE(0); central.writeInt16LE(0)
            central.writeInt16LE(0); central.writeInt16LE(0)
            central.writeInt32LE(crc)
            central.writeInt32LE(data.size.toLong()); central.writeInt32LE(data.size.toLong())
            central.writeInt16LE(nameBytes.size)
            central.writeInt16LE(0); central.writeInt16LE(0); central.writeInt16LE(0)
            central.writeInt16LE(0); central.writeInt32LE(0)
            central.writeInt32LE(offset)
            central.writeBytes(nameBytes)
            count++
        }

        val cdOffset = output.size.toLong()
        output.writeBytes(central.toByteArray())

        // End of central directory
        output.writeInt32LE(0x06054b50L)
        output.writeInt16LE(0); output.writeInt16LE(0)
        output.writeInt16LE(count); output.writeInt16LE(count)
        output.writeInt32LE(central.size.toLong())
        output.writeInt32LE(cdOffset)
        output.writeInt16LE(0)

        return output.toByteArray()
    }

    // ── XML 생성 ──────────────────────────────────────────────────────────
    private fun contentTypes(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("\n") { i ->
            """<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overrides
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
    }

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(sheets: List<SheetData>): String {
        val items = sheets.mapIndexed { i, s ->
            """<sheet name="${escapeXml(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$items</sheets>
</workbook>"""
    }

    private fun workbookRels(sheetCount: Int): String {
        val rels = (1..sheetCount).joinToString("\n") { i ->
            """<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
<Relationship Id="rIdSt" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>"""

    private fun worksheet(rows: List<List<String>>): String {
        val rowsXml = rows.mapIndexed { ri, cells ->
            val r = ri + 1
            val cellsXml = cells.mapIndexed { ci, v ->
                """<c r="${colLetter(ci)}$r" t="inlineStr"><is><t>${escapeXml(v)}</t></is></c>"""
            }.joinToString("")
            """<row r="$r">$cellsXml</row>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>
$rowsXml
</sheetData>
</worksheet>"""
    }

    private fun colLetter(colIndex: Int): String {
        var idx = colIndex; var result = ""
        while (idx >= 0) {
            result = ('A' + (idx % 26)).toString() + result
            idx = idx / 26 - 1
        }
        return result
    }

    internal fun escapeXml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
