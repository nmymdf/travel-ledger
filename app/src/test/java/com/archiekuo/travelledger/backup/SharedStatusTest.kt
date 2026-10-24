package com.archiekuo.travelledger.backup

import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanStatus
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.testDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the organizer ticked off (已去) or skipped shows the same on the companion's copy, also after sharing again. */
@RunWith(RobolectricTestRunner::class)
class SharedStatusTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun ticksTravelWithTheShare() = runBlocking {
        val organizer = testDb()
        val companion = testDb()
        val trip = organizer.tripDao().insertTrip(Trip(name = "首爾", startDate = 20748, endDate = 20753, uuid = "t"))
        val palace = organizer.planDao().insert(PlanItem(tripId = trip, title = "景福宮", date = 20749, uuid = "p1"))
        organizer.planDao().insert(PlanItem(tripId = trip, title = "廣藏市場", date = 20750, uuid = "p2"))
        fun share() = tmp.newFile().also { f -> f.outputStream().use { runBlocking { TripArchive.export(organizer, it, TripArchive.KIND_TRIP, listOf(trip), false, "小明") } } }
        fun statuses() = runBlocking { companion.planDao().forTrip(companion.tripDao().findByUuid("t")!!.id).associate { it.uuid to it.status } }

        organizer.planDao().setStatus(palace, PlanStatus.DONE)
        TripArchive.import(companion, share(), tmp.newFolder())
        assertEquals(mapOf("p1" to PlanStatus.DONE, "p2" to PlanStatus.TODO), statuses())

        // Ticked later: the companion sees it once the organizer shares again.
        organizer.planDao().setStatus(palace + 1, PlanStatus.SKIPPED)
        TripArchive.import(companion, share(), tmp.newFolder())
        assertEquals(mapOf("p1" to PlanStatus.DONE, "p2" to PlanStatus.SKIPPED), statuses())
        organizer.close(); companion.close()
    }
}
