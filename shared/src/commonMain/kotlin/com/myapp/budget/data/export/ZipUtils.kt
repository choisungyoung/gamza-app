package com.myapp.budget.data.export

/** ZIP 아카이브에서 파일명 → 바이트 배열 맵을 반환. STORE·DEFLATE 모두 지원. */
internal expect fun parseZipEntries(bytes: ByteArray): Map<String, ByteArray>
