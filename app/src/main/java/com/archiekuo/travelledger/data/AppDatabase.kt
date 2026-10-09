package com.archiekuo.travelledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Trip::class, TripCurrencyRate::class, Member::class, Category::class,
        PaymentMethod::class, Expense::class, ExpenseShare::class, Photo::class, PlanItem::class,
    ],
    version = 6,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun lookupDao(): LookupDao
    abstract fun photoDao(): PhotoDao
    abstract fun planDao(): PlanDao

    companion object {
        /** v2: trip cover photo, category icon and color. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip ADD COLUMN coverPath TEXT")
                db.execSQL("ALTER TABLE category ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE category ADD COLUMN color INTEGER NOT NULL DEFAULT -1")
            }
        }

        /** v3: expense title (old notes become titles), time of day, recognized receipt text. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expense ADD COLUMN minuteOfDay INTEGER")
                db.execSQL("ALTER TABLE expense ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE expense SET title = note, note = ''")
            }
        }

        /** v4: itinerary items, and expenses can point at the item they were spent on. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `plan_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `title` TEXT NOT NULL,
                        `categoryId` INTEGER, `date` INTEGER, `minuteOfDay` INTEGER, `status` TEXT NOT NULL,
                        `reservation` TEXT NOT NULL, `reservationNote` TEXT NOT NULL, `location` TEXT NOT NULL,
                        `estCost` REAL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trip`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_item_tripId` ON `plan_item` (`tripId`)")
                db.execSQL("ALTER TABLE expense ADD COLUMN planItemId INTEGER")
            }
        }

        /** v5: hand-picked cover illustration. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip ADD COLUMN coverTheme TEXT")
            }
        }

        /** v6: trip identity for sharing, and who shared a read-only copy. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE trip SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
                db.execSQL("ALTER TABLE trip ADD COLUMN sharedBy TEXT")
                db.execSQL("ALTER TABLE trip ADD COLUMN sharedAt INTEGER")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel-ledger.db").setup().build()

        /** Shared setup for the real database and the in-memory one used by tests. */
        fun RoomDatabase.Builder<AppDatabase>.setup(): RoomDatabase.Builder<AppDatabase> =
            addMigrations(*ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        listOf("吃", "交通", "購物", "住宿", "景點", "其他").forEachIndexed { i, n ->
                            db.execSQL("INSERT INTO category (name, sortOrder, icon, color) VALUES ('$n', $i, '', -1)")
                        }
                        listOf("現金", "信用卡", "行動支付").forEachIndexed { i, n ->
                            db.execSQL("INSERT INTO payment_method (name, sortOrder) VALUES ('$n', $i)")
                        }
                    }
                })
    }
}
