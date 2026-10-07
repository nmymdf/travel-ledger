package com.archiekuo.travelledger.logic

import org.json.JSONObject
import java.net.URLEncoder

/** A cover image candidate: a small preview and a larger image to download when chosen. */
data class CoverCandidate(val previewUrl: String, val imageUrl: String)

/** Builds Wikipedia/Wikimedia Commons queries and parses their JSON. No API key needed. */
object CoverSearch {
    const val USER_AGENT = "TravelLedger/1.0 (personal Android app; author ArchieKUO)"

    private val noise = listOf(
        "美食之旅", "親子遊", "自由行", "跟團", "快閃", "之旅", "旅行", "旅遊", "小旅行", "行程", "遊記", "假期", "度假",
        "美食", "購物", "賞櫻", "賞楓", "賞雪", "滑雪", "蜜月", "畢業", "家族", "親子", "自駕", "跳島", "夏日", "冬日",
        "春日", "秋日", "週末", "連假", "出差", "一日遊", "二日遊", "三日遊", "遊",
    )

    /** "東京美食之旅" -> "東京", "2024 大阪親子遊 5天" -> "大阪". Returns "" when nothing is left. */
    fun keyword(tripName: String): String {
        var s = tripName
            .replace(Regex("""\d{2,4}\s*年?"""), " ")
            .replace(Regex("""\d+\s*(天|日|晚|夜)"""), " ")
            .replace(Regex("""[!-/:-@\[-`{-~、。,!?()()「」【】·・]"""), " ")
        noise.sortedByDescending { it.length }.forEach { s = s.replace(it, " ") }
        return s.trim().split(Regex("""\s+""")).firstOrNull { it.isNotBlank() } ?: ""
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** zh.wikipedia: best matching article's lead image plus its English title (for Commons). */
    fun wikipediaUrl(keyword: String): String =
        "https://zh.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
            "&generator=search&gsrlimit=1&gsrsearch=${enc(keyword)}" +
            "&prop=pageimages|langlinks&piprop=thumbnail&pithumbsize=1600&lllang=en"

    fun commonsUrl(query: String): String =
        "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&generator=search&gsrnamespace=6&gsrlimit=12&gsrsearch=${enc("$query filetype:bitmap")}" +
            "&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1600"

    /** Returns the lead image and the English article title, if any. */
    fun parseWikipedia(json: String): Pair<CoverCandidate?, String?> {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return null to null
        if (pages.length() == 0) return null to null
        val page = pages.getJSONObject(0)
        val thumb = page.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotEmpty() }
        val en = page.optJSONArray("langlinks")?.optJSONObject(0)?.let { it.optString("title").ifEmpty { it.optString("*") } }
            ?.takeIf { it.isNotEmpty() }
        return thumb?.let { CoverCandidate(it, it) } to en
    }

    /** Landscape JPEG/PNG photos only; covers are wide. */
    fun parseCommons(json: String): List<CoverCandidate> {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val out = mutableListOf<Pair<Int, CoverCandidate>>()
        for (i in 0 until pages.length()) {
            val p = pages.getJSONObject(i)
            val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = info.optString("mime")
            val w = info.optInt("width")
            val h = info.optInt("height")
            if (mime !in listOf("image/jpeg", "image/png") || w < 1000 || h <= 0 || w < h * 1.2) continue
            val url = info.optString("thumburl").ifEmpty { info.optString("url") }
            if (url.isEmpty()) continue
            out += p.optInt("index", i) to CoverCandidate(url, url)
        }
        return out.sortedBy { it.first }.map { it.second }
    }
}
