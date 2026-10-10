package com.archiekuo.travelledger.logic

/** A place parsed from pasted text or a map app's share. */
data class ParsedPlace(val title: String, val location: String, val categoryHint: String?, val minuteOfDay: Int? = null)

/** Turns pasted research notes and shared map links into itinerary items. */
object PlanParser {
    private val urlRegex = Regex("""https?://\S+""")

    // Keyword → category name (matching the default categories). First match wins, so lodging ("飯店") goes before food ("飯").
    private val hints = listOf(
        "住宿" to listOf("飯店", "酒店", "旅館", "民宿", "ホテル", "Hotel", "hotel", "Inn", "check-in", "入住", "退房", "住宿"),
        "交通" to listOf("機場", "空港", "車站", "駅", "新幹線", "JR", "巴士", "バス", "租車", "Airport", "Station", "地鐵", "捷運", "KTX", "高鐵", "計程車", "轉乘", "接駁", "AREX"),
        "吃" to listOf(
            "早餐", "午餐", "晚餐", "宵夜", "下午茶", "拉麵", "ラーメン", "壽司", "寿司", "燒肉", "焼肉", "烤肉", "炸雞", "蔘雞湯", "部隊鍋", "冷麵",
            "火鍋", "牛舌", "餃子", "小吃", "餐", "食堂", "居酒屋", "咖哩", "カレー", "麵", "うどん", "そば", "丼", "飯", "料理", "甜點", "スイーツ",
            "パン", "麵包", "Cafe", "cafe", "咖啡", "茶", "Restaurant", "restaurant",
        ),
        "購物" to listOf("百貨", "商場", "Outlet", "outlet", "唐吉訶德", "ドン・キホーテ", "藥妝", "超市", "市場", "商店街", "Mall", "購物", "免稅", "伴手禮", "Olive Young"),
        "景點" to listOf(
            "寺", "神社", "宮", "城", "塔", "公園", "博物館", "美術館", "展望", "水族館", "動物園", "樂園", "ランド", "山", "海", "湖", "島", "橋",
            "老街", "夜市", "壁畫村", "韓屋", "韓服", "Temple", "Park", "Museum", "Tower", "Shrine", "Palace",
        ),
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

    private val headerLine = Regex("""^\s*(?:day\s*\d+|d\d+|第\s*[一二三四五六七八九十\d]+\s*天)\s*[:：]?\s*$""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""(?<!\d)([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)(?!\d)""")
    private val hourWord = Regex("""(?<!\d)([01]?\d|2[0-3])\s*點\s*(半|\d{1,2}\s*分)?""")
    // Period words and the time they stand for when no clock time is written.
    private val periods = listOf(
        "早上" to 9 * 60, "上午" to 9 * 60, "早餐" to 8 * 60, "中午" to 12 * 60, "午餐" to 12 * 60, "下午茶" to 15 * 60, "下午" to 14 * 60,
        "傍晚" to 17 * 60, "晚餐" to 18 * 60, "晚上" to 18 * 60, "宵夜" to 21 * 60,
    )
    private val separators = Regex("""\s*(?:→|➡|->|⇒|>)\s*""")

    /** The time written in [text] (09:30, 3點半, 下午3點, 晚上…), in minutes after midnight. */
    fun findTime(text: String): Int? {
        val afternoon = text.contains("下午") || text.contains("晚上") || text.contains("傍晚")
        clock.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            return (if (afternoon && h < 12) h + 12 else h) * 60 + m.groupValues[2].toInt()
        }
        hourWord.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = when {
                m.groupValues[2] == "半" -> 30
                m.groupValues[2].isNotEmpty() -> m.groupValues[2].filter { it.isDigit() }.toInt()
                else -> 0
            }
            return (if (afternoon && h < 12) h + 12 else h) * 60 + min
        }
        return periods.firstOrNull { text.contains(it.first) }?.second
    }

    /**
     * A day's notes split into separate plan items: one per line (and per "→" step), with the time and a guessed
     * category. "Day 3" / "第三天" headings are skipped; a line that is only a link becomes the previous item's location.
     * Nothing else is dropped, so whatever the guess misses is still there to fix in the preview.
     */
    fun splitNote(text: String): List<ParsedPlace> {
        val out = mutableListOf<ParsedPlace>()
        for (raw in text.lines()) {
            if (raw.isBlank() || headerLine.matches(raw)) continue
            val lineUrl = urlRegex.find(raw)?.value
            val rest = raw.replace(urlRegex, " ").trim()
            if (rest.isEmpty() && lineUrl != null) {
                if (out.isNotEmpty() && out.last().location.isEmpty()) out[out.lastIndex] = out.last().copy(location = lineUrl)
                else out += ParsedPlace("地圖地點", lineUrl, null)
                continue
            }
            val lineTime = findTime(rest)
            val steps = rest.split(separators).filter { it.isNotBlank() }
            steps.forEachIndexed { i, step ->
                val time = findTime(step) ?: lineTime.takeIf { i == 0 }
                val title = step
                    .replace(clock, " ").replace(hourWord, " ")
                    .replace(Regex("""^\s*(?:[-*•・●○◎▶►✓✔☐□]|\d{1,3}[.、)):]|[(\(]\d{1,3}[)\)])\s*"""), "")
                    .let { t -> periods.fold(t) { acc, (w, _) -> if (w == "早餐" || w == "午餐" || w == "晚餐" || w == "下午茶" || w == "宵夜") acc else acc.replace(w, " ") } }
                    .replace(Regex("""\s+"""), " ").trim().trim('-', '—', ':', '：', ',', '，', '、', ' ')
                if (title.isEmpty()) return@forEachIndexed
                out += ParsedPlace(title, if (i == 0) lineUrl ?: "" else "", guessCategory(title) ?: guessCategory(step), time)
            }
        }
        return out
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
