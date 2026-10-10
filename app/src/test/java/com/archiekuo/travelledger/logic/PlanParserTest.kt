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

    @Test fun dayNotesSplitIntoPlans() {
        val note = """
            第三天
            09:00 景福宮 韓服體驗
            中午 土俗村蔘雞湯
            https://maps.app.goo.gl/abc
            下午3點半 北村韓屋村 → 仁寺洞 → Olive Young 明洞店
            晚上 明洞夜市
            回飯店休息
            記得帶護照
        """.trimIndent()
        val items = PlanParser.splitNote(note)
        assertEquals(
            listOf("景福宮 韓服體驗", "土俗村蔘雞湯", "北村韓屋村", "仁寺洞", "Olive Young 明洞店", "明洞夜市", "回飯店休息", "記得帶護照"),
            items.map { it.title },
        )
        assertEquals(listOf(9 * 60, 12 * 60, 15 * 60 + 30, null, null, 18 * 60, null, null), items.map { it.minuteOfDay })
        assertEquals(listOf("景點", "吃", "景點", "景點", "購物", "景點", "住宿", null), items.map { it.categoryHint })
        // A link on its own line belongs to the item above it.
        assertEquals("https://maps.app.goo.gl/abc", items[1].location)
    }

    @Test fun timesInNotes() {
        assertEquals(19 * 60, PlanParser.findTime("19:00 烤肉"))
        assertEquals(15 * 60, PlanParser.findTime("下午3點 咖啡"))
        assertEquals(10 * 60 + 15, PlanParser.findTime("10點15分 集合"))
        assertEquals(8 * 60, PlanParser.findTime("飯店早餐"))
        assertEquals(null, PlanParser.findTime("景福宮"))
    }

    @Test fun whatTheNoteSaysWins() {
        val items = PlanParser.splitNote(
            """
            11:40～【交通】:搭乘 01A 循環公車下山直達「南大門市場」站(約 20 分鐘)。
            12:10 南大門市場 逛街
            [吃] 明洞餃子
            住宿:樂天飯店
            🛍 樂天免稅店
            步行前往 景福宮
            """.trimIndent(),
        )
        assertEquals(
            listOf("搭乘 01A 循環公車下山直達「南大門市場」站(約 20 分鐘)", "南大門市場 逛街", "明洞餃子", "樂天飯店", "🛍 樂天免稅店", "步行前往 景福宮"),
            items.map { it.title },
        )
        assertEquals(listOf("交通", "購物", "吃", "住宿", "購物", "交通"), items.map { it.categoryHint })
        assertEquals(11 * 60 + 40, items[0].minuteOfDay)
    }
}
