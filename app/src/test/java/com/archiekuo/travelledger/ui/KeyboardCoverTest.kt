package com.archiekuo.travelledger.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.Trip
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app draws edge to edge, so the keyboard covers the screen instead of shrinking it. With the keyboard up,
 * the 備註 field of 記一筆 (at the bottom of the form) must still be above it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class KeyboardCoverTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun noteStaysAboveTheKeyboard() {
        val keyboardDp = 300f
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent {
            AppTheme {
                ExpenseEditScreen(
                    EditState(title = "拉麵", amount = "1200", currency = "JPY", rate = "0.21", categoryId = 1, paymentId = 1, note = "很好吃", noteOpen = true),
                    true,
                    listOf("吃", "交通", "購物", "住宿", "景點", "其他").mapIndexed { i, n -> Category(i + 1L, n, i) },
                    listOf("現金", "信用卡", "行動支付").mapIndexed { i, n -> PaymentMethod(i + 1L, n, i) },
                    emptyList(), emptyList(), listOf("TWD", "JPY"), null, false, ExpenseActions(),
                )
            }
        }
        keyboardUp(keyboardDp)
        rule.waitForIdle()
        val note = rule.onNodeWithText("很好吃").getBoundsInRoot()
        val screen = 640f
        assertTrue("備註 bottom ${note.bottom} must be above the keyboard top ${screen - keyboardDp}", note.bottom.value <= screen - keyboardDp)
    }

    /** 筆記 is the last field of the plan editor; with the keyboard up and the field focused it scrolls into view. */
    @Test fun planNoteStaysAboveTheKeyboard() {
        val keyboardDp = 300f
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent {
            AppTheme {
                PlanEditScreen(
                    PlanItem(1, 1, "景福宮", note = "記得預約韓服"),
                    Trip(1, "首爾", 20748, 20753),
                    listOf("吃", "交通", "購物", "住宿", "景點", "其他").mapIndexed { i, n -> Category(i + 1L, n, i) },
                    isNew = false, actions = PlanActions(),
                )
            }
        }
        keyboardUp(keyboardDp)
        rule.waitForIdle()
        rule.onNodeWithText("記得預約韓服").performScrollTo()
        val note = rule.onNodeWithText("記得預約韓服").getBoundsInRoot()
        assertTrue("筆記 bottom ${note.bottom} must be above the keyboard top ${640f - keyboardDp}", note.bottom.value <= 640f - keyboardDp)
    }

    private fun keyboardUp(keyboardDp: Float) {
        rule.runOnUiThread {
            val root: View = rule.activity.window.decorView
            val px = (keyboardDp * root.resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(root, insets)
        }
    }
}
