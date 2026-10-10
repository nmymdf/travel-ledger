package com.archiekuo.travelledger.backup

import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Expense
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanPhoto
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.UNSCHEDULED_DAY
import com.archiekuo.travelledger.data.testDb
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
 * The phone and the desktop planner (desktop/) exchange files:
 *  - the phone's trip file opens on the desktop (written here as a fixture for desktop/test/e2e.mjs),
 *  - the desktop's "plans" file merges into the phone's trip (plans updated/added/deleted, day notes, pictures).
 */
@RunWith(RobolectricTestRunner::class)
class DesktopExchangeTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var files: File

    @Before fun setUp() {
        db = testDb()
        files = tmp.newFolder("files")
    }

    @After fun tearDown() = db.close()

    private fun cat(name: String) = runBlocking { db.lookupDao().getCategories().first { it.name == name }.id }

    /** The same trip every time (fixed identities), so desktop fixtures made from it line up. */
    private fun seoul(): Long = runBlocking {
        val trip = db.tripDao().insertTrip(Trip(name = "首爾賞楓", startDate = 20748, endDate = 20753, uuid = "trip-seoul"))
        val palace = db.planDao().insert(PlanItem(tripId = trip, title = "景福宮 韓服體驗", categoryId = cat("景點"), date = 20749, minuteOfDay = 9 * 60, uuid = "plan-palace", note = "韓服要先預約 https://hanbok.example.com"))
        db.planDao().insert(PlanItem(tripId = trip, title = "廣藏市場", categoryId = cat("吃"), date = 20750, uuid = "plan-market"))
        db.planDao().insert(PlanItem(tripId = trip, title = "Olive Young", categoryId = cat("購物"), uuid = "plan-oy"))
        val pic = File(files, "photos/plans").apply { mkdirs() }.resolve("menu.jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, -32, 1, 2, 3)) }
        db.planPhotoDao().insert(PlanPhoto(planItemId = palace, path = pic.absolutePath, width = 800, height = 600))
        db.dayNoteDao().set(trip, 20749, "早點出門")
        db.dayNoteDao().set(trip, UNSCHEDULED_DAY, "有空再去")
        db.expenseDao().insert(Expense(tripId = trip, date = 20748, amount = 1200.0, currency = "TWD", rate = 1.0, homeAmount = 1200.0, categoryId = null, paymentMethodId = null, payerId = null, title = "機場捷運"))
        trip
    }

    /** Writes the phone's file for the desktop end-to-end test (desktop/test/e2e.mjs reads it). */
    @Test fun phoneFileForTheDesktop() = runBlocking {
        val trip = seoul()
        val out = File("build/desktop-fixtures").apply { mkdirs() }.resolve("phone-trip.zip")
        out.outputStream().use { TripArchive.export(db, it, TripArchive.KIND_TRIP, listOf(trip), false, "小明") }
        val s = TripArchive.readSummary(out)
        assertEquals("首爾賞楓", s.trips.single().name)
        assertEquals(1, s.photoCount) // plan pictures always travel, even without "包含照片"
    }

    private fun plansFile(trip: JSONObject, plans: JSONArray, deleted: List<String> = emptyList(), dayNotes: JSONArray = JSONArray(), pictures: Map<String, ByteArray> = emptyMap()): File {
        val f = tmp.newFile()
        ZipOutputStream(f.outputStream()).use { zip ->
            fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            put("manifest.json", JSONObject().put("format", "travelledger").put("version", 1).put("kind", "plans").put("exportedAt", 1L)
                .put("trips", JSONArray().put(JSONObject().put("uuid", trip.getString("uuid")).put("name", trip.getString("name")).put("startDate", trip.getLong("startDate")).put("endDate", trip.getLong("endDate")))).toString().toByteArray())
            put("data.json", JSONObject().put("trip", trip).put("plans", plans).put("deleted", JSONArray(deleted)).put("dayNotes", dayNotes)
                .put("categories", JSONArray().put(JSONObject().put("name", "吃")).put(JSONObject().put("name", "交通"))).toString().toByteArray())
            pictures.forEach { (name, bytes) -> put(name, bytes) }
        }
        return f
    }

    @Test fun desktopPlansMergeIntoTheSameTrip() = runBlocking {
        val trip = seoul()
        val file = plansFile(
            JSONObject().put("uuid", "trip-seoul").put("name", "首爾賞楓").put("startDate", 20748).put("endDate", 20753),
            JSONArray()
                // moved to another day and given a time on the desktop
                .put(JSONObject().put("uuid", "plan-market").put("title", "廣藏市場 綠豆煎餅").put("category", "吃").put("date", 20751).put("minuteOfDay", 12 * 60)
                    .put("photos", JSONArray().put(JSONObject().put("file", "files/plans/0-a.jpg").put("width", 10).put("height", 10))))
                // new on the desktop
                .put(JSONObject().put("uuid", "plan-new").put("title", "搭乘 01A 循環公車").put("category", "交通").put("date", 20751).put("minuteOfDay", 11 * 60 + 40)),
            deleted = listOf("plan-oy"),
            dayNotes = JSONArray().put(JSONObject().put("day", 20751).put("text", "【第四天】\n市場 → 公車")).put(JSONObject().put("day", 20749).put("text", "")),
            pictures = mapOf("files/plans/0-a.jpg" to byteArrayOf(1, 2, 3)),
        )
        val preview = TripArchive.previewPlans(db, file)
        assertEquals(Triple(1, 1, 1), Triple(preview.added, preview.updated, preview.deleted))
        assertEquals(false, preview.newTrip)

        val result = TripArchive.importPlans(db, file, files) as TripArchive.Result.Imported
        assertEquals(listOf(trip), result.tripIds)
        val plans = db.planDao().forTrip(trip).associateBy { it.uuid }
        assertEquals(setOf("plan-palace", "plan-market", "plan-new"), plans.keys)
        // Untouched on the desktop → unchanged here, pictures included.
        assertEquals("景福宮 韓服體驗", plans.getValue("plan-palace").title)
        assertEquals(1, db.planPhotoDao().forPlan(plans.getValue("plan-palace").id).size)
        val market = plans.getValue("plan-market")
        assertEquals(Triple("廣藏市場 綠豆煎餅", 20751L, 12 * 60), Triple(market.title, market.date, market.minuteOfDay))
        assertEquals(byteArrayOf(1, 2, 3).toList(), File(db.planPhotoDao().forPlan(market.id).single().path).readBytes().toList())
        assertEquals(cat("交通"), plans.getValue("plan-new").categoryId)
        // Day notes: set, and cleared where the desktop cleared them.
        assertEquals("【第四天】\n市場 → 公車", db.dayNoteDao().get(trip, 20751)!!.text)
        assertNull(db.dayNoteDao().get(trip, 20749))
        // The ledger is never touched.
        assertEquals(1, db.expenseDao().forTrip(trip).size)
    }

    @Test fun aTripMadeOnTheDesktopIsCreated() = runBlocking {
        val file = plansFile(
            JSONObject().put("uuid", "trip-new").put("name", "大阪").put("startDate", 20800).put("endDate", 20803),
            JSONArray().put(JSONObject().put("uuid", "p1").put("title", "環球影城").put("date", 20801)),
        )
        assertTrue(TripArchive.previewPlans(db, file).newTrip)
        val id = (TripArchive.importPlans(db, file, files) as TripArchive.Result.Imported).tripIds.single()
        val t = db.tripDao().getTrip(id)!!
        assertEquals(Triple("大阪", 20800L, 20803L), Triple(t.name, t.startDate, t.endDate))
        assertNull(t.sharedBy)
        assertEquals(listOf("環球影城"), db.planDao().forTrip(id).map { it.title })
    }

    @Test fun someoneElsesTripIsRefused() = runBlocking {
        db.tripDao().insertTrip(Trip(name = "沖繩", startDate = 1, endDate = 2, uuid = "trip-shared", sharedBy = "阿姨"))
        val file = plansFile(JSONObject().put("uuid", "trip-shared").put("name", "沖繩").put("startDate", 1).put("endDate", 2), JSONArray())
        assertTrue(TripArchive.previewPlans(db, file).refused!!.contains("阿姨"))
        assertTrue(TripArchive.importPlans(db, file, files) is TripArchive.Result.Failed)
    }

    /** A file the real desktop page wrote (desktop/test/e2e.mjs) after opening [phoneFileForTheDesktop]'s file. */
    @Test fun realDesktopFileMerges() = runBlocking {
        val fixture = File("src/test/resources/desktop/roundtrip.zip")
        assumeTrue("run desktop/test/e2e.mjs to make the fixture", fixture.exists())
        val trip = seoul()
        val result = TripArchive.importPlans(db, fixture, files) as TripArchive.Result.Imported
        assertEquals(listOf(trip), result.tripIds)
        val plans = db.planDao().forTrip(trip).associateBy { it.title }
        // What e2e.mjs did on the desktop: moved 廣藏市場 to Day 4, deleted Olive Young, added two from pasted text,
        // attached a screenshot to 景福宮, wrote a day note for Day 3.
        assertEquals(20751L, plans.getValue("廣藏市場").date)
        assertTrue("Olive Young" !in plans)
        assertEquals(cat("交通"), plans.getValue("搭乘 01A 循環公車到南大門市場站").categoryId)
        assertEquals(11 * 60 + 40, plans.getValue("搭乘 01A 循環公車到南大門市場站").minuteOfDay)
        assertEquals(cat("吃"), plans.getValue("明洞餃子").categoryId)
        assertEquals(2, db.planPhotoDao().forPlan(plans.getValue("景福宮 韓服體驗").id).size)
        assertEquals("先換錢再出門", db.dayNoteDao().get(trip, 20750)!!.text)
        assertEquals("有空再去", db.dayNoteDao().get(trip, UNSCHEDULED_DAY)!!.text)
        assertEquals(1, db.expenseDao().forTrip(trip).size)
    }
}
