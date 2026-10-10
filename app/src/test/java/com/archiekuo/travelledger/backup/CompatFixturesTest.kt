package com.archiekuo.travelledger.backup

import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Expense
import com.archiekuo.travelledger.data.Photo
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanPhoto
import com.archiekuo.travelledger.data.Reservation
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import com.archiekuo.travelledger.data.UNSCHEDULED_DAY
import com.archiekuo.travelledger.data.testDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Files for another app that reads and writes the same format (the iPhone version, see docs/IOS_PORT.md):
 *  - compat/fixtures/{trip,backup,additions}.zip are written by this phone. Run with WRITE_COMPAT_FIXTURES=1 to
 *    remake them; otherwise this test checks that the committed ones still import here.
 *  - compat/from-ios/…zip are files the other app wrote; each one must import here.
 */
@RunWith(RobolectricTestRunner::class)
class CompatFixturesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var organizer: AppDatabase
    private lateinit var companion: AppDatabase
    private lateinit var files: File
    private val fixtures = File("../compat/fixtures")

    @Before fun setUp() {
        organizer = testDb()
        companion = testDb()
        files = tmp.newFolder("files")
    }

    @After fun tearDown() {
        organizer.close()
        companion.close()
    }

    private fun cat(db: AppDatabase, name: String) = runBlocking { db.lookupDao().getCategories().first { it.name == name }.id }

    /** A real JPEG (app/src/test/resources/compat), so the other app can decode the pictures. */
    private fun jpeg(name: String): File = File(files, name).also { it.writeBytes(File("src/test/resources/compat/$name").readBytes()) }

    /** The organizer's trip: members, a rate, plans on days and in 待排, day notes, expenses in two currencies, pictures. */
    private fun seoul(): Long = runBlocking {
        val db = organizer
        val trip = db.tripDao().createTrip(
            Trip(name = "首爾賞楓", startDate = 20748, endDate = 20753, splitEnabled = true, budget = 40000.0, uuid = "trip-seoul", navApp = "naver", createdAt = 1_790_000_000_000),
            listOf("我", "小美"), listOf(TripCurrencyRate(0, "KRW", 0.024, 1_790_000_000_000, "manual")),
        )
        val palace = db.planDao().insert(
            PlanItem(tripId = trip, title = "景福宮 韓服體驗", categoryId = cat(db, "景點"), date = 20749, minuteOfDay = 9 * 60, uuid = "plan-palace",
                reservation = Reservation.BOOKED, reservationNote = "10:00 兩位 #A123", location = "https://maps.app.goo.gl/palace",
                note = "韓服要先預約 https://hanbok.example.com 電話 02-1234-5678", createdAt = 1_790_000_000_001),
        )
        val market = db.planDao().insert(PlanItem(tripId = trip, title = "廣藏市場", categoryId = cat(db, "吃"), date = 20750, uuid = "plan-market", createdAt = 1_790_000_000_002))
        db.planDao().insert(PlanItem(tripId = trip, title = "Olive Young", categoryId = cat(db, "購物"), uuid = "plan-oy", createdAt = 1_790_000_000_003))
        db.planPhotoDao().insert(PlanPhoto(planItemId = palace, path = jpeg("menu.jpg").absolutePath, width = 64, height = 48, createdAt = 1_790_000_000_004))
        db.dayNoteDao().set(trip, 20749, "早點出門")
        db.dayNoteDao().set(trip, UNSCHEDULED_DAY, "有空再去")
        val members = db.tripDao().getMembers(trip)
        db.expenseDao().insert(
            Expense(tripId = trip, date = 20748, minuteOfDay = 15 * 60 + 30, amount = 1200.0, currency = "TWD", rate = 1.0, homeAmount = 1200.0,
                categoryId = cat(db, "交通"), paymentMethodId = db.lookupDao().getPaymentMethods().first { it.name == "信用卡" }.id,
                payerId = members[0].id, title = "機場捷運", uuid = "exp-metro", createdAt = 1_790_000_000_005),
        )
        val pancake = db.expenseDao().insert(
            Expense(tripId = trip, date = 20750, amount = 12000.0, currency = "KRW", rate = 0.024, homeAmount = 288.0,
                categoryId = cat(db, "吃"), paymentMethodId = db.lookupDao().getPaymentMethods().first { it.name == "現金" }.id,
                payerId = members[1].id, title = "綠豆煎餅", note = "很好吃", planItemId = market, uuid = "exp-pancake", createdAt = 1_790_000_000_006),
        )
        db.photoDao().insert(Photo(expenseId = pancake, type = PhotoType.RECEIPT, path = jpeg("receipt.jpg").absolutePath, width = 64, height = 48, createdAt = 1_790_000_000_007))
        trip
    }

    @Test fun fixtures() = runBlocking {
        val write = System.getenv("WRITE_COMPAT_FIXTURES") == "1"
        if (!write) assumeTrue("no compat fixtures yet", File(fixtures, "trip.zip").exists())
        if (write) {
            fixtures.mkdirs()
            val trip = seoul()
            // trip.zip: shared with companions (read-only copy on their phone), photos included.
            File(fixtures, "trip.zip").outputStream().use { TripArchive.export(organizer, it, TripArchive.KIND_TRIP, listOf(trip), true, "小明", "0.10.0", 1_790_000_100_000) }
            // additions.zip: the companion's suggestions for that trip.
            val copy = (TripArchive.import(companion, File(fixtures, "trip.zip"), tmp.newFolder("c")) as TripArchive.Result.Imported).tripIds.single()
            val market = companion.planDao().forTrip(copy).first { it.uuid == "plan-market" }
            companion.planDao().insert(PlanItem(tripId = copy, title = "明洞餃子", categoryId = cat(companion, "吃"), date = 20751, minuteOfDay = 12 * 60, uuid = "plan-add-dumpling", pending = true, createdAt = 1_790_000_200_000))
            companion.expenseDao().insert(
                Expense(tripId = copy, date = 20750, amount = 5000.0, currency = "KRW", rate = 0.024, homeAmount = 120.0, categoryId = cat(companion, "吃"),
                    paymentMethodId = null, payerId = null, title = "麻藥飯捲", planItemId = market.id, uuid = "exp-add-gimbap", pending = true, createdAt = 1_790_000_200_001),
            )
            File(fixtures, "additions.zip").outputStream().use { TripArchive.exportAdditions(companion, it, copy, "小美", false, "0.10.0", 1_790_000_300_000) }
            // backup.zip: everything on the organizer's phone, including a read-only trip someone shared with them.
            organizer.tripDao().insertTrip(Trip(name = "沖繩", startDate = 20800, endDate = 20803, uuid = "trip-okinawa", sharedBy = "阿姨", sharedAt = 1_790_000_000_000))
            val okinawa = organizer.tripDao().findByUuid("trip-okinawa")!!.id
            organizer.planDao().insert(PlanItem(tripId = okinawa, title = "美麗海水族館", uuid = "plan-aquarium", date = 20801, createdAt = 1_790_000_400_000))
            organizer.planDao().insert(PlanItem(tripId = okinawa, title = "古宇利島", uuid = "plan-kouri", pending = true, createdAt = 1_790_000_400_001))
            File(fixtures, "backup.zip").outputStream().use {
                TripArchive.export(organizer, it, TripArchive.KIND_BACKUP, listOf(trip, okinawa), true, null, "0.10.0", 1_790_000_500_000)
            }
        }

        // Whatever is committed imports on a fresh phone.
        val phone = testDb()
        try {
            val shared = TripArchive.import(phone, File(fixtures, "trip.zip"), tmp.newFolder("p1")) as TripArchive.Result.Imported
            val t = phone.tripDao().getTrip(shared.tripIds.single())!!
            assertEquals("小明", t.sharedBy)
            assertEquals(2, phone.expenseDao().forTrip(t.id).size)
            assertEquals("有空再去", phone.dayNoteDao().get(t.id, UNSCHEDULED_DAY)!!.text)

            val other = testDb()
            val backup = TripArchive.import(other, File(fixtures, "backup.zip"), tmp.newFolder("p2")) as TripArchive.Result.Imported
            assertEquals(2, backup.tripIds.size)
            val ok = other.tripDao().findByUuid("trip-okinawa")!!
            assertEquals("阿姨", ok.sharedBy)
            assertTrue(other.planDao().forTrip(ok.id).single { it.uuid == "plan-kouri" }.pending)
            val seoul = other.tripDao().findByUuid("trip-seoul")!!
            assertEquals(null, seoul.sharedBy)
            val added = TripArchive.importAdditions(other, File(fixtures, "additions.zip"), tmp.newFolder("p3"), setOf("plan-add-dumpling", "exp-add-gimbap"))
            assertEquals(2, (added as TripArchive.Result.Imported).updated)
            other.close()
        } finally {
            phone.close()
        }
    }

    /** Files the iPhone version wrote: each must import (trip/backup) or merge (plans/additions) on this phone. */
    @Test fun filesFromTheIphone() = runBlocking {
        val dir = File("../compat/from-ios")
        val zips = dir.listFiles { f -> f.name.endsWith(".zip") }.orEmpty().sortedBy { it.name }
        assumeTrue("no files from the iPhone yet", zips.isNotEmpty())
        for (zip in zips) {
            val db = testDb()
            try {
                val summary = TripArchive.readSummary(zip)
                val result = when (summary.kind) {
                    TripArchive.KIND_PLANS -> TripArchive.importPlans(db, zip, tmp.newFolder())
                    TripArchive.KIND_ADDITIONS -> {
                        // The organizer's trip must exist first: the iPhone made these additions on trip.zip's trip.
                        TripArchive.import(db, File(fixtures, "backup.zip"), tmp.newFolder())
                        val items = TripArchive.readAdditions(zip).items.map { it.uuid }.toSet()
                        TripArchive.importAdditions(db, zip, tmp.newFolder(), items)
                    }
                    else -> TripArchive.import(db, zip, tmp.newFolder())
                }
                assertTrue("${zip.name}: $result", result is TripArchive.Result.Imported)
            } finally {
                db.close()
            }
        }
    }
}
