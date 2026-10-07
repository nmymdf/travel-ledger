package com.archiekuo.travelledger.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanParserTest {
    @Test fun pastedList() {
        val items = PlanParser.parseLines(
            """
            1. 一蘭拉麵 本店
            - 淺草寺
            • 唐吉訶德 澀谷店 https://maps.app.goo.gl/abc

            (4) 新宿王子飯店
            晴空塔
            """.trimIndent(),
        )
        assertEquals(listOf("一蘭拉麵 本店", "淺草寺", "唐吉訶德 澀谷店", "新宿王子飯店", "晴空塔"), items.map { it.title })
        assertEquals(listOf("吃", "景點", "購物", "住宿", "景點"), items.map { it.categoryHint })
        assertEquals("https://maps.app.goo.gl/abc", items[2].location)
    }

    @Test fun googleMapsShare() {
        val p = PlanParser.parseShare("一蘭 新宿中央東口店\nhttps://maps.app.goo.gl/XyZ123")
        assertEquals("一蘭 新宿中央東口店", p.title)
        assertEquals("https://maps.app.goo.gl/XyZ123", p.location)
    }

    @Test fun shareWithAddress() {
        val p = PlanParser.parseShare("Ichiran Shinjuku, 3 Chome-34-11 Shinjuku, Tokyo 160-0022 https://maps.app.goo.gl/q")
        assertEquals("Ichiran Shinjuku", p.title)
        assertEquals("https://maps.app.goo.gl/q", p.location)
    }

    @Test fun shareOnlyLinkUsesSubject() {
        val p = PlanParser.parseShare("https://maps.app.goo.gl/q", subject = "淺草寺")
        assertEquals("淺草寺", p.title)
        assertEquals("景點", p.categoryHint)
    }
}
