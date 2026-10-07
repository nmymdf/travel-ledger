package com.archiekuo.travelledger.snap

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.ExpenseRow
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanRow
import com.archiekuo.travelledger.data.PlanStatus
import com.archiekuo.travelledger.data.Reservation
import com.archiekuo.travelledger.data.TitleSuggestion
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import com.archiekuo.travelledger.data.TripSummary
import com.archiekuo.travelledger.logic.ReceiptGuess
import com.archiekuo.travelledger.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Renders each screen with sample data in light and dark themes (run recordPaparazziDebug). */
class ScreenSnapshots {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.1)

    private val today = LocalDate.of(2024, 6, 12)
    private fun d(m: Int, day: Int) = LocalDate.of(2024, m, day).toEpochDay()

    private val trips = listOf(
        TripSummary(1, "東京美食之旅", d(6, 10), d(6, 15), 40000.0, null, 28450.0, 4, "JPY"),
        TripSummary(2, "沖繩夏日跳島", d(7, 20), d(7, 24), 30000.0, null, 6200.0, 2, "JPY"),
        TripSummary(3, "大阪親子遊", LocalDate.of(2023, 12, 20).toEpochDay(), LocalDate.of(2023, 12, 26).toEpochDay(), null, null, 52300.0, 4, "JPY"),
    )
    private val trip = Trip(1, "東京美食之旅", d(6, 10), d(6, 15), budget = 40000.0, splitEnabled = true)
    private val categories = listOf("吃", "交通", "購物", "住宿", "景點", "飲料", "其他").mapIndexed { i, n -> Category(i + 1L, n, i) }
    private fun cat(name: String) = categories.first { it.name == name }.id
    private fun row(id: Long, date: Long, min: Int?, title: String, amount: Double, cur: String, rate: Double, c: String, pay: String, thumb: Boolean = false) =
        ExpenseRow(id, date, min, title, amount, cur, rate, Math.round(amount * rate * 100) / 100.0, cat(c), c, "", -1, pay, "", if (thumb) "/nope.jpg" else null, null)
    private val expenses = listOf(
        row(1, d(6, 12), 12 * 60 + 5, "一蘭拉麵 本店", 1320.0, "JPY", 0.21, "吃", "信用卡", thumb = true),
        row(2, d(6, 12), 10 * 60 + 40, "地鐵一日券", 680.0, "JPY", 0.21, "交通", "行動支付"),
        row(3, d(6, 12), 16 * 60, "唐吉訶德 澀谷店", 5980.0, "JPY", 0.21, "購物", "信用卡", thumb = true),
        row(4, d(6, 12), 18 * 60 + 30, "自動販賣機飲料", 150.0, "JPY", 0.21, "飲料", "現金"),
        row(5, d(6, 11), 19 * 60, "敘敘苑 燒肉", 12800.0, "JPY", 0.21, "吃", "信用卡"),
        row(6, d(6, 1), null, "新宿飯店 5 晚", 18000.0, "TWD", 1.0, "住宿", "信用卡"),
        row(7, d(5, 20), null, "來回機票", 8516.0, "TWD", 1.0, "交通", "信用卡"),
    )
    private fun plan(id: Long, title: String, c: String, date: Long?, min: Int? = null, status: String = PlanStatus.TODO, res: String = Reservation.NONE, note: String = "", spent: Double = 0.0, loc: String = "") =
        PlanRow(id, title, cat(c), c, "", -1, date, min, status, res, note, loc, null, spent)
    private val plans = listOf(
        plan(1, "淺草寺", "景點", d(6, 12), 9 * 60, PlanStatus.DONE, loc = "淺草"),
        plan(2, "一蘭拉麵 本店", "吃", d(6, 12), 12 * 60, PlanStatus.DONE, spent = 277.2, loc = "x"),
        plan(3, "晴空塔", "景點", d(6, 12), 15 * 60, loc = "x"),
        plan(4, "敘敘苑 燒肉", "吃", d(6, 12), 19 * 60, res = Reservation.BOOKED, note = "19:00 4位"),
        plan(5, "築地場外市場", "吃", d(6, 11), 8 * 60),
        plan(6, "壽司大", "吃", null, res = Reservation.NEEDED),
        plan(7, "唐吉訶德 澀谷店", "購物", null),
        plan(8, "明治神宮", "景點", d(6, 13)),
        plan(9, "新宿飯店 check-in", "住宿", d(6, 10), 15 * 60, PlanStatus.DONE),
    )
    private val rates = listOf(TripCurrencyRate(1, "JPY", 0.21), TripCurrencyRate(1, "USD", 32.5))
    private val methods = listOf("現金", "信用卡", "行動支付").mapIndexed { i, n -> PaymentMethod(i + 1L, n, i) }
    private val editState = EditState(
        title = "一蘭拉麵 本店", amount = "1320", currency = "JPY", rate = "0.21",
        categoryId = 1, paymentId = 2, date = d(6, 12), minuteOfDay = 12 * 60 + 5,
    )

    private fun both(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot("${name}_light") { AppTheme(ThemeMode.LIGHT, content = content) }
        paparazzi.snapshot("${name}_dark") { AppTheme(ThemeMode.DARK, content = content) }
    }

    @Test fun home() = both("home") { TripListScreen(trips, {}, {}, {}, today, "0.5.0") }

    private fun trip(tab: TripTab, day: LocalDate = today) = @Composable {
        TripScreen(trip, 4, expenses, plans, tab, {}, TripActions(), day)
    }

    @Test fun tabToday() = both("tabToday", trip(TripTab.TODAY))
    @Test fun tabTodayBefore() = paparazzi.snapshot { AppTheme(ThemeMode.LIGHT) { trip(TripTab.TODAY, LocalDate.of(2024, 5, 28))() } }
    @Test fun tabPlan() = both("tabPlan", trip(TripTab.PLAN))
    @Test fun tabLedger() = both("tabLedger", trip(TripTab.LEDGER))
    @Test fun tabStats() = both("tabStats", trip(TripTab.STATS))

    @Test fun tabLedgerXL() = paparazzi.snapshot { AppTheme(ThemeMode.LIGHT, FontSize.XLARGE) { trip(TripTab.LEDGER)() } }

    @Test fun planEdit() = both("planEdit") {
        PlanEditScreen(
            PlanItem(4, 1, "敘敘苑 燒肉", cat("吃"), d(6, 12), 19 * 60, PlanStatus.TODO, Reservation.BOOKED, "19:00 · 4 位 · AB123", "新宿區西新宿 1-26-2", 6000.0, "必點:特選牛舌"),
            trip, categories, false, PlanActions(),
        )
    }

    @Test fun tripEdit() = both("tripEdit") {
        TripEditScreen(
            trip, listOf("我", "小美", "小華", "阿姨"), rates, null, false,
            {}, {}, { _, _, _ -> }, today,
        )
    }

    @Test fun expenseNew() = both("expenseNew") {
        ExpenseEditScreen(
            EditState(currency = "JPY", rate = "0.21", categoryId = 1, paymentId = 2, date = d(6, 12), minuteOfDay = 14 * 60 + 32),
            true, categories, methods, emptyList(), emptyList(), listOf("TWD", "JPY"), null, false, ExpenseActions(),
        )
    }

    @Test fun expenseReceipt() = both("expenseReceipt") {
        ExpenseEditScreen(
            editState.copy(title = "", amount = ""), true, categories, methods,
            listOf(PhotoItem(null, "/nope.jpg", PhotoType.RECEIPT)),
            emptyList(), listOf("TWD", "JPY"),
            ReceiptGuess(true, "一蘭 新宿中央東口店", 1330.0, "JPY", LocalDate.of(2024, 6, 12), 13 * 60 + 5),
            false, ExpenseActions(),
        )
    }

    @Test fun expenseEditXL() = paparazzi.snapshot {
        AppTheme(ThemeMode.LIGHT, FontSize.XLARGE) {
            ExpenseEditScreen(
                editState.copy(note = "點了特製拉麵", noteOpen = true), false, categories, methods,
                listOf(PhotoItem(1, "/a.jpg", PhotoType.RECEIPT), PhotoItem(2, "/b.jpg", PhotoType.MEMORY)),
                listOf(TitleSuggestion("一蘭拉麵 本店", 1, 0)), listOf("TWD", "JPY"), null, false, ExpenseActions(),
            )
        }
    }

    @Test fun categories() = both("categories") {
        ManageListScreen(
            "分類管理",
            categories.map { c -> categoryStyle(c.name, c.icon, c.color).let { ManageItem(c.id, c.name, it.icon, it.color) } },
            true, "其他", "", {}, { _, _, _ -> }, { _, _, _, _ -> }, {}, { _, _ -> },
        )
    }

    @Test fun settings() = both("settings") { SettingsScreen(AppSettings(ThemeMode.DARK, FontSize.LARGE), {}, {}, {}, {}, "0.5.0") }
}
