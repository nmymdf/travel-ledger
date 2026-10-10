package com.archiekuo.travelledger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plan names and notes take several lines: Enter starts a new line, and the boxes show room for 2–3 lines. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp")
class PlanEditorInputTest {
    @get:Rule val rule = createComposeRule()

    private fun lineHeightAndBoxHeight(index: Int): Pair<Float, Int> {
        val node = rule.onAllNodes(hasSetTextAction())[index].fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.first()
        return (layout.multiParagraph.height / layout.lineCount) to node.size.height
    }

    @Test fun nameAndNoteTakeSeveralLines() {
        var item by mutableStateOf(PlanItem(0, 1, "", null, 19885))
        rule.setContent {
            AppTheme { PlanEditScreen(item, Trip(1, "首爾", 19884, 19889), listOf(Category(1, "吃", 0)), true, PlanActions(edit = { f -> item = f(item) })) }
        }
        // Empty boxes already leave room: the name two lines, the note three.
        val (nameLine, nameBox) = lineHeightAndBoxHeight(0)
        assertTrue("name box shows 2 lines ($nameBox vs line $nameLine)", nameBox >= nameLine * 2 - 1)

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("【交通】搭乘 01A 循環公車\n下山直達南大門市場站")
        assertEquals("【交通】搭乘 01A 循環公車\n下山直達南大門市場站", item.title)

        val noteIndex = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().lastIndex
        val (noteLine, noteBox) = lineHeightAndBoxHeight(noteIndex)
        assertTrue("note box shows 3 lines ($noteBox vs line $noteLine)", noteBox >= noteLine * 3 - 1)
        rule.onAllNodes(hasSetTextAction())[noteIndex].performTextInput("必點綠豆煎餅\n營業到 23:00\n記得帶現金")
        assertEquals("必點綠豆煎餅\n營業到 23:00\n記得帶現金", item.note)
    }
}
