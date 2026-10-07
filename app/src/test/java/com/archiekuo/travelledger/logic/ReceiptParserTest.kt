package com.archiekuo.travelledger.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    /** Builds lines top to bottom; "label | amount" becomes two boxes on the same row like ML Kit returns them. */
    private fun receipt(vararg rows: String, tallFirst: Boolean = true): List<OcrLine> {
        val out = mutableListOf<OcrLine>()
        rows.forEachIndexed { i, row ->
            val h = if (i == 0 && tallFirst) 60 else 30
            val top = if (i == 0) 0 else 80 + (i - 1) * 40
            val parts = row.split(" | ")
            out += OcrLine(parts[0], 20, top, 300, top + h)
            if (parts.size > 1) out += OcrLine(parts[1], 500, top + 2, 700, top + h - 2)
        }
        return out.shuffled(java.util.Random(7)) // OCR block order is not reliable
    }

    @Test fun japaneseRamen() {
        val g = ReceiptParser.parse(
            receipt(
                "一蘭 新宿中央東口店", "領収書", "2024年6月12日 13:05",
                "ラーメン | ¥980", "替玉 | ¥210", "半熟卵 | ¥140",
                "小計 | ¥1,330", "(内消費税 | ¥120)", "合計 | ¥1,330", "お預り | ¥2,000", "お釣り | ¥670",
            ),
        )
        assertTrue(g.isReceipt)
        assertEquals("一蘭 新宿中央東口店", g.store)
        assertEquals(1330.0, g.total!!, 0.0)
        assertEquals("JPY", g.currency)
        assertEquals(LocalDate.of(2024, 6, 12), g.date)
        assertEquals(13 * 60 + 5, g.minuteOfDay)
    }

    @Test fun japaneseReiwaAndFullWidth() {
        val g = ReceiptParser.parse(
            receipt("ファミリーマート", "令和6年6月11日(火) 21:47", "おにぎり | ￥１５０", "お茶 | ￥１６０", "合計 | ￥３１０", "現計 | ￥３１０"),
        )
        assertEquals(310.0, g.total!!, 0.0)
        assertEquals(LocalDate.of(2024, 6, 11), g.date)
        assertEquals("ファミリーマート", g.store)
    }

    @Test fun totalOnSameLine() {
        val g = ReceiptParser.parse(receipt("唐吉訶德", "商品A ¥2,980", "商品B ¥3,000", "合計 ¥5,980", "お預り ¥10,000", "お釣 ¥4,020"))
        assertEquals(5980.0, g.total!!, 0.0)
    }

    @Test fun taiwanInvoice() {
        val g = ReceiptParser.parse(
            receipt("全家便利商店", "電子發票證明聯", "113-06-12 08:15:22", "拿鐵 | 65", "三明治 | 45", "總計 | 110", "現金 | 200", "找零 | 90"),
        )
        assertEquals(110.0, g.total!!, 0.0)
        assertEquals("TWD", g.currency)
        assertEquals(LocalDate.of(2024, 6, 12), g.date)
        assertEquals(8 * 60 + 15, g.minuteOfDay)
    }

    @Test fun koreanReceipt() {
        val g = ReceiptParser.parse(
            receipt("스타벅스 명동점", "영수증", "2023-09-16 10:20", "아메리카노 | 4,500", "라떼 | 5,000", "합계 | 9,500", "받은금액 | 10,000", "거스름돈 | 500"),
        )
        assertEquals(9500.0, g.total!!, 0.0)
        assertEquals("KRW", g.currency)
    }

    @Test fun englishReceipt() {
        val g = ReceiptParser.parse(
            receipt("BLUE BOTTLE COFFEE", "06/12/2024", "Latte | 6.50", "Muffin | 4.25", "Subtotal | 10.75", "Tax | 0.86", "TOTAL | 11.61", "CASH | 20.00", "CHANGE | 8.39"),
        )
        assertEquals(11.61, g.total!!, 0.001)
        assertEquals("BLUE BOTTLE COFFEE", g.store)
    }

    @Test fun phoneAndTimeAreNotAmounts() {
        assertEquals(emptyList<Double>(), ReceiptParser.amountsIn("TEL 03-1234-5678"))
        assertEquals(emptyList<Double>(), ReceiptParser.amountsIn("2024/06/12 13:05"))
        assertEquals(listOf(1330.0), ReceiptParser.amountsIn("合計 ¥1,330"))
        assertEquals(listOf(980.0), ReceiptParser.amountsIn("ラーメン x2 ¥980"))
    }

    @Test fun foodPhotoIsNotReceipt() {
        assertFalse(ReceiptParser.parse(receipt("一蘭", "ICHIRAN")).isReceipt)
    }
}
