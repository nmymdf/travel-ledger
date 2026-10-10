package com.archiekuo.travelledger.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Upgrading a phone that still has data from an older version must keep every record. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun v1DataSurvivesUpgradeToLatest() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO trip (id, name, startDate, endDate, homeCurrency, budget, splitEnabled, createdAt, archived) VALUES (1, '東京', 19884, 19889, 'TWD', 40000, 0, 0, 0)")
            execSQL("INSERT INTO member (id, tripId, name) VALUES (1, 1, '我')")
            execSQL("INSERT INTO category (id, name, sortOrder) VALUES (1, '吃', 0)")
            execSQL("INSERT INTO payment_method (id, name, sortOrder) VALUES (1, '信用卡', 0)")
            execSQL("INSERT INTO trip_currency_rate (tripId, currency, rate, updatedAt, source) VALUES (1, 'JPY', 0.21, 0, 'manual')")
            execSQL("INSERT INTO expense (id, tripId, date, amount, currency, rate, homeAmount, categoryId, paymentMethodId, payerId, note, createdAt) VALUES (1, 1, 19886, 1320, 'JPY', 0.21, 277.2, 1, 1, NULL, '一蘭拉麵', 0)")
            execSQL("INSERT INTO photo (id, expenseId, type, path, width, height, createdAt, savedToGallery) VALUES (1, 1, 'RECEIPT', '/x/r.jpg', 0, 0, 0, 0)")
            close()
        }
        // Validates each step's resulting schema against the exported schema files.
        helper.runMigrationsAndValidate(name, 7, true, *AppDatabase.ALL_MIGRATIONS).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            val row = db.expenseDao().observeRows(1).first().single()
            assertEquals("一蘭拉麵", row.title) // old note became the title
            assertEquals("", row.note)
            assertEquals(277.2, row.homeAmount, 0.0)
            assertEquals("/x/r.jpg", row.thumbPath)
            assertNull(row.minuteOfDay)
            assertNull(row.planItemId)
            val cat = db.lookupDao().observeCategories().first().single()
            assertEquals(Triple("吃", "", -1), Triple(cat.name, cat.icon, cat.color))
            val trip = db.tripDao().observeTrip(1).first()!!
            assertNull(trip.coverPath)
            assertNull(trip.coverTheme)
            assertEquals(32, trip.uuid.length) // every old trip gets its own identity for sharing
            assertNull(trip.sharedBy)
            assertEquals(40000.0, trip.budget!!, 0.0)
            assertEquals(listOf("我"), db.tripDao().getMembers(1).map { it.name })
            assertEquals(0, db.planDao().observeRows(1).first().size)
            val e = db.expenseDao().get(1)!!
            assertEquals(32, e.uuid.length) // v7: every old expense gets its own identity
            assertEquals(false, e.pending)
            assertNull(e.addedBy)
        }
        db.close()
    }

    @Test fun eachStepValidates() {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 2, true, AppDatabase.MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(name, 3, true, AppDatabase.MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(name, 4, true, AppDatabase.MIGRATION_3_4).close()
        helper.runMigrationsAndValidate(name, 5, true, AppDatabase.MIGRATION_4_5).close()
        helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_5_6).close()
        helper.runMigrationsAndValidate(name, 7, true, AppDatabase.MIGRATION_6_7).close()
    }

    @Test fun v6GivesEachTripADifferentUuid() {
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO trip (id, name, startDate, endDate, homeCurrency, budget, splitEnabled, createdAt, archived) VALUES (1, 'A', 0, 1, 'TWD', NULL, 0, 0, 0)")
            execSQL("INSERT INTO trip (id, name, startDate, endDate, homeCurrency, budget, splitEnabled, createdAt, archived) VALUES (2, 'B', 0, 1, 'TWD', NULL, 0, 0, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_5_6)
        val ids = db.query("SELECT uuid FROM trip").use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        db.close()
        assertEquals(2, ids.toSet().size)
    }
}
