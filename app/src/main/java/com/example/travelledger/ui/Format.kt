package com.example.travelledger.ui

import com.example.travelledger.data.HOME_CURRENCY
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate

fun fmtDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

fun fmtMoney(v: Double, currency: String = HOME_CURRENCY): String =
    "$currency " + NumberFormat.getIntegerInstance().format(Math.round(v))

/** Plain decimal text without trailing zeros, e.g. 1200.0 -> "1200", 0.2150 -> "0.215". */
fun fmtNumber(v: Double): String =
    BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Amount shown with the currency's natural precision (up to 2 decimals). */
fun fmtAmount(v: Double, currency: String): String =
    "$currency " + NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(v)

/** Converts an original-currency amount to the home currency, rounded to 2 decimals. */
fun toHomeAmount(amount: Double, rate: Double): Double =
    BigDecimal(amount).multiply(BigDecimal(rate)).setScale(2, RoundingMode.HALF_UP).toDouble()

val COMMON_CURRENCIES = listOf(
    "TWD", "JPY", "USD", "KRW", "EUR", "HKD", "CNY", "THB", "SGD", "MYR", "GBP", "AUD", "VND",
)
