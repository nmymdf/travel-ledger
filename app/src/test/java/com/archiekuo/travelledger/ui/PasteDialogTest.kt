package com.archiekuo.travelledger.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.archiekuo.travelledger.logic.PlanParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The 貼上多筆 dialog hands the pasted text over; the parser turns it into places. */
@RunWith(RobolectricTestRunner::class)
class PasteDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pastedLinesBecomePlaces() {
        var received: String? = null
        rule.setContent { AppTheme { PasteDialog(onDismiss = {}, onAdd = { received = it }) } }
        rule.onNode(hasSetTextAction()).performTextInput("1. 伏見稻荷神社\n- 一蘭拉麵 京都河原町店\n• 錦市場")
        rule.onNodeWithText("加入待排").performClick()
        val places = PlanParser.parseLines(received!!)
        assertEquals(listOf("伏見稻荷神社", "一蘭拉麵 京都河原町店", "錦市場"), places.map { it.title })
        assertEquals(listOf("景點", "吃", "購物"), places.map { it.categoryHint })
    }
}
