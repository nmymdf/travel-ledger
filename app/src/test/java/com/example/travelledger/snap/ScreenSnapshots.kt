package com.example.travelledger.snap

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.example.travelledger.data.Category
import com.example.travelledger.data.ExpenseRow
import com.example.travelledger.data.Member
import com.example.travelledger.data.PaymentMethod
import com.example.travelledger.data.Trip
import com.example.travelledger.data.TripCurrencyRate
import com.example.travelledger.data.TripSummary
import com.example.travelledger.ui.*
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
        TripSummary(4, "韓國首爾快閃", LocalDate.of(2023, 9, 15).toEpochDay(), LocalDate.of(2023, 9, 18).toEpochDay(), null, null, 18900.0, 2, "KRW"),
        TripSummary(5, "泰國曼谷", LocalDate.of(2023, 4, 3).toEpochDay(), LocalDate.of(2023, 4, 8).toEpochDay(), null, null, 24100.0, 3, "THB"),
    )
    private val trip = Trip(1, "東京美食之旅", d(6, 10), d(6, 15), budget = 40000.0, splitEnabled = true)
    private val members = listOf("我", "小美", "小華", "阿姨").mapIndexed { i, n -> Member(i + 1L, 1, n) }
    private val expenses = listOf(
        ExpenseRow(1, d(6, 12), 1320.0, "JPY", 0.21, 277.2, "吃", "", -1, "信用卡", "一蘭拉麵 本店"),
        ExpenseRow(2, d(6, 12), 680.0, "JPY", 0.21, 142.8, "交通", "", -1, "行動支付", "地鐵一日券"),
        ExpenseRow(3, d(6, 12), 5980.0, "JPY", 0.21, 1255.8, "購物", "", -1, "信用卡", "唐吉訶德"),
        ExpenseRow(4, d(6, 12), 150.0, "JPY", 0.21, 31.5, "其他", "", -1, "現金", "自動販賣機飲料"),
        ExpenseRow(5, d(6, 11), 1080.0, "JPY", 0.21, 226.8, "吃", "", -1, "現金", "東京車站便當"),
        ExpenseRow(6, d(6, 1), 18000.0, "TWD", 1.0, 18000.0, "住宿", "", -1, "信用卡", "新宿飯店 5 晚"),
        ExpenseRow(7, d(5, 20), 8516.0, "TWD", 1.0, 8516.0, "交通", "", -1, "信用卡", "來回機票"),
    )
    private val rates = listOf(TripCurrencyRate(1, "JPY", 0.21), TripCurrencyRate(1, "USD", 32.5))
    private val categories = listOf("交通", "住宿", "購物", "吃", "景點", "飲料", "其他").mapIndexed { i, n -> Category(i + 1L, n, i) }
    private val methods = listOf("現金", "信用卡", "行動支付").mapIndexed { i, n -> PaymentMethod(i + 1L, n, i) }

    private fun both(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot("${name}_light") { AppTheme(ThemeMode.LIGHT, content) }
        paparazzi.snapshot("${name}_dark") { AppTheme(ThemeMode.DARK, content) }
    }

    @Test fun home() = both("home") { TripListScreen(trips, {}, {}, {}, today) }

    @Test fun homeEmpty() = paparazzi.snapshot { AppTheme(ThemeMode.LIGHT) { TripListScreen(emptyList(), {}, {}, {}, today) } }

    @Test fun tripEdit() = both("tripEdit") {
        TripEditScreen(trip, members.map { it.name }, null, {}, {}, { _, _ -> }, today)
    }

    @Test fun tripDetail() = both("tripDetail") {
        TripDetailScreen(trip, members, expenses, rates, {}, {}, {}, {}, {}, { _, _ -> }, {}, today)
    }

    @Test fun expenseNewKeypad() = both("expenseKeypad") {
        ExpenseEditScreen(
            EditState(amount = "1320", currency = "JPY", rate = "0.21", categoryId = 4, paymentId = 2, date = d(6, 12)),
            categories, methods, isNew = true, keypadOpen = true, onKeypadOpen = {},
            onEdit = {}, onKey = {}, onCurrency = {}, onSave = {}, onDelete = {}, onBack = {},
        )
    }

    @Test fun expenseForm() = both("expenseForm") {
        ExpenseEditScreen(
            EditState(amount = "1320", currency = "JPY", rate = "0.21", categoryId = 4, paymentId = 2, date = d(6, 12), note = "一蘭拉麵 本店"),
            categories, methods, isNew = false, keypadOpen = false, onKeypadOpen = {},
            onEdit = {}, onKey = {}, onCurrency = {}, onSave = {}, onDelete = {}, onBack = {},
        )
    }

    @Test fun categories() = both("categories") {
        ManageListScreen(
            "分類管理",
            categories.map { c -> categoryStyle(c.name, c.icon, c.color).let { ManageItem(c.id, c.name, it.icon, it.color) } },
            true, "其他", "", {}, { _, _, _ -> }, { _, _, _, _ -> }, {}, { _, _ -> },
        )
    }

    @Test fun settings() = both("settings") { SettingsScreen(ThemeMode.DARK, {}, {}, {}, {}, "0.3.0") }
}
