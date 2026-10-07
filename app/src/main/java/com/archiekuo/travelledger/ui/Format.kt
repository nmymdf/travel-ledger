package com.archiekuo.travelledger.ui

import com.archiekuo.travelledger.data.HOME_CURRENCY
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Zh = Locale.TRADITIONAL_CHINESE

fun fmtDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let {
    "%d/%02d/%02d".format(it.year, it.monthValue, it.dayOfMonth)
}

fun weekday(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Zh).let {
    if (it.startsWith("週")) it else "週" + it.removePrefix("星期")
}

/** "2024/06/12 週三" */
fun fmtDateWithWeekday(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).let { "${fmtDate(epochDay)} ${weekday(it)}" }

/** "6月12日 週三" */
fun fmtDayHeader(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).let { "${it.monthValue}月${it.dayOfMonth}日 ${weekday(it)}" }

fun fmtRange(start: Long, end: Long): String = "${fmtDate(start)} – ${fmtDate(end)}"

private fun symbol(currency: String) = if (currency == HOME_CURRENCY) "NT$" else currency

/** Home-currency money, rounded to whole dollars: "NT$ 28,450". */
fun fmtMoney(v: Double, currency: String = HOME_CURRENCY): String =
    symbol(currency) + " " + NumberFormat.getIntegerInstance().format(Math.round(v))

/** Plain decimal text without trailing zeros, e.g. 1200.0 -> "1200", 0.2150 -> "0.215". */
fun fmtNumber(v: Double): String =
    BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Rate with 4 decimals like a bank board: "0.2100". */
fun fmtRate(v: Double): String = BigDecimal(v).setScale(4, RoundingMode.HALF_UP).toPlainString()

/** Grouped amount with up to 2 decimals, no currency: "1,320" / "12.5". */
fun fmtPlain(v: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(v)

/** Typed keypad text shown with grouping: "1320.5" -> "1,320.5". */
fun fmtTyped(text: String): String {
    if (text.isEmpty()) return "0"
    val intPart = text.substringBefore('.')
    val grouped = intPart.toLongOrNull()?.let { NumberFormat.getIntegerInstance().format(it) } ?: intPart
    return if ('.' in text) grouped + "." + text.substringAfter('.') else grouped
}

/** Amount with currency code: "JPY 1,320". */
fun fmtAmount(v: Double, currency: String): String = "${symbol(currency)} ${fmtPlain(v)}"

/** Converts an original-currency amount to the home currency, rounded to 2 decimals. */
fun toHomeAmount(amount: Double, rate: Double): Double =
    BigDecimal(amount).multiply(BigDecimal(rate)).setScale(2, RoundingMode.HALF_UP).toDouble()

val COMMON_CURRENCIES = listOf(
    "TWD", "JPY", "USD", "KRW", "EUR", "HKD", "CNY", "THB", "SGD", "MYR", "GBP", "AUD", "VND",
)

val CURRENCY_NAMES = mapOf(
    "TWD" to "新台幣", "JPY" to "日圓", "USD" to "美元", "KRW" to "韓元", "EUR" to "歐元",
    "HKD" to "港幣", "CNY" to "人民幣", "THB" to "泰銖", "SGD" to "新加坡幣", "MYR" to "馬幣",
    "GBP" to "英鎊", "AUD" to "澳幣", "VND" to "越南盾",
)

fun currencyLabel(code: String): String = CURRENCY_NAMES[code]?.let { "$code $it" } ?: code

/** "14:32" */
fun fmtTime(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** "6/12 週三 14:32" */
fun fmtShortDateTime(epochDay: Long, minuteOfDay: Int?): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return "${d.monthValue}/${d.dayOfMonth} ${weekday(d)}" + (minuteOfDay?.let { " " + fmtTime(it) } ?: "")
}
