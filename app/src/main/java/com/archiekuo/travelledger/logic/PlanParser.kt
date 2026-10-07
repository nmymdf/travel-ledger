package com.archiekuo.travelledger.logic

/** A place parsed from pasted text or a map app's share. */
data class ParsedPlace(val title: String, val location: String, val categoryHint: String?)

/** Turns pasted research notes and shared map links into itinerary items. */
object PlanParser {
    private val urlRegex = Regex("""https?://\S+""")

    // Keyword → category name (matching the default categories). First match wins, so lodging ("飯店") goes before food ("飯").
    private val hints = listOf(
        "住宿" to listOf("飯店", "酒店", "旅館", "民宿", "ホテル", "Hotel", "hotel", "Inn", "check-in", "入住"),
        "吃" to listOf("拉麵", "ラーメン", "壽司", "寿司", "燒肉", "焼肉", "餐", "食堂", "居酒屋", "咖哩", "カレー", "麵", "うどん", "そば", "丼", "飯", "料理", "甜點", "スイーツ", "パン", "Cafe", "cafe", "咖啡", "茶", "Restaurant", "restaurant"),
        "交通" to listOf("機場", "空港", "車站", "駅", "新幹線", "JR", "巴士", "バス", "租車", "Airport", "Station"),
        "購物" to listOf("百貨", "商場", "Outlet", "outlet", "唐吉訶德", "ドン・キホーテ", "藥妝", "超市", "市場", "商店街", "Mall", "購物"),
        "景點" to listOf("寺", "神社", "城", "塔", "公園", "博物館", "美術館", "展望", "水族館", "動物園", "樂園", "ランド", "山", "海", "湖", "島", "橋", "老街", "夜市", "Temple", "Park", "Museum", "Tower", "Shrine"),
    )

    fun guessCategory(text: String): String? = hints.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first

    /** One place per non-empty line; bullets and numbering are stripped, URLs become the location. */
    fun parseLines(text: String): List<ParsedPlace> = text.lines().mapNotNull { raw ->
        val url = urlRegex.find(raw)?.value ?: ""
        val title = raw.replace(urlRegex, " ")
            .replace(Regex("""^\s*(?:[-*•・●○◎▶►]|\d{1,3}[.、)):]|[(\(]\d{1,3}[)\)])\s*"""), "")
            .trim().trim('-', '—', ':', ':', ' ')
        when {
            title.isEmpty() && url.isEmpty() -> null
            else -> ParsedPlace(title.ifEmpty { "地圖地點" }, url, guessCategory(title))
        }
    }

    /**
     * Google Maps shares look like "一蘭 新宿中央東口店\nhttps://maps.app.goo.gl/…" (sometimes with an address
     * after a comma, sometimes only the link). The first non-link line is the name.
     */
    fun parseShare(text: String, subject: String? = null): ParsedPlace {
        val url = urlRegex.find(text)?.value ?: ""
        val firstLine = text.replace(urlRegex, "\n").lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        val name = (firstLine ?: subject?.trim()?.takeIf { it.isNotEmpty() } ?: "").let { line ->
            // "Ichiran Shinjuku, 3-34-11 Shinjuku, Tokyo" → keep the name before the address.
            val cut = line.indexOfFirst { it == ',' || it == ',' }
            if (cut > 0 && Regex("""\d""").containsMatchIn(line.substring(cut))) line.substring(0, cut).trim() else line
        }
        return ParsedPlace(name.ifEmpty { "地圖地點" }, url.ifEmpty { text.trim().takeIf { name.isEmpty() } ?: "" }, guessCategory(name))
    }
}
