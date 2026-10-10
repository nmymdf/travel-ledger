package com.archiekuo.travelledger.logic

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The phone runs the same cases as the desktop planner (desktop/test/parser-cases.json, also run by
 * desktop/test/parser.test.mjs), so a note splits and classifies the same way on both.
 */
@RunWith(RobolectricTestRunner::class)
class ParserParityTest {
    private fun JSONObject.nullableString(k: String) = if (isNull(k)) null else getString(k)
    private fun JSONObject.nullableInt(k: String) = if (isNull(k)) null else getInt(k)

    @Test fun sameAnswersAsTheDesktop() {
        val cases = JSONArray(File("../desktop/test/parser-cases.json").readText())
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val input = c.getString("input")
            val where = "case $i ${c.getString("fn")}"
            when (c.getString("fn")) {
                "splitNote" -> {
                    val expect = c.getJSONArray("expect")
                    val got = PlanParser.splitNote(input)
                    assertEquals(where, expect.length(), got.size)
                    for (j in 0 until expect.length()) {
                        val e = expect.getJSONObject(j)
                        val g = got[j]
                        assertEquals("$where[$j]", listOf(e.getString("title"), e.nullableString("category"), e.nullableInt("minuteOfDay"), e.getString("location")),
                            listOf(g.title, g.categoryHint, g.minuteOfDay, g.location))
                    }
                }
                "findTime" -> assertEquals(where, if (c.isNull("expect")) null else c.getInt("expect"), PlanParser.findTime(input))
                "guessCategory" -> assertEquals(where, c.nullableString("expect"), PlanParser.guessCategory(input))
                "parseShare" -> {
                    val e = c.getJSONObject("expect")
                    val g = PlanParser.parseShare(input)
                    assertEquals(where, listOf(e.getString("title"), e.getString("location"), e.nullableString("category")), listOf(g.title, g.location, g.categoryHint))
                }
            }
        }
    }
}
