package com.archiekuo.travelledger.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    private fun uris(text: String) = Links.find(text).map { it.uri }

    @Test fun webAddresses() {
        assertEquals(listOf("https://tabelog.com/kr/a/1"), uris("訂位 https://tabelog.com/kr/a/1 很難訂"))
        assertEquals(listOf("https://www.visitkorea.or.kr"), uris("官網 www.visitkorea.or.kr。"))
        assertEquals(listOf("https://maps.app.goo.gl/abc123"), uris("https://maps.app.goo.gl/abc123"))
        // Full-width punctuation right after a link is not part of it.
        assertEquals(listOf("https://a.com/x"), uris("看這裡https://a.com/x,很好吃"))
        assertEquals(listOf("http://a.com/b"), uris("(http://a.com/b)"))
    }

    @Test fun phoneNumbers() {
        assertEquals(listOf("tel:0227760000"), uris("電話 02-2776-0000"))
        assertEquals(listOf("tel:0912345678"), uris("0912 345 678 找小美"))
        assertEquals(listOf("tel:+8227765348"), uris("+82 2-776-5348"))
        assertEquals(listOf("tel:0312345678"), uris("03-1234-5678"))
    }

    @Test fun notPhoneNumbers() {
        assertEquals(emptyList<String>(), uris("2026-10-22 19:00 4 位 確認碼 AB123456"))
        assertEquals(emptyList<String>(), uris("NT$ 1,234,567"))
        assertEquals(emptyList<String>(), uris("0800")) // too short
    }

    @Test fun bothInOrder() {
        val found = Links.find("www.a.com 或 02-2776-0000")
        assertEquals(listOf(false, true), found.map { it.isPhone })
        assertEquals(listOf("www.a.com", "02-2776-0000"), found.map { it.text })
    }
}
