package com.archiekuo.travelledger.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CoverArtTest {
    private fun d(m: Int) = LocalDate.of(2026, m, 10)

    @Test fun keywordsWin() {
        assertEquals(CoverSpec(CoverTheme.AUTUMN, listOf(Landmark.SEOUL_TOWER)), CoverArt.pick("韓國賞楓", d(10)))
        assertEquals(CoverSpec(CoverTheme.FOOD, listOf(Landmark.FUJI, Landmark.TOKYO_TOWER)), CoverArt.pick("東京美食之旅", d(6)))
        assertEquals(CoverSpec(CoverTheme.BEACH, emptyList()), CoverArt.pick("沖繩夏日跳島", d(7)))
        assertEquals(CoverTheme.SAKURA, CoverArt.pick("京都賞櫻", d(10)).theme)
        assertEquals(CoverTheme.SNOW, CoverArt.pick("北海道滑雪", d(5)).theme)
    }

    @Test fun seasonFromTheMonthWhenTheNameHasNone() {
        assertEquals(CoverSpec(CoverTheme.SAKURA, listOf(Landmark.PAGODA)), CoverArt.pick("京都五日", d(4)))
        assertEquals(CoverTheme.AUTUMN, CoverArt.pick("首爾自由行", d(11)).theme)
        assertEquals(CoverTheme.SNOW, CoverArt.pick("札幌親子遊", d(1)).theme)
        // Osaka in December: not a snow place, so a city scene.
        assertEquals(CoverSpec(CoverTheme.CITY, listOf(Landmark.OSAKA_CASTLE)), CoverArt.pick("大阪親子遊", d(12)))
        assertEquals(CoverTheme.BEACH, CoverArt.pick("峇里島", d(12)).theme)
    }

    @Test fun unknownPlacesGetTheGenericScene() {
        assertEquals(CoverSpec(CoverTheme.GENERIC, emptyList()), CoverArt.pick("跟阿嬤出去玩", d(6)))
        assertEquals(CoverSpec(CoverTheme.GENERIC, emptyList()), CoverArt.pick("", null))
        assertEquals(listOf(Landmark.EIFFEL), CoverArt.pick("巴黎蜜月", d(6)).landmarks)
        assertEquals(listOf(Landmark.TAIPEI_101), CoverArt.pick("台北跨年", d(12)).landmarks)
    }
}
