package com.archiekuo.travelledger.backup

import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Expense
import com.archiekuo.travelledger.data.Photo
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import com.archiekuo.travelledger.data.testDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The organizer shares a trip; a companion adds an expense and a place on the read-only copy and sends them back;
 * the organizer picks what to take in and shares again; the companion's copy settles.
 */
@RunWith(RobolectricTestRunner::class)
class AdditionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var organizer: AppDatabase
    private lateinit var companion: AppDatabase
    private lateinit var organizerFiles: File
    private lateinit var companionFiles: File
    private var trip = 0L
    private var copy = 0L
    private var shareTime = 1_000L

    @Before fun setUp() = runBlocking {
        organizer = testDb()
        companion = testDb()
        organizerFiles = tmp.newFolder("organizer")
        companionFiles = tmp.newFolder("companion")
        trip = organizer.tripDao().createTrip(
            Trip(name = "首爾賞楓", startDate = 20748, endDate = 20753), listOf("我", "小美"), listOf(TripCurrencyRate(0, "KRW", 0.024)),
        )
        organizer.planDao().insert(PlanItem(tripId = trip, title = "景福宮", date = 20749))
        organizer.expenseDao().insert(
            Expense(tripId = trip, date = 20748, amount = 1200.0, currency = "TWD", rate = 1.0, homeAmount = 1200.0,
                categoryId = cat(organizer, "交通"), paymentMethodId = null, payerId = null, title = "機場捷運"),
        )
        copy = (TripArchive.import(companion, share(), companionFiles) as TripArchive.Result.Imported).tripIds.single()
    }

    @After fun tearDown() {
        organizer.close()
        companion.close()
    }

    private fun cat(db: AppDatabase, name: String) = runBlocking { db.lookupDao().getCategories().first { it.name == name }.id }

    private fun share(): File = runBlocking {
        shareTime += 1_000
        tmp.newFile().also { f -> f.outputStream().use { TripArchive.export(organizer, it, TripArchive.KIND_TRIP, listOf(trip), true, "小明", "test", shareTime) } }
    }

    private fun sendAdditions(photos: Boolean = false): File = runBlocking {
        tmp.newFile().also { f -> f.outputStream().use { TripArchive.exportAdditions(companion, it, copy, "小美", photos) } }
    }

    /** What a companion does on the shared copy: a new place, an expense for it, and one for the organizer's place. */
    private fun companionAdds(): Triple<String, String, String> = runBlocking {
        val gyeongbok = companion.planDao().forTrip(copy).first { it.title == "景福宮" }
        val newPlace = PlanItem(tripId = copy, title = "廣藏市場", date = 20750, categoryId = cat(companion, "吃"), pending = true)
        val placeId = companion.planDao().insert(newPlace)
        val pancake = Expense(tripId = copy, date = 20750, amount = 12000.0, currency = "KRW", rate = 0.024, homeAmount = 288.0,
            categoryId = cat(companion, "吃"), paymentMethodId = null, payerId = null, title = "綠豆煎餅", planItemId = placeId, pending = true)
        val pancakeId = companion.expenseDao().insert(pancake)
        val receipt = File(companionFiles, "photos/receipts").apply { mkdirs() }.resolve("p.jpg").apply { writeText("receipt") }
        companion.photoDao().insert(Photo(expenseId = pancakeId, type = PhotoType.RECEIPT, path = receipt.absolutePath))
        val hanbok = Expense(tripId = copy, date = 20749, amount = 20000.0, currency = "KRW", rate = 0.024, homeAmount = 480.0,
            categoryId = cat(companion, "景點"), paymentMethodId = null, payerId = null, title = "韓服租借", planItemId = gyeongbok.id, pending = true)
        companion.expenseDao().insert(hanbok)
        Triple(newPlace.uuid, pancake.uuid, hanbok.uuid)
    }

    @Test fun organizerReviewsAndTakesEverythingIn() = runBlocking {
        val (place, pancake, hanbok) = companionAdds()
        assertEquals(3, TripArchive.pendingCount(companion, copy))
        val file = sendAdditions(photos = true)

        val adds = TripArchive.readAdditions(file)
        assertEquals("小美", adds.from)
        assertEquals("首爾賞楓", adds.tripName)
        assertEquals(setOf(place, pancake, hanbok), adds.items.map { it.uuid }.toSet())

        val result = TripArchive.importAdditions(organizer, file, organizerFiles, adds.items.map { it.uuid }.toSet()) as TripArchive.Result.Imported
        assertEquals(3, result.updated)
        val expenses = organizer.expenseDao().forTrip(trip)
        val plans = organizer.planDao().forTrip(trip)
        assertEquals(3, expenses.size)
        assertEquals(2, plans.size)
        assertTrue("taken in as the organizer's own", expenses.none { it.pending } && plans.none { it.pending })
        assertEquals("小美", expenses.first { it.title == "綠豆煎餅" }.addedBy)
        assertEquals("小美", plans.first { it.title == "廣藏市場" }.addedBy)
        assertNull(expenses.first { it.title == "機場捷運" }.addedBy)
        // Links: the pancake to the new place, the hanbok to the organizer's own place.
        assertEquals(plans.first { it.title == "廣藏市場" }.id, expenses.first { it.title == "綠豆煎餅" }.planItemId)
        assertEquals(plans.first { it.title == "景福宮" }.id, expenses.first { it.title == "韓服租借" }.planItemId)
        // The receipt photo came along.
        val photo = organizer.photoDao().forExpense(expenses.first { it.title == "綠豆煎餅" }.id).single()
        assertEquals("receipt", File(photo.path).readText())

        // The same file opened again adds nothing.
        val again = TripArchive.importAdditions(organizer, file, organizerFiles, adds.items.map { it.uuid }.toSet()) as TripArchive.Result.Imported
        assertEquals(0, again.updated)
        assertEquals(3, organizer.expenseDao().forTrip(trip).size)
    }

    @Test fun reSharingSettlesTheCompanionsCopy() = runBlocking {
        val (place, pancake, hanbok) = companionAdds()
        val file = sendAdditions()
        // The organizer takes the place and the hanbok, not the pancake.
        TripArchive.importAdditions(organizer, file, organizerFiles, setOf(place, hanbok))

        // The organizer shares again; the companion opens it.
        val updated = TripArchive.import(companion, share(), companionFiles) as TripArchive.Result.Imported
        assertEquals(listOf(copy), updated.tripIds)

        val expenses = companion.expenseDao().forTrip(copy)
        val plans = companion.planDao().forTrip(copy)
        // No duplicates: accepted items now come once, as the organizer's.
        assertEquals(listOf("機場捷運", "綠豆煎餅", "韓服租借"), expenses.map { it.title }.sorted())
        assertEquals(listOf("廣藏市場", "景福宮"), plans.map { it.title }.sorted())
        assertFalse(expenses.first { it.uuid == hanbok }.pending)
        assertFalse(plans.first { it.uuid == place }.pending)
        // The pancake was not taken in: still mine, still pending, its photo kept, and linked to the (now official) market.
        val mine = expenses.first { it.uuid == pancake }
        assertTrue(mine.pending)
        assertEquals(plans.first { it.uuid == place }.id, mine.planItemId)
        assertTrue(File(companion.photoDao().forExpense(mine.id).single().path).exists())
        assertEquals(1, TripArchive.pendingCount(companion, copy))
    }

    @Test fun sharesLeaveMyAdditionsOutButBackupsKeepThem() = runBlocking {
        companionAdds()
        // A companion forwarding the trip passes on only the organizer's book.
        val forwarded = tmp.newFile().also { f -> f.outputStream().use { TripArchive.export(companion, it, TripArchive.KIND_TRIP, listOf(copy), false, "小美") } }
        assertEquals(1, TripArchive.readSummary(forwarded).trips.single().expenseCount)

        // A backup keeps them as my pending additions, once, even when restored twice.
        val backup = tmp.newFile().also { f -> f.outputStream().use { TripArchive.export(companion, it, TripArchive.KIND_BACKUP, listOf(copy), false) } }
        val fresh = testDb()
        val dir = tmp.newFolder("fresh")
        TripArchive.import(fresh, backup, dir)
        TripArchive.import(fresh, backup, dir)
        val t = fresh.tripDao().getTrips().single()
        assertEquals("小明", t.sharedBy)
        assertEquals(3, TripArchive.pendingCount(fresh, t.id))
        assertEquals(3, fresh.expenseDao().forTrip(t.id).size)
        fresh.close()
    }

    @Test fun additionsOnlyGoToTheOrganizer() = runBlocking {
        companionAdds()
        val file = sendAdditions()
        // Opened on the companion's own phone (a read-only copy) or a phone without the trip: refused.
        assertTrue(TripArchive.importAdditions(companion, file, companionFiles, setOf("x")) is TripArchive.Result.Failed)
        val stranger = testDb()
        assertTrue(TripArchive.importAdditions(stranger, file, tmp.newFolder("s"), setOf("x")) is TripArchive.Result.Failed)
        stranger.close()
        // And the normal import path does not take additions files.
        assertTrue(TripArchive.import(organizer, file, organizerFiles) is TripArchive.Result.Failed)
    }

    @Test fun restoreWarnsWhenThePhoneIsNewer() = runBlocking {
        val backup = tmp.newFile().also { f -> f.outputStream().use { TripArchive.export(organizer, it, TripArchive.KIND_BACKUP, listOf(trip), false, now = System.currentTimeMillis()) } }
        val summary = TripArchive.readSummary(backup)
        assertEquals(emptyList<String>(), TripArchive.newerOnPhone(organizer, summary))
        organizer.expenseDao().insert(
            Expense(tripId = trip, date = 20751, amount = 100.0, currency = "TWD", rate = 1.0, homeAmount = 100.0,
                categoryId = null, paymentMethodId = null, payerId = null, title = "之後記的", createdAt = summary.exportedAt + 60_000),
        )
        assertEquals(listOf("首爾賞楓"), TripArchive.newerOnPhone(organizer, summary))
    }
}
