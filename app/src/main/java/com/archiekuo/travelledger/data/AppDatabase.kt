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
        PaymentMethod::class, Expense::class, ExpenseShare::class, Photo::class,
    ],
    version = 3,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun lookupDao(): LookupDao
    abstract fun photoDao(): PhotoDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel-ledger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
                .build()
    }
}
