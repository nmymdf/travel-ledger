package com.archiekuo.travelledger.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Dragging the handle above the calculator makes the keys bigger or smaller, within limits, and remembers it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeypadResizeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dragHandleResizesKeys() {
        var saved: Float? = null
        rule.setContent {
            AppTheme {
                ExpenseEditScreen(
                    EditState(amount = "100"), true, emptyList(), emptyList(), emptyList(), emptyList(), listOf("TWD"), null, false,
                    ExpenseActions(saveKeypadHeight = { saved = it }),
                )
            }
        }
        val keyHeight = { rule.onNodeWithText("7").fetchSemanticsNode().size.height }
        val before = keyHeight()
        rule.onNodeWithContentDescription("拖曳調整計算機高度").performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f) }
        rule.waitForIdle()
        val bigger = keyHeight()
        assertTrue("keys grow when the handle goes up: $before → $bigger", bigger > before)
        assertTrue("height remembered", saved != null && saved!! > KEY_HEIGHT_DEFAULT)

        // Pushing far down stops at the smallest size.
        rule.onNodeWithContentDescription("拖曳調整計算機高度").performTouchInput { swipeDown(startY = centerY, endY = centerY + 3000f) }
        rule.waitForIdle()
        assertEquals(KEY_HEIGHT_MIN, saved!!, 0.5f)
    }
}
