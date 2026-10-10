package com.archiekuo.travelledger.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Tapping a web address or phone number really asks Android to open it — in editors and on detail screens. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class LinkTapTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val url = "https://tabelog.com/tokyo/A1301"
    private val note = "官網 $url 電話 03-1234-5678 必點牛舌"
    private val trip = Trip(1, "東京", 19884, 19889)
    private val categories = listOf(Category(1, "吃", 0))

    private fun started(): Intent? = shadowOf(rule.activity).nextStartedActivity

    /** Taps the middle of [piece] inside the text node that shows [whole]. */
    private fun tapInside(whole: String, piece: String) {
        val node = rule.onNode(hasText(whole) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        val at = whole.indexOf(piece) + piece.length / 2
        val box = layout.getBoundingBox(at)
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }
    }

    @Test fun planNoteLinkOpensFromTheText() {
        var item by mutableStateOf(PlanItem(1, 1, "敘敘苑", 1, 19885, note = note))
        rule.setContent { AppTheme { PlanEditScreen(item, trip, categories, false, PlanActions(edit = { f -> item = f(item) })) } }

        tapInside(note, url)
        val web = started()!!
        assertEquals(Intent.ACTION_VIEW, web.action)
        assertEquals(url, web.dataString)

        tapInside(note, "03-1234-5678")
        val call = started()!!
        assertEquals(Intent.ACTION_DIAL, call.action)
        assertEquals("tel:0312345678", call.dataString)

        // Tapping the plain words starts editing instead.
        tapInside(note, "必點牛舌")
        rule.waitForIdle()
        assertTrue(
            rule.onRoot().printToString(),
            rule.onAllNodes(hasSetTextAction() and hasText(note)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(null, started())
    }

    @Test fun buttonUnderTheFieldOpensToo() {
        val item = PlanItem(1, 1, "敘敘苑", 1, 19885, note = note)
        rule.setContent { AppTheme { PlanEditScreen(item, trip, categories, false, PlanActions()) } }
        rule.onNodeWithText("開啟 tabelog.com").performClick()
        assertEquals(url, started()!!.dataString)
        rule.onNodeWithText("撥打 03-1234-5678").performClick()
        assertEquals("tel:0312345678", started()!!.dataString)
    }

    @Test fun expenseNoteEditorAndDetail() {
        val state = EditState(title = "牛舌", amount = "100", note = note, noteOpen = true)
        var detail by mutableStateOf(false)
        rule.setContent {
            AppTheme {
                if (detail) ExpenseDetailScreen(state, categories, listOf(PaymentMethod(1, "現金", 0)), emptyList(), {}, {})
                else ExpenseEditScreen(state, false, categories, emptyList(), emptyList(), emptyList(), listOf("TWD"), null, false, ExpenseActions())
            }
        }
        tapInside(note, url)
        assertEquals(url, started()!!.dataString)

        detail = true
        rule.waitForIdle()
        tapInside(note, "03-1234-5678")
        assertEquals("tel:0312345678", started()!!.dataString)
    }

    @Test fun plainLinkText() {
        rule.setContent { AppTheme { LinkText(note) } }
        tapInside(note, url)
        assertEquals(url, started()?.dataString)
    }

    @Test fun plainWordsStartEditing() {
        var item by mutableStateOf(PlanItem(1, 1, "敘敘苑", 1, 19885, note = note))
        rule.setContent { AppTheme { PlanEditScreen(item, trip, categories, false, PlanActions(edit = { f -> item = f(item) })) } }
        tapInside(note, "必點牛舌")
        rule.waitForIdle()
        assertTrue(rule.onRoot().printToString(), rule.onAllNodes(hasSetTextAction() and hasText(note)).fetchSemanticsNodes().isNotEmpty())
    }
}
