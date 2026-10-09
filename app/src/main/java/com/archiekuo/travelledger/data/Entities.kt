package com.archiekuo.travelledger.data

import androidx.room.ColumnInfo
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
    /** Absolute path of the cover image in app-private storage, or null for a generated cover. */
    val coverPath: String? = null,
    /** Illustration chosen by hand (CoverTheme name), or null to pick it from the trip name. */
    val coverTheme: String? = null,
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
    /** Icon key (see CategoryStyle); empty means "guess from the name". */
    @ColumnInfo(defaultValue = "") val icon: String = "",
    /** Palette index (see CategoryStyle); -1 means "guess from the name". */
    @ColumnInfo(defaultValue = "-1") val color: Int = -1,
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
    /** Shop or item name, shown as the expense title. */
    @ColumnInfo(defaultValue = "") val title: String = "",
    /** Local time of the expense in minutes after midnight, or null if unknown. */
    val minuteOfDay: Int? = null,
    /** Text recognized from receipt photos, kept for search. */
    @ColumnInfo(defaultValue = "") val ocrText: String = "",
    /** The itinerary item this expense was recorded for, if any. */
    val planItemId: Long? = null,
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
    val type: String, // PhotoType.RECEIPT / PhotoType.MEMORY
    val path: String,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val savedToGallery: Boolean = false,
)

object PhotoType {
    const val RECEIPT = "RECEIPT"
    const val MEMORY = "MEMORY"
}

/** A place or activity planned for the trip: scheduled on a day, or unscheduled ("待排") when [date] is null. */
@Entity(
    tableName = "plan_item",
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class PlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    /** Shares the expense categories so one-tap recording files it correctly. */
    val categoryId: Long? = null,
    val date: Long? = null,
    val minuteOfDay: Int? = null,
    val status: String = PlanStatus.TODO,
    val reservation: String = Reservation.NONE,
    /** Booking time, party size, confirmation number… */
    val reservationNote: String = "",
    /** Address or a map link. */
    val location: String = "",
    /** Estimated cost in TWD. */
    val estCost: Double? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object PlanStatus {
    const val TODO = "TODO"
    const val DONE = "DONE"
    const val SKIPPED = "SKIPPED"
}

object Reservation {
    const val NONE = "NONE"
    const val NEEDED = "NEEDED"
    const val BOOKED = "BOOKED"
}
