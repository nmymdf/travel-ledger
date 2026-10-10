package com.archiekuo.travelledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which map app 導航 opens, and with what, for each choice on the trip. */
@RunWith(RobolectricTestRunner::class)
class MapLauncherTest {
    private val me = "com.archiekuo.travelledger"

    @Test fun googleGetsDirectionsOrTheSavedLink() {
        val byAddress = MapLauncher.intents(NavApp.GOOGLE, "明洞餃子", "首爾中區明洞10街29", me)
        assertEquals("com.google.android.apps.maps", byAddress[0].`package`)
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=" + android.net.Uri.encode("首爾中區明洞10街29"), byAddress[0].dataString)
        val byLink = MapLauncher.intents(NavApp.GOOGLE, "明洞餃子", "https://maps.app.goo.gl/abc", me)
        assertEquals("https://maps.app.goo.gl/abc", byLink[0].dataString)
    }

    @Test fun naverAndKakaoSearchByName() {
        // A Google link means nothing to Naver or Kakao, so they search the place name.
        val naver = MapLauncher.intents(NavApp.NAVER, "廣藏市場", "https://maps.app.goo.gl/abc", me)
        assertEquals("com.nhn.android.nmap", naver[0].`package`)
        assertEquals("nmap://search?query=" + android.net.Uri.encode("廣藏市場") + "&appname=$me", naver[0].dataString)
        val kakao = MapLauncher.intents(NavApp.KAKAO, "景福宮", "", me)
        assertEquals("kakaomap://search?q=" + android.net.Uri.encode("景福宮"), kakao[0].dataString)
        // Each has a fallback that any map app can open.
        assertNull(naver.last().`package`)
        assertEquals("https://maps.app.goo.gl/abc", naver.last().dataString)
    }

    @Test fun askEachTimeUsesAnyMapApp() {
        val any = MapLauncher.intents(null, "景福宮", "", me).single()
        assertNull(any.`package`)
        assertEquals("geo:0,0?q=" + android.net.Uri.encode("景福宮"), any.dataString)
    }
}
