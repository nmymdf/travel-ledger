package com.archiekuo.travelledger.cover

import android.content.Context
import android.net.Uri
import com.archiekuo.travelledger.logic.CoverCandidate
import com.archiekuo.travelledger.logic.CoverSearch
import com.archiekuo.travelledger.ui.CoverStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Finds cover photo candidates on Wikipedia + Wikimedia Commons; failures just return fewer results. */
object CoverRepository {
    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", CoverSearch.USER_AGENT)
        c.connectTimeout = 8000
        c.readTimeout = 10000
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    suspend fun search(keyword: String, limit: Int = 3): List<CoverCandidate> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val out = mutableListOf<CoverCandidate>()
        val (lead, english) = get(CoverSearch.wikipediaUrl(keyword))?.let { CoverSearch.parseWikipedia(it) } ?: (null to null)
        lead?.let { out += it }
        val queries = listOfNotNull(english, keyword).distinct()
        for (q in queries) {
            if (out.size >= limit) break
            get(CoverSearch.commonsUrl(q))?.let { json ->
                CoverSearch.parseCommons(json).forEach { if (out.size < limit && out.none { o -> o.imageUrl == it.imageUrl }) out += it }
            }
        }
        out.take(limit)
    }

    /** Downloads the chosen image into app storage as a trip cover. */
    suspend fun download(context: Context, candidate: CoverCandidate): String? = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.cacheDir, "cover_dl.jpg")
            val c = URL(candidate.imageUrl).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", CoverSearch.USER_AGENT)
            c.connectTimeout = 8000
            c.readTimeout = 20000
            try {
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
            CoverStore.import(context, Uri.fromFile(tmp)).also { tmp.delete() }
        }.getOrNull()
    }
}
