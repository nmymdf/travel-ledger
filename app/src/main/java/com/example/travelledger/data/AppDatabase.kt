package com.example.travelledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Trip::class, TripCurrencyRate::class, Member::class, Category::class,
        PaymentMethod::class, Expense::class, ExpenseShare::class, Photo::class,
    ],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun lookupDao(): LookupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel-ledger.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        listOf("交通", "住宿", "購物", "吃", "其他").forEachIndexed { i, n ->
                            db.execSQL("INSERT INTO category (name, sortOrder) VALUES ('$n', $i)")
                        }
                        listOf("現金", "信用卡", "行動支付").forEachIndexed { i, n ->
                            db.execSQL("INSERT INTO payment_method (name, sortOrder) VALUES ('$n', $i)")
                        }
                    }
                })
                .build()
    }
}
