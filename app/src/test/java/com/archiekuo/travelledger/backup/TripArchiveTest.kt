package com.archiekuo.travelledger.backup

import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.Expense
import com.archiekuo.travelledger.data.Photo
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.Reservation
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import com.archiekuo.travelledger.data.testDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Two phones: the organizer keeps the books and shares the trip; a companion imports read-only copies.
 * A third, empty phone restores a full backup.
 */
@RunWith(RobolectricTestRunner::class)
class TripArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var organizer: AppDatabase
    private lateinit var companion: AppDatabase
    private lateinit var organizerFiles: File
    private lateinit var companionFiles: File

    @Before fun setUp() {
        organizer = testDb()
        companion = testDb()
        organizerFiles = tmp.newFolder("organizer")
        companionFiles = tmp.newFolder("companion")
    }

    @After fun tearDown() {
        organizer.close()
        companion.close()
    }

    private fun catId(db: AppDatabase, name: String) = runBlocking { db.lookupDao().getCategories().first { it.name == name }.id }
    private fun payId(db: AppDatabase, name: String) = runBlocking { db.lookupDao().getPaymentMethods().first { it.name == name }.id }

    private fun photoFile(name: String, bytes: String): String =
        File(organizerFiles, "photos/receipts").apply { mkdirs() }.resolve(name).apply { writeText(bytes) }.absolutePath

    /** A Kyoto trip with a custom category, a booked dinner and expenses in yen and TWD. */
    private fun kyoto(): Long = runBlocking {
        val onsen = organizer.lookupDao().insertCategory(Category(name = "溫泉", sortOrder = 9, icon = "spa", color = 4))
        val trip = organizer.tripDao().createTrip(
            Trip(name = "京都賞楓", startDate = 20400, endDate = 20404, budget = 50000.0, coverTheme = "AUTUMN", navApp = "google"),
            listOf("我", "小美"), listOf(TripCurrencyRate(0, "JPY", 0.215)),
        )
        val dinner = organizer.planDao().insert(
            PlanItem(tripId = trip, title = "瓢亭 早餐", categoryId = catId(organizer, "吃"), date = 20401, minuteOfDay = 8 * 60,
                reservation = Reservation.BOOKED, reservationNote = "08:00 · 2 位", location = "京都市左京區南禪寺草川町35", estCost = 2400.0),
        )
        organizer.planDao().insert(PlanItem(tripId = trip, title = "嵐山小火車", location = "嵯峨野"))
        organizer.dayNoteDao().set(trip, 20401, "【第二天行程】\n08:00 瓢亭")
        val members = organizer.tripDao().getMembers(trip)
        val e1 = organizer.expenseDao().insert(
            Expense(tripId = trip, date = 20401, amount = 11000.0, currency = "JPY", rate = 0.215, homeAmount = 2365.0,
                categoryId = catId(organizer, "吃"), paymentMethodId = payId(organizer, "信用卡"), payerId = members[1].id,
                title = "瓢亭", minuteOfDay = 8 * 60 + 40, planItemId = dinner, ocrText = "瓢亭\n合計 ¥11,000"),
        )
        organizer.photoDao().insert(Photo(expenseId = e1, type = PhotoType.RECEIPT, path = photoFile("r1.jpg", "receipt-bytes"), width = 900, height = 1600))
        organizer.expenseDao().insert(
            Expense(tripId = trip, date = 20402, amount = 3000.0, currency = "JPY", rate = 0.215, homeAmount = 645.0,
                categoryId = onsen, paymentMethodId = payId(organizer, "現金"), payerId = null, title = "船岡溫泉"),
        )
        organizer.expenseDao().insert(
            Expense(tripId = trip, date = 20400, amount = 1200.0, currency = "TWD", rate = 1.0, homeAmount = 1200.0,
                categoryId = catId(organizer, "交通"), paymentMethodId = null, payerId = null, title = "桃園機場捷運", note = "兩人"),
        )
        trip
    }

    private fun share(db: AppDatabase, tripId: Long, photos: Boolean = false, by: String = "小明", now: Long = 1_000L): File = runBlocking {
        val f = tmp.newFile()
        f.outputStream().use { TripArchive.export(db, it, TripArchive.KIND_TRIP, listOf(tripId), photos, by, "test", now) }
        f
    }

    private fun backup(db: AppDatabase, photos: Boolean): File = runBlocking {
        val f = tmp.newFile()
        f.outputStream().use { TripArchive.export(db, it, TripArchive.KIND_BACKUP, db.tripDao().allIds(), photos, null, "test") }
        f
    }

    private fun import(db: AppDatabase, file: File, dir: File) = runBlocking { TripArchive.import(db, file, dir) }

    @Test fun summaryDescribesTheFileBeforeImporting() {
        val trip = kyoto()
        val s = TripArchive.readSummary(share(organizer, trip, photos = true))
        assertEquals(TripArchive.KIND_TRIP, s.kind)
        assertEquals("小明", s.sharedBy)
        assertEquals(1, s.photoCount)
        val t = s.trips.single()
        assertEquals("京都賞楓", t.name)
        assertEquals(3, t.expenseCount)
        assertEquals(2365.0 + 645.0 + 1200.0, t.totalHome, 0.001)
    }

    @Test fun companionGetsAReadOnlyCopyWithEverything() = runBlocking {
        val trip = kyoto()
        val result = import(companion, share(organizer, trip), companionFiles)
        assertTrue(result is TripArchive.Result.Imported)
        val id = (result as TripArchive.Result.Imported).tripIds.single()

        val copy = companion.tripDao().getTrip(id)!!
        assertEquals("京都賞楓", copy.name)
        assertEquals("小明", copy.sharedBy)
        assertTrue(copy.readOnly)
        assertEquals(organizer.tripDao().getTrip(trip)!!.uuid, copy.uuid)
        assertEquals(50000.0, copy.budget!!, 0.0)
        assertEquals("AUTUMN", copy.coverTheme)
        assertEquals("google", copy.navApp)
        assertEquals(listOf("我", "小美"), companion.tripDao().getMembers(id).map { it.name })
        assertEquals(0.215, companion.tripDao().getRates(id).single().rate, 0.0)
        assertEquals("【第二天行程】\n08:00 瓢亭", companion.dayNoteDao().get(id, 20401)!!.text)

        val rows = companion.expenseDao().observeRows(id).first()
        assertEquals(3, rows.size)
        assertEquals(4210.0, rows.sumOf { it.homeAmount }, 0.001)
        // The custom category travelled along, with its look.
        val onsen = rows.first { it.title == "船岡溫泉" }
        assertEquals(Triple("溫泉", "spa", 4), Triple(onsen.categoryName, onsen.categoryIcon, onsen.categoryColor))
        assertEquals("信用卡", rows.first { it.title == "瓢亭" }.paymentMethodName)
        // Without photos, no photo rows and no files.
        assertNull(rows.first { it.title == "瓢亭" }.thumbPath)

        // The expense still points at its plan item, and the payer at the right member.
        val plans = companion.planDao().observeRows(id).first()
        val breakfast = plans.first { it.title == "瓢亭 早餐" }
        assertEquals(2365.0, breakfast.spent, 0.001)
        assertEquals("08:00 · 2 位", breakfast.reservationNote)
        val e = companion.expenseDao().forTrip(id).first { it.title == "瓢亭" }
        assertEquals(breakfast.id, e.planItemId)
        assertEquals("小美", companion.tripDao().getMembers(id).first { it.id == e.payerId }.name)
        assertEquals(8 * 60 + 40, e.minuteOfDay)
        assertEquals("瓢亭\n合計 ¥11,000", e.ocrText)
    }

    @Test fun sharingAgainUpdatesTheSameCopy() = runBlocking {
        val trip = kyoto()
        val first = (import(companion, share(organizer, trip, now = 1_000), companionFiles) as TripArchive.Result.Imported).tripIds.single()

        // The organizer keeps recording, then shares again.
        organizer.expenseDao().insert(
            Expense(tripId = trip, date = 20403, amount = 500.0, currency = "JPY", rate = 0.215, homeAmount = 107.5,
                categoryId = null, paymentMethodId = null, payerId = null, title = "抹茶冰"),
        )
        val again = import(companion, share(organizer, trip, now = 2_000), companionFiles) as TripArchive.Result.Imported

        assertEquals(listOf(first), again.tripIds)
        assertEquals(1, again.updated)
        assertEquals(1, companion.tripDao().getTrips().size)
        assertEquals(4, companion.expenseDao().forTrip(first).size)
        assertEquals(2, companion.planDao().forTrip(first).size)
        assertEquals(2, companion.tripDao().getMembers(first).size)
        assertEquals(2_000L, companion.tripDao().getTrip(first)!!.sharedAt)
        // Categories are not duplicated by the second import.
        assertEquals(1, companion.lookupDao().getCategories().count { it.name == "溫泉" })
    }

    @Test fun ownTripComingBackIsRefused() = runBlocking {
        val trip = kyoto()
        val result = import(organizer, share(organizer, trip), organizerFiles)
        assertTrue(result is TripArchive.Result.Failed)
        assertEquals(1, organizer.tripDao().getTrips().size)
        assertNull(organizer.tripDao().getTrip(trip)!!.sharedBy)
        assertEquals(3, organizer.expenseDao().forTrip(trip).size)
    }

    @Test fun photosAreCopiedWhenIncludedAndReplacedOnUpdate() = runBlocking {
        val trip = kyoto()
        val id = (import(companion, share(organizer, trip, photos = true), companionFiles) as TripArchive.Result.Imported).tripIds.single()
        val path = companion.photoDao().pathsForTrip(id).single()
        assertTrue(path.startsWith(companionFiles.absolutePath))
        assertEquals("receipt-bytes", File(path).readText())

        import(companion, share(organizer, trip, photos = true), companionFiles)
        val newPath = companion.photoDao().pathsForTrip(id).single()
        assertNotEquals(path, newPath)
        assertFalse("old copy cleaned up", File(path).exists())
        assertTrue(File(newPath).exists())
    }

    @Test fun backupRestoresEverythingOnANewPhoneWithoutDuplicates() = runBlocking {
        val trip = kyoto()
        // The organizer also holds a trip someone else shared.
        val other = testDb()
        val otherTrip = other.tripDao().createTrip(Trip(name = "沖繩", startDate = 20500, endDate = 20503), emptyList(), emptyList())
        import(organizer, share(other, otherTrip, by = "阿姨"), organizerFiles)
        other.close()

        val file = backup(organizer, photos = true)
        val summary = TripArchive.readSummary(file)
        assertEquals(TripArchive.KIND_BACKUP, summary.kind)
        assertEquals(2, summary.trips.size)

        val fresh = testDb()
        val freshFiles = tmp.newFolder("fresh")
        val result = import(fresh, file, freshFiles) as TripArchive.Result.Imported
        assertEquals(2, result.tripIds.size)
        assertEquals(0, result.updated)
        val restored = fresh.tripDao().getTrips().associateBy { it.name }
        assertNull("own trip stays editable", restored.getValue("京都賞楓").sharedBy)
        assertEquals("阿姨", restored.getValue("沖繩").sharedBy)
        val kyotoId = restored.getValue("京都賞楓").id
        assertEquals(3, fresh.expenseDao().forTrip(kyotoId).size)
        assertTrue(File(fresh.photoDao().pathsForTrip(kyotoId).single()).exists())
        assertEquals(organizer.tripDao().getTrip(trip)!!.uuid, restored.getValue("京都賞楓").uuid)

        // Restoring the same backup twice changes nothing.
        val again = import(fresh, file, freshFiles) as TripArchive.Result.Imported
        assertEquals(2, again.updated)
        assertEquals(2, fresh.tripDao().getTrips().size)
        assertEquals(3, fresh.expenseDao().forTrip(kyotoId).size)
        assertEquals(1, fresh.lookupDao().getCategories().count { it.name == "溫泉" })
        fresh.close()
    }

    @Test fun backupWithoutPhotosKeepsTheBooks() = runBlocking {
        kyoto()
        val file = backup(organizer, photos = false)
        assertEquals(0, TripArchive.readSummary(file).photoCount)
        val fresh = testDb()
        import(fresh, file, tmp.newFolder("fresh"))
        val t = fresh.tripDao().getTrips().single()
        assertEquals(3, fresh.expenseDao().forTrip(t.id).size)
        assertTrue(fresh.photoDao().pathsForTrip(t.id).isEmpty())
        fresh.close()
    }

    @Test fun otherFilesAreRejected() = runBlocking {
        val notZip = tmp.newFile().apply { writeText("hello") }
        assertTrue(runCatching { TripArchive.readSummary(notZip) }.exceptionOrNull() is TripArchive.BadArchive)
        assertTrue(import(companion, notZip, companionFiles) is TripArchive.Result.Failed)

        val otherZip = tmp.newFile()
        ZipOutputStream(otherZip.outputStream()).use { it.putNextEntry(ZipEntry("readme.txt")); it.write(1); it.closeEntry() }
        assertTrue(runCatching { TripArchive.readSummary(otherZip) }.exceptionOrNull() is TripArchive.BadArchive)
        assertTrue(import(companion, otherZip, companionFiles) is TripArchive.Result.Failed)
        assertTrue(companion.tripDao().getTrips().isEmpty())
    }
}
