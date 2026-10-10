package com.archiekuo.travelledger.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** The map app a trip navigates with; null on the trip means "ask each time". */
enum class NavApp(val key: String, val label: String, val pkg: String) {
    GOOGLE("google", "Google 地圖", "com.google.android.apps.maps"),
    NAVER("naver", "Naver Map", "com.nhn.android.nmap"),
    KAKAO("kakao", "KakaoMap", "net.daum.android.map");

    companion object {
        fun of(key: String?): NavApp? = entries.firstOrNull { it.key == key }
    }
}

/** Opens a plan item's place in the trip's map app, falling back to any map app when that one is missing. */
object MapLauncher {
    private fun enc(s: String) = Uri.encode(s)

    /** What to search for: the address if one was written, otherwise the place name (a link is opened as is). */
    private fun query(title: String, location: String): String =
        if (location.isNotBlank() && !location.startsWith("http")) location else title

    /** The intents to try in order for [app]; the last one works with whatever map app is installed. */
    fun intents(app: NavApp?, title: String, location: String, ownPackage: String): List<Intent> {
        val q = query(title, location)
        val link = location.takeIf { it.startsWith("http") }
        val generic = Intent(Intent.ACTION_VIEW, Uri.parse(link ?: "geo:0,0?q=" + enc(listOf(title, location).filter { it.isNotBlank() }.joinToString(" "))))
        val specific = when (app) {
            null -> null
            NavApp.GOOGLE -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse(link ?: "https://www.google.com/maps/dir/?api=1&destination=" + enc(q)),
            ).setPackage(app.pkg)
            // Naver and Kakao don't open Google links: search the place by name there.
            NavApp.NAVER -> Intent(Intent.ACTION_VIEW, Uri.parse("nmap://search?query=${enc(q)}&appname=$ownPackage")).setPackage(app.pkg)
            NavApp.KAKAO -> Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://search?q=${enc(q)}")).setPackage(app.pkg)
        }
        return listOfNotNull(specific, generic)
    }

    fun open(context: Context, app: NavApp?, title: String, location: String) {
        val tries = intents(app, title, location, context.packageName)
        for ((i, intent) in tries.withIndex()) {
            try {
                context.startActivity(intent)
                if (i > 0 && app != null) Toast.makeText(context, "沒有安裝 ${app.label},改用其他地圖", Toast.LENGTH_SHORT).show()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(context, "找不到地圖 App", Toast.LENGTH_SHORT).show()
    }
}
