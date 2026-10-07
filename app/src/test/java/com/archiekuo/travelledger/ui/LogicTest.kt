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
}
