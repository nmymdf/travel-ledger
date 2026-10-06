package com.example.travelledger.data

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
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun lookupDao(): LookupDao

    companion object {
        /** v2: trip cover photo, category icon and color. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip ADD COLUMN coverPath TEXT")
                db.execSQL("ALTER TABLE category ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE category ADD COLUMN color INTEGER NOT NULL DEFAULT -1")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel-ledger.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        listOf("交通", "住宿", "購物", "吃", "其他").forEachIndexed { i, n ->
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
