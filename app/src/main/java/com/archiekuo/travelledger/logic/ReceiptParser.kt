package com.archiekuo.travelledger.logic

import java.time.LocalDate

/** One recognized text line with its bounding box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/** What we could read from a photo. Every field is optional; the user confirms before applying. */
data class ReceiptGuess(
    val isReceipt: Boolean,
    val store: String? = null,
    val total: Double? = null,
    val currency: String? = null,
    val date: LocalDate? = null,
    val minuteOfDay: Int? = null,
) {
    val hasData: Boolean get() = store != null || total != null || date != null
}

/**
 * Heuristic receipt reader for Japanese, Taiwanese, Korean and English receipts.
 * Works on OCR lines with positions so a "合計" label can be paired with the amount printed on the same row.
 */
object ReceiptParser {
    // Strongest first. A label containing an excluded word is skipped.
    private val totalLabels = listOf(
        "総合計", "税込合計", "合計金額", "お会計", "ご請求額", "お支払", "合計", "総額", "現計",
        "總計", "總金額", "應付", "應收", "合計金額", "總額",
        "합계", "총액", "결제금액", "받을금액", "판매금액",
        "GRAND TOTAL", "TOTAL", "AMOUNT DUE", "BALANCE DUE",
        "小計", "SUBTOTAL",
    )
    private val excludedLabels = listOf(
        "預り", "預かり", "お釣", "釣銭", "おつり", "対象", "消費税", "税額", "内税", "外税", "ポイント", "点数", "點數",
        "找零", "找錢", "實收", "收現", "稅額", "數量",
        "거스름", "받은금액", "부가세", "과세",
        "CHANGE", "CASH", "TENDER", "TAX", "QTY", "ITEMS",
    )
    private val receiptWords = listOf(
        "領収", "レシート", "お買上", "税込", "合計", "統一發票", "電子發票", "發票", "收據", "交易明細",
        "영수증", "합계", "RECEIPT", "INVOICE", "TOTAL",
    )
    private val storeStopWords = listOf(
        "領収", "レシート", "receipt", "RECEIPT", "TEL", "Tel", "電話", "〒", "發票", "统一", "統一", "영수증",
        "登録番号", "No.", "NO.", "日付", "時間", "年", "月", "www", "http", "@",
    )

    private val amountRegex = Regex("""(?:[¥￥$₩]|NT\$|円)?\s*(\d{1,3}(?:[,，]\d{3})+(?:\.\d{1,2})?|\d{1,7}(?:\.\d{1,2})?)\s*(?:円|元|원|-)?""")

    fun normalize(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                in '０'..'９' -> '0' + (c - '０')
                '，' -> ','
                '．' -> '.'
                '：' -> ':'
                '／' -> '/'
                '￥' -> '¥'
                '\\' -> '¥' // OCR often reads the yen sign as a backslash
                else -> c
            },
        )
    }

    /** Numbers in a line that look like money (not dates, times, phone numbers). */
    fun amountsIn(raw: String): List<Double> {
        val line = normalize(raw)
        if (Regex("""\d{2,4}[/\-.年]\d{1,2}[/\-.月]\d{1,2}""").containsMatchIn(line)) return emptyList()
        val cleaned = line
            .replace(Regex("""\d{1,2}:\d{2}(:\d{2})?"""), " ")
            .replace(Regex("""\d{2,4}-\d{2,4}-\d{3,4}"""), " ")
            .replace(Regex("""\d+\s*%"""), " ")
            .replace(Regex("""[x×*]\s*\d+|\d+\s*(個|点|點|件|개|pcs)"""), " ")
        return amountRegex.findAll(cleaned).mapNotNull { m ->
            m.groupValues[1].replace(",", "").replace("，", "").toDoubleOrNull()
        }.filter { it > 0 }.toList()
    }

    private fun labelRank(text: String): Int? {
        val upper = normalize(text).uppercase().replace(" ", "")
        if (excludedLabels.any { upper.contains(it.uppercase().replace(" ", "")) }) return null
        val idx = totalLabels.indexOfFirst { upper.contains(it.uppercase().replace(" ", "")) }
        return idx.takeIf { it >= 0 }
    }

    /** Amount on the same visual row as [label]: in the label line itself, else the nearest line at that height. */
    private fun amountForLabel(label: OcrLine, lines: List<OcrLine>): Double? {
        amountsIn(label.text).lastOrNull()?.let { return it }
        val tolerance = maxOf(label.height, 12) * 0.7
        return lines.asSequence()
            .filter { it !== label && it.left >= label.left && kotlin.math.abs(it.centerY - label.centerY) <= tolerance }
            .filter { labelRank(it.text) == null && excludedLabels.none { w -> normalize(it.text).uppercase().contains(w.uppercase()) } }
            .sortedBy { kotlin.math.abs(it.centerY - label.centerY) }
            .mapNotNull { amountsIn(it.text).lastOrNull() }
            .firstOrNull()
    }

    fun findTotal(lines: List<OcrLine>): Double? {
        val labeled = lines.mapNotNull { l -> labelRank(l.text)?.let { l to it } }
            .sortedWith(compareBy({ it.second }, { -it.first.top }))
        for ((label, _) in labeled) amountForLabel(label, lines)?.let { return it }
        // No label: the largest money-looking number, ignoring lines about cash tendered or change.
        return lines.filter { l -> excludedLabels.none { normalize(l.text).uppercase().contains(it.uppercase()) } }
            .flatMap { amountsIn(it.text) }
            .filter { it < 10_000_000 }
            .maxOrNull()
    }

    fun findCurrency(all: String): String? {
        val t = normalize(all)
        return when {
            t.contains("₩") || t.contains("원") || Regex("[가-힣]").containsMatchIn(t) -> "KRW"
            t.contains("NT$") || t.contains("統一發票") || t.contains("電子發票") || t.contains("新台幣") -> "TWD"
            t.contains("¥") || t.contains("円") || Regex("[ぁ-んァ-ヶ]").containsMatchIn(t) -> "JPY"
            t.contains("฿") || t.contains("บาท") -> "THB"
            t.contains("HK$") -> "HKD"
            t.contains("S$") -> "SGD"
            t.contains("RM") && t.contains("SST") -> "MYR"
            else -> null
        }
    }

    fun findDate(all: String): LocalDate? {
        val t = normalize(all)
        fun ok(y: Int, m: Int, d: Int) = runCatching { LocalDate.of(y, m, d) }.getOrNull()?.takeIf { y in 2000..2100 }
        Regex("""令和\s*(\d{1,2}|元)\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""").find(t)?.let { m ->
            val n = m.groupValues[1].let { if (it == "元") 1 else it.toInt() }
            ok(2018 + n, m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        Regex("""(20\d{2})\s*[/\-.年]\s*(\d{1,2})\s*[/\-.月]\s*(\d{1,2})""").find(t)?.let { m ->
            ok(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        // Taiwan ROC year, e.g. 113/06/12 or 民國113年6月12日
        Regex("""(?<!\d)(1[01]\d)\s*[/\-.年]\s*(\d{1,2})\s*[/\-.月]\s*(\d{1,2})""").find(t)?.let { m ->
            ok(m.groupValues[1].toInt() + 1911, m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        return null
    }

    fun findTime(all: String): Int? {
        val m = Regex("""(?<!\d)([01]?\d|2[0-3])\s*[:時]\s*([0-5]\d)""").find(normalize(all)) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    /** The store name is usually the tallest text near the top of the receipt. */
    fun findStore(lines: List<OcrLine>): String? {
        if (lines.isEmpty()) return null
        val top = lines.minOf { it.top }
        val bottom = lines.maxOf { it.bottom }
        val limit = top + (bottom - top) * 0.3
        return lines.asSequence()
            .filter { it.top <= limit }
            .map { it to normalize(it.text).trim() }
            .filter { (_, t) ->
                val letters = t.count { it.isLetter() }
                letters >= 2 && letters * 2 >= t.length && storeStopWords.none { t.contains(it) } && labelRank(t) == null
            }
            .sortedWith(compareBy({ -it.first.height }, { it.first.top }))
            .map { it.second.trim('*', '-', '=', ' ', '【', '】') }
            .firstOrNull { it.isNotEmpty() }
    }

    fun isReceipt(lines: List<OcrLine>): Boolean {
        if (lines.size < 4) return false
        val all = normalize(lines.joinToString("\n") { it.text }).uppercase()
        val keyword = receiptWords.any { all.contains(it.uppercase()) }
        val priceLines = lines.count { amountsIn(it.text).isNotEmpty() }
        return (keyword && priceLines >= 2) || priceLines >= 4
    }

    fun parse(lines: List<OcrLine>): ReceiptGuess {
        if (!isReceipt(lines)) return ReceiptGuess(isReceipt = false)
        val all = lines.joinToString("\n") { it.text }
        return ReceiptGuess(
            isReceipt = true,
            store = findStore(lines),
            total = findTotal(lines),
            currency = findCurrency(all),
            date = findDate(all),
            minuteOfDay = findTime(all),
        )
    }
}
