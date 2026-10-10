package com.archiekuo.travelledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LogicTest {
    private fun keys(vararg k: String) = k.fold("") { acc, key -> applyKey(acc, key) }

    @Test fun digitsAndDecimal() = assertEquals("12.5", keys("1", "2", ".", "5"))
    @Test fun leadingDotBecomesZeroDot() = assertEquals("0.5", keys(".", "5"))
    @Test fun leadingZeroCollapses() = assertEquals("5", keys("0", "5"))
    @Test fun secondDotIgnored() = assertEquals("1.2", keys("1", ".", "2", "."))
    @Test fun limitsToTwoDecimals() = assertEquals("1.23", keys("1", ".", "2", "3", "4"))
    @Test fun limitsIntegerDigits() = assertEquals("123456789", keys(*"1234567890".map { it.toString() }.toTypedArray()))
    @Test fun backspace() = assertEquals("1", keys("1", "2", "⌫"))
    @Test fun backspaceOnEmpty() = assertEquals("", keys("⌫"))
    @Test fun doubleZero() = assertEquals("1200", keys("1", "2", "00"))
    @Test fun doubleZeroOnEmpty() = assertEquals("", keys("00"))
    @Test fun clear() = assertEquals("", keys("1", "2", "C"))

    @Test fun homeAmountRoundsToTwoDecimals() = assertEquals(1075.0, toHomeAmount(5000.0, 0.215), 0.0)
    @Test fun homeAmountRounding() = assertEquals(0.01, toHomeAmount(1.0, 0.005), 0.0)
    @Test fun numberFormatting() {
        assertEquals("1200", fmtNumber(1200.0))
        assertEquals("0.215", fmtNumber(0.215))
    }

    @Test fun shortDateRanges() {
        val d = { y: Int, m: Int, day: Int -> java.time.LocalDate.of(y, m, day).toEpochDay() }
        assertEquals("10/22 – 10/27", fmtRange(d(2026, 10, 22), d(2026, 10, 27), 2026))
        assertEquals("2023/12/20 – 12/26", fmtRange(d(2023, 12, 20), d(2023, 12, 26), 2026))
        assertEquals("2023/12/30 – 2024/1/2", fmtRange(d(2023, 12, 30), d(2024, 1, 2), 2026))
    }

    @Test fun backupReminderAfterATripEnds() {
        val today = java.time.LocalDate.of(2026, 11, 1)
        val day = { m: Int, d: Int -> java.time.LocalDate.of(2026, m, d).toEpochDay() }
        fun trip(id: Long, end: Long, sharedBy: String? = null) =
            com.archiekuo.travelledger.data.TripSummary(id, "T$id", end - 4, end, null, null, null, 0.0, 0, null, sharedBy)
        val korea = trip(1, day(10, 27))
        val friends = trip(2, day(10, 30), sharedBy = "小明")
        val next = trip(3, day(12, 5))
        // Never backed up: the ended trip of my own (not the shared one, not the upcoming one).
        assertEquals(korea, backupReminder(listOf(korea, friends, next), today, -1, -1))
        // Backed up after it ended, or put off after it ended: nothing.
        assertEquals(null, backupReminder(listOf(korea), today, day(10, 28), -1))
        assertEquals(null, backupReminder(listOf(korea), today, -1, day(10, 29)))
        // Backed up on the last day still reminds (the last day's spending may be missing).
        assertEquals(korea, backupReminder(listOf(korea), today, day(10, 27), -1))
    }
}
