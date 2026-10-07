package com.archiekuo.travelledger.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverSearchTest {
    @Test fun keywords() {
        assertEquals("東京", CoverSearch.keyword("東京美食之旅"))
        assertEquals("大阪", CoverSearch.keyword("大阪親子遊"))
        assertEquals("首爾", CoverSearch.keyword("韓國首爾快閃").removePrefix("韓國"))
        assertEquals("沖繩", CoverSearch.keyword("2024 沖繩夏日跳島 5天"))
        assertEquals("曼谷", CoverSearch.keyword("泰國曼谷").removePrefix("泰國"))
        assertEquals("", CoverSearch.keyword("之旅"))
    }

    @Test fun parsesWikipedia() {
        val json = """{"batchcomplete":true,"query":{"pages":[{"pageid":1,"ns":0,"title":"東京都","index":1,
            "thumbnail":{"source":"https://upload.wikimedia.org/a/Tokyo_1600.jpg","width":1600,"height":1000},
            "langlinks":[{"lang":"en","title":"Tokyo"}]}]}}"""
        val (lead, en) = CoverSearch.parseWikipedia(json)
        assertEquals("https://upload.wikimedia.org/a/Tokyo_1600.jpg", lead!!.imageUrl)
        assertEquals("Tokyo", en)
    }

    @Test fun parsesEmptyWikipedia() {
        val (lead, en) = CoverSearch.parseWikipedia("""{"batchcomplete":true}""")
        assertNull(lead); assertNull(en)
    }

    @Test fun commonsKeepsLandscapePhotosInRankOrder() {
        val json = """{"query":{"pages":[
            {"index":2,"imageinfo":[{"mime":"image/jpeg","width":4000,"height":2600,"thumburl":"https://x/b.jpg","url":"https://x/b_full.jpg"}]},
            {"index":1,"imageinfo":[{"mime":"image/jpeg","width":3000,"height":2000,"thumburl":"https://x/a.jpg"}]},
            {"index":3,"imageinfo":[{"mime":"image/jpeg","width":2000,"height":3000,"thumburl":"https://x/portrait.jpg"}]},
            {"index":4,"imageinfo":[{"mime":"image/svg+xml","width":3000,"height":1000,"thumburl":"https://x/map.png"}]},
            {"index":5,"imageinfo":[{"mime":"image/png","width":600,"height":300,"thumburl":"https://x/small.png"}]}
        ]}}"""
        assertEquals(listOf("https://x/a.jpg", "https://x/b.jpg"), CoverSearch.parseCommons(json).map { it.imageUrl })
    }
}
