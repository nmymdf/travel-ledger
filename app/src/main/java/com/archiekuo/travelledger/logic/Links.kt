package com.archiekuo.travelledger.logic

/** A web address or phone number found in free text, and the uri that opens it. */
data class FoundLink(val start: Int, val end: Int, val text: String, val uri: String) {
    val isPhone: Boolean get() = uri.startsWith("tel:")
}

/** Finds links in notes, addresses and booking details so they can be tapped. */
object Links {
    // Stops at whitespace, brackets and Chinese/Japanese/Korean text, which often follows a link without a space.
    private val url = Regex("""(?i)\b(?:https?://|www\.)[^\s()<>"\u2E80-\u9FFF\uAC00-\uD7AF\uFF00-\uFFEF]+""")

    // Starts with + or 0 (local and international numbers), 8–15 digits with optional spaces or dashes.
    // Dates (2026-10-22), times and prices don't start that way.
    private val phone = Regex("""(?<![\w/.])(?:\+\d{1,3}[\s-]?|0)\d(?:[\s-]?\d){6,13}(?![\w/])""")

    private val trailing = ".,:;!?)]}'\""

    fun find(text: String): List<FoundLink> {
        val found = mutableListOf<FoundLink>()
        for (m in url.findAll(text)) {
            val raw = m.value.trimEnd { it in trailing }
            val uri = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            found += FoundLink(m.range.first, m.range.first + raw.length, raw, uri)
        }
        for (m in phone.findAll(text)) {
            if (found.any { m.range.first < it.end && m.range.last >= it.start }) continue // part of a URL
            val digits = m.value.filter { it.isDigit() || it == '+' }
            if (digits.count { it.isDigit() } < 8) continue
            found += FoundLink(m.range.first, m.range.last + 1, m.value, "tel:$digits")
        }
        return found.sortedBy { it.start }
    }
}
