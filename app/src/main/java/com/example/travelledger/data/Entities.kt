package com.example.travelledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All totals are reported in this currency; expenses may use any currency. */
const val HOME_CURRENCY = "TWD"

/** Dates are stored as epoch days (LocalDate.toEpochDay). */
@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val homeCurrency: String = HOME_CURRENCY,
    val budget: Double? = null,
    val splitEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

@Entity(
    tableName = "trip_currency_rate",
    primaryKeys = ["tripId", "currency"],
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class TripCurrencyRate(
    val tripId: Long,
    val currency: String,
    /** 1 unit of [currency] in TWD. */
    val rate: Double,
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "manual",
)

@Entity(
    tableName = "member",
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
)

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(tableName = "payment_method")
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "expense",
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val date: Long,
    val amount: Double,
    val currency: String,
    val rate: Double,
    val homeAmount: Double,
    val categoryId: Long?,
    val paymentMethodId: Long?,
    val payerId: Long?,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "expense_share",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [ForeignKey(Expense::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("memberId")],
)
data class ExpenseShare(val expenseId: Long, val memberId: Long)

@Entity(
    tableName = "photo",
    foreignKeys = [ForeignKey(Expense::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("expenseId")],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val type: String, // RECEIPT / MEMORY
    val path: String,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val savedToGallery: Boolean = false,
)
