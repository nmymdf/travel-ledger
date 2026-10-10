package com.archiekuo.travelledger.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.archiekuo.travelledger.logic.PlanParser
import com.archiekuo.travelledger.ui.EditState
import com.archiekuo.travelledger.ui.ExpenseEditViewModel
import com.archiekuo.travelledger.ui.LookupViewModel
import com.archiekuo.travelledger.ui.PlanEditViewModel
import com.archiekuo.travelledger.ui.splitPlan
import com.archiekuo.travelledger.ui.TripDetailViewModel
import com.archiekuo.travelledger.ui.TripListViewModel
import com.archiekuo.travelledger.ui.currentTrip
import com.archiekuo.travelledger.ui.toHomeAmount
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * End-to-end data tests: real Room database, real ViewModels, a realistic Tokyo trip.
 * Everything a screen does goes through the same calls the UI makes.
 */
@RunWith(RobolectricTestRunner::class)
class LedgerFlowTest {
    @get:Rule val main = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun d(m: Int, day: Int) = LocalDate.of(2024, m, day).toEpochDay()

    @Before fun setUp() { db = testDb() }
    @After fun tearDown() = db.close()

    private fun cat(name: String) = runBlocking { db.lookupDao().observeCategories().first().first { it.name == name }.id }
    private fun pay(name: String) = runBlocking { db.lookupDao().observePaymentMethods().first().first { it.name == name }.id }

    private fun createTrip(
        name: String = "東京美食之旅",
        start: Long = d(6, 10),
        end: Long = d(6, 15),
        members: List<String> = listOf("我", "小美", "小華", "阿姨"),
        rates: List<TripCurrencyRate> = listOf(TripCurrencyRate(0, "JPY", 0.21)),
        budget: Double? = 40000.0,
    ): Long {
        var id = -1L
        TripListViewModel(db).create(Trip(name = name, startDate = start, endDate = end, budget = budget), members, rates) { id = it }
        assertTrue("trip created", id > 0)
        return id
    }

    /** Records one expense exactly like the 記一筆 screen: keypad digits, currency switch, rate edit, save. */
    private fun record(
        trip: Long, title: String, amount: String, category: String, payment: String,
        date: Long, minute: Int? = null, currency: String? = null, rate: String? = null, plan: Long? = null,
    ): ExpenseEditViewModel {
        val vm = ExpenseEditViewModel(app, db, trip, null, saveMemoriesToGallery = false, planItemId = plan)
        assertTrue("editor ready", vm.state != null)
        currency?.let { vm.setCurrency(it) }
        rate?.let { r -> vm.edit { it.copy(rate = r) } }
        vm.edit { it.copy(title = title, categoryId = cat(category), paymentId = pay(payment), date = date, minuteOfDay = minute) }
        amount.forEach { vm.key(it.toString()) }
        var saved = false
        vm.save { saved = true }
        assertTrue("saved $title", saved)
        return vm
    }

    private fun rows(trip: Long) = runBlocking { db.expenseDao().observeRows(trip).first() }

    @Test fun realisticTripTotals() {
        val trip = createTrip()
        // Pre-paid before departure, in TWD.
        record(trip, "來回機票", "8516", "交通", "信用卡", d(5, 20), currency = "TWD")
        record(trip, "新宿飯店 5 晚", "18000", "住宿", "信用卡", d(6, 1), currency = "TWD")
        // On the trip, in JPY at the trip rate 0.21 (one card charge at its own rate).
        record(trip, "築地早餐", "2400", "吃", "現金", d(6, 11), 8 * 60, currency = "JPY")
        record(trip, "敘敘苑 燒肉", "12800", "吃", "信用卡", d(6, 11), 19 * 60)
        record(trip, "地鐵一日券", "680", "交通", "行動支付", d(6, 12), 10 * 60 + 40)
        record(trip, "一蘭拉麵 本店", "1320", "吃", "信用卡", d(6, 12), 12 * 60 + 5)
        record(trip, "唐吉訶德 澀谷店", "5980", "購物", "信用卡", d(6, 12), 16 * 60, rate = "0.215")
        record(trip, "自動販賣機", "150", "吃", "現金", d(6, 12), 18 * 60 + 30)
        record(trip, "晴空塔門票", "3100", "景點", "信用卡", d(6, 13), 15 * 60)
        record(trip, "藥妝", "8765", "購物", "信用卡", d(6, 14))

        val r = rows(trip)
        assertEquals(10, r.size)
        val expected = 8516.0 + 18000.0 + listOf(2400, 12800, 680, 1320, 150, 3100, 8765).sumOf { toHomeAmount(it.toDouble(), 0.21) } +
            toHomeAmount(5980.0, 0.215)
        assertEquals(expected, r.sumOf { it.homeAmount }, 0.001)

        // Conversion is stored per expense and rounded to cents.
        assertEquals(277.2, r.first { it.title == "一蘭拉麵 本店" }.homeAmount, 0.0)
        assertEquals(1285.7, r.first { it.title == "唐吉訶德 澀谷店" }.homeAmount, 0.0)
        assertEquals(0.215, r.first { it.title == "唐吉訶德 澀谷店" }.rate, 0.0)

        // Newest day first, later time first, untimed last within a day.
        assertEquals(
            listOf("藥妝", "晴空塔門票", "自動販賣機", "唐吉訶德 澀谷店", "一蘭拉麵 本店", "地鐵一日券", "敘敘苑 燒肉", "築地早餐", "新宿飯店 5 晚", "來回機票"),
            r.map { it.title },
        )

        // Trip list summary: total, members, foreign currencies used.
        val summary = runBlocking { db.tripDao().observeSummaries().first() }.single()
        assertEquals(expected, summary.totalHome, 0.001)
        assertEquals(4, summary.memberCount)
        assertEquals("JPY", summary.currencies)

        // Category breakdown used by the ledger and stats tabs.
        val byCat = r.groupBy { it.categoryName }.mapValues { e -> e.value.sumOf { it.homeAmount } }
        assertEquals(18000.0, byCat["住宿"]!!, 0.001)
        assertEquals(8516.0 + 142.8, byCat["交通"]!!, 0.001)
        assertEquals(504.0 + 2688.0 + 277.2 + 31.5, byCat["吃"]!!, 0.001)
    }

    @Test fun newExpenseStartsFromPreviousOne() {
        val trip = createTrip()
        record(trip, "一蘭拉麵", "1320", "吃", "信用卡", d(6, 12), currency = "JPY")
        val next = ExpenseEditViewModel(app, db, trip, null, false).state!!
        assertEquals("JPY", next.currency)
        assertEquals("0.21", next.rate)
        assertEquals(cat("吃"), next.categoryId)
        assertEquals(pay("信用卡"), next.paymentId)
        assertEquals("", next.amount)
        assertEquals("", next.title)
    }

    @Test fun firstUseOfACurrencyBecomesTheTripRate() {
        val trip = createTrip(rates = emptyList())
        record(trip, "明洞炸雞", "25000", "吃", "現金", d(6, 11), currency = "KRW", rate = "0.024")
        val rates = runBlocking { db.expenseDao().getRates(trip) }
        assertEquals(listOf("KRW" to 0.024), rates.map { it.currency to it.rate })
        // A second KRW expense picks the remembered rate up.
        val vm = ExpenseEditViewModel(app, db, trip, null, false)
        vm.setCurrency("KRW")
        assertEquals("0.024", vm.state!!.rate)
        // Switching back to TWD needs no rate.
        vm.setCurrency("TWD")
        assertEquals("1", vm.state!!.rate)
    }

    @Test fun cannotSaveWithoutAmountOrRate() {
        val trip = createTrip(rates = emptyList())
        val vm = ExpenseEditViewModel(app, db, trip, null, false)
        vm.setCurrency("THB")
        assertEquals("", vm.state!!.rate)
        "350".forEach { vm.key(it.toString()) }
        var saved = false
        vm.save { saved = true }
        assertTrue("no rate → not saved", !saved)
        vm.edit { it.copy(rate = "0.9") }
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(315.0, rows(trip).single().homeAmount, 0.0)
    }

    @Test fun rememberedTitleRestoresItsCategory() {
        val trip = createTrip()
        record(trip, "7-ELEVEN", "580", "購物", "現金", d(6, 11))
        val vm = ExpenseEditViewModel(app, db, trip, null, false)
        val s = vm.suggestions.single { it.title == "7-ELEVEN" }
        vm.edit { it.copy(categoryId = cat("吃")) }
        vm.pickSuggestion(s)
        assertEquals("7-ELEVEN", vm.state!!.title)
        assertEquals(cat("購物"), vm.state!!.categoryId)
    }

    @Test fun editingKeepsTheSameExpense() {
        val trip = createTrip()
        record(trip, "一蘭拉麵", "1320", "吃", "信用卡", d(6, 12), 12 * 60)
        val id = rows(trip).single().id
        val vm = ExpenseEditViewModel(app, db, trip, id, false)
        assertEquals("1320", vm.state!!.amount)
        assertEquals(12 * 60, vm.state!!.minuteOfDay)
        vm.key("C"); "1500".forEach { vm.key(it.toString()) }
        vm.edit { it.copy(title = "一蘭拉麵 加麵") }
        vm.save {}
        val r = rows(trip).single()
        assertEquals(id, r.id)
        assertEquals("一蘭拉麵 加麵", r.title)
        assertEquals(315.0, r.homeAmount, 0.0)
    }

    @Test fun photosShowAsThumbnailAndGoAwayWithTheExpense() {
        val trip = createTrip()
        record(trip, "一蘭拉麵", "1320", "吃", "信用卡", d(6, 12))
        val id = rows(trip).single().id
        runBlocking {
            db.photoDao().insert(Photo(expenseId = id, type = PhotoType.RECEIPT, path = "/x/receipt.jpg"))
            db.photoDao().insert(Photo(expenseId = id, type = PhotoType.MEMORY, path = "/x/ramen.jpg"))
        }
        assertEquals("/x/receipt.jpg", rows(trip).single().thumbPath)
        assertEquals(2, runBlocking { db.photoDao().pathsForTrip(trip) }.size)

        val vm = ExpenseEditViewModel(app, db, trip, id, false)
        assertEquals(2, vm.photos.size)
        vm.removePhoto(0)
        vm.setPhotoType(0, PhotoType.RECEIPT)
        vm.save {}
        val left = runBlocking { db.photoDao().forExpense(id) }
        assertEquals(listOf("/x/ramen.jpg" to PhotoType.RECEIPT), left.map { it.path to it.type })

        ExpenseEditViewModel(app, db, trip, id, false).delete {}
        assertTrue(rows(trip).isEmpty())
        assertTrue(runBlocking { db.photoDao().pathsForTrip(trip) }.isEmpty())
    }

    @Test fun deletingACategoryMovesExpensesToOther() {
        val trip = createTrip()
        record(trip, "晴空塔", "3100", "景點", "信用卡", d(6, 13))
        val lookups = LookupViewModel(db)
        lookups.deleteCategory(cat("景點"))
        assertEquals("其他", rows(trip).single().categoryName)

        // Deleting "其他" itself leaves the expense uncategorised rather than pointing at nothing.
        lookups.deleteCategory(cat("其他"))
        assertNull(rows(trip).single().categoryId)

        lookups.deleteMethod(pay("信用卡"))
        assertNull(rows(trip).single().paymentMethodName)
    }

    @Test fun categoriesCanBeAddedRenamedAndReordered() {
        val lookups = LookupViewModel(db)
        lookups.categories.value // start collecting not needed: the VM reads the flow value after we load it
        runBlocking { lookups.categories.first { it.isNotEmpty() } }
        lookups.addCategory("溫泉", "spa", 4)
        runBlocking { lookups.categories.first { list -> list.any { it.name == "溫泉" } } }
        val spa = runBlocking { db.lookupDao().observeCategories().first() }.last()
        assertEquals("溫泉" to "spa", spa.name to spa.icon)
        lookups.updateCategory(spa.id, "溫泉旅館", "hotel", 1)
        runBlocking { lookups.categories.first { list -> list.any { it.name == "溫泉旅館" } } }
        lookups.moveCategory(6, 0)
        val names = runBlocking { db.lookupDao().observeCategories().first() }.map { it.name }
        assertEquals("溫泉旅館", names.first())
    }

    /** A whole day written in one item's note becomes separate items on that day, with times and categories. */
    @Test fun splittingADayNote() {
        val trip = createTrip()
        val dayNote = runBlocking {
            db.planDao().insert(PlanItem(tripId = trip, title = "第三天行程", date = d(6, 12), note = "09:00 淺草寺\n中午 一蘭拉麵 本店\n晚上 新宿王子飯店 check-in"))
        }
        val original = runBlocking { db.planDao().get(dayNote)!! }
        val parsed = PlanParser.splitNote(original.note)
        val byName = runBlocking { db.lookupDao().getCategories() }.associateBy { it.name }
        val undo = runBlocking {
            splitPlan(db, original, parsed.map { p -> PlanItem(tripId = trip, title = p.title, date = d(6, 12), minuteOfDay = p.minuteOfDay, categoryId = p.categoryHint?.let { byName[it]?.id }) }, sharedTrip = false)
        }
        val plans = runBlocking { db.planDao().observeRows(trip).first() }
        assertEquals(listOf("淺草寺", "一蘭拉麵 本店", "新宿王子飯店 check-in"), plans.map { it.title })
        assertEquals(listOf(9 * 60, 12 * 60, 18 * 60), plans.map { it.minuteOfDay })
        assertEquals(listOf("景點", "吃", "住宿"), plans.map { it.categoryName })
        assertTrue(plans.all { it.date == d(6, 12) })
        // The original note is kept word for word as the day's note, for comparing.
        assertEquals("【第三天行程】\n09:00 淺草寺\n中午 一蘭拉麵 本店\n晚上 新宿王子飯店 check-in", runBlocking { db.dayNoteDao().get(trip, d(6, 12)) }!!.text)

        // 復原 puts everything back as it was.
        runBlocking { undo.undo() }
        val back = runBlocking { db.planDao().observeRows(trip).first() }
        assertEquals(listOf("第三天行程"), back.map { it.title })
        assertEquals(null, runBlocking { db.dayNoteDao().get(trip, d(6, 12)) })
    }

    @Test fun planningAndRecordingFromThePlan() {
        val trip = createTrip()
        val detail = TripDetailViewModel(db, trip)
        runBlocking { detail.categories.first { it.isNotEmpty() } }
        detail.addParsed(PlanParser.parseLines("1. 一蘭拉麵 本店\n- 淺草寺\n• 新宿王子飯店\n晴空塔 https://maps.app.goo.gl/x"), date = null)
        var plans = runBlocking { db.planDao().observeRows(trip).first() }
        assertEquals(listOf("一蘭拉麵 本店", "淺草寺", "新宿王子飯店", "晴空塔"), plans.map { it.title })
        assertEquals(listOf("吃", "景點", "住宿", "景點"), plans.map { it.categoryName })
        assertTrue(plans.all { it.date == null })
        assertEquals("https://maps.app.goo.gl/x", plans.last().location)

        // Schedule two of them on day 3; the rest stay unscheduled and sort last.
        val ramen = plans.first { it.title == "一蘭拉麵 本店" }.id
        val temple = plans.first { it.title == "淺草寺" }.id
        detail.movePlans(listOf(ramen, temple), d(6, 12))
        plans = runBlocking { db.planDao().observeRows(trip).first() }
        assertEquals(listOf(d(6, 12), d(6, 12), null, null), plans.map { it.date })

        // Booking info via the plan editor.
        val editor = PlanEditViewModel(db, trip, ramen, null, null)
        editor.edit { it.copy(minuteOfDay = 12 * 60, reservation = Reservation.BOOKED, reservationNote = "12:00 4位") }
        editor.save {}

        // "記一筆" from the plan: title and category come from it; saving ticks it off and links the money.
        val vm = record(trip, "", "1320", "吃", "信用卡", d(6, 12), 12 * 60 + 5, plan = ramen)
        val linked = rows(trip).single()
        assertEquals(ramen, linked.planItemId)
        val ramenRow = runBlocking { db.planDao().observeRows(trip).first() }.first { it.id == ramen }
        assertEquals(PlanStatus.DONE, ramenRow.status)
        assertEquals(277.2, ramenRow.spent, 0.0)
        assertEquals(Reservation.BOOKED, ramenRow.reservation)
        assertEquals(12 * 60, ramenRow.minuteOfDay)

        detail.setPlanStatus(temple, PlanStatus.SKIPPED)
        assertEquals(PlanStatus.SKIPPED, runBlocking { db.planDao().get(temple) }!!.status)

        // Deleting the plan keeps the expense, just unlinked.
        PlanEditViewModel(db, trip, ramen, null, null).delete {}
        assertNull(rows(trip).single().planItemId)
        assertEquals(3, runBlocking { db.planDao().observeRows(trip).first() }.size)
    }

    @Test fun recordFromPlanPrefillsTitleAndCategory() {
        val trip = createTrip()
        val id = runBlocking { db.planDao().insert(PlanItem(tripId = trip, title = "敘敘苑 燒肉", categoryId = cat("吃"))) }
        val s: EditState = ExpenseEditViewModel(app, db, trip, null, false, planItemId = id).state!!
        assertEquals("敘敘苑 燒肉", s.title)
        assertEquals(cat("吃"), s.categoryId)
    }

    @Test fun sharedMapPlaceIsPrefilled() {
        val trip = createTrip()
        val shared = PlanParser.parseShare("淺草寺\nhttps://maps.app.goo.gl/abc")
        val editor = PlanEditViewModel(db, trip, null, null, shared)
        assertEquals("淺草寺", editor.item!!.title)
        assertEquals("https://maps.app.goo.gl/abc", editor.item!!.location)
        assertEquals(cat("景點"), editor.item!!.categoryId)
        assertNull(editor.item!!.date)
        editor.save {}
        assertEquals(1, runBlocking { db.planDao().observeRows(trip).first() }.size)
    }

    @Test fun editingTheTripKeepsExistingMembers() {
        val trip = createTrip()
        val before = runBlocking { db.tripDao().getMembers(trip) }.associate { it.name to it.id }
        val t = runBlocking { db.tripDao().observeTrip(trip).first() }!!
        TripDetailViewModel(db, trip).update(
            t.copy(name = "東京吃吃喝喝", budget = 50000.0, coverTheme = "SAKURA"), listOf("我", "小美", "阿姨", "表哥"),
            listOf(TripCurrencyRate(trip, "JPY", 0.205), TripCurrencyRate(trip, "USD", 32.4)),
        )
        val after = runBlocking { db.tripDao().getMembers(trip) }.associate { it.name to it.id }
        assertEquals(setOf("我", "小美", "阿姨", "表哥"), after.keys)
        listOf("我", "小美", "阿姨").forEach { assertEquals("$it keeps its id", before[it], after[it]) }
        assertEquals(listOf("JPY" to 0.205, "USD" to 32.4), runBlocking { db.expenseDao().getRates(trip) }.map { it.currency to it.rate })
        assertEquals("東京吃吃喝喝", runBlocking { db.tripDao().observeTrip(trip).first() }!!.name)
        assertEquals("SAKURA", runBlocking { db.tripDao().observeSummaries().first() }.single().coverTheme)
    }

    @Test fun deletingATripRemovesEverythingInIt() {
        val trip = createTrip()
        val other = createTrip(name = "大阪", start = d(12, 20), end = d(12, 26))
        record(trip, "一蘭拉麵", "1320", "吃", "信用卡", d(6, 12))
        record(other, "章魚燒", "600", "吃", "現金", d(12, 21))
        runBlocking {
            db.photoDao().insert(Photo(expenseId = rows(trip).single().id, type = PhotoType.RECEIPT, path = "/x/a.jpg"))
            db.planDao().insert(PlanItem(tripId = trip, title = "淺草寺"))
        }
        TripDetailViewModel(db, trip).delete()
        assertTrue(rows(trip).isEmpty())
        assertTrue(runBlocking { db.planDao().observeRows(trip).first() }.isEmpty())
        assertTrue(runBlocking { db.tripDao().getMembers(trip) }.isEmpty())
        assertTrue(runBlocking { db.expenseDao().getRates(trip) }.isEmpty())
        assertTrue(runBlocking { db.photoDao().pathsForTrip(trip) }.isEmpty())
        // The other trip is untouched.
        assertEquals(listOf("大阪"), runBlocking { db.tripDao().observeSummaries().first() }.map { it.name })
        assertEquals(1, rows(other).size)
    }

    @Test fun appOpensTheRightTrip() {
        val past = Trip(1, "大阪", d(1, 1), d(1, 5))
        val ongoing = Trip(2, "東京", d(6, 10), d(6, 15))
        val soon = Trip(3, "沖繩", d(7, 20), d(7, 24))
        val trips = listOf(past, ongoing, soon)
        assertEquals("東京", currentTrip(trips, LocalDate.of(2024, 6, 12))?.name)
        assertEquals("東京", currentTrip(trips, LocalDate.of(2024, 6, 15))?.name)
        // Before Tokyo starts both Tokyo and Okinawa are "soon"; the earlier one wins.
        assertEquals("東京", currentTrip(trips, LocalDate.of(2024, 6, 1))?.name)
        assertEquals("沖繩", currentTrip(trips, LocalDate.of(2024, 6, 25))?.name)
        assertNull(currentTrip(trips, LocalDate.of(2024, 3, 1)))
        assertNull(currentTrip(trips, LocalDate.of(2024, 8, 1)))
    }
}
