package com.archiekuo.travelledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TripSummary(
    val id: Long,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val budget: Double?,
    val coverPath: String?,
    val totalHome: Double,
    val memberCount: Int,
    /** Comma-separated foreign currencies used in this trip, or null. */
    val currencies: String?,
)

@Dao
interface TripDao {
    @Query(
        """SELECT t.id, t.name, t.startDate, t.endDate, t.budget, t.coverPath,
                  COALESCE((SELECT SUM(homeAmount) FROM expense WHERE tripId = t.id), 0) AS totalHome,
                  (SELECT COUNT(*) FROM member WHERE tripId = t.id) AS memberCount,
                  (SELECT GROUP_CONCAT(currency) FROM
                      (SELECT DISTINCT currency FROM expense WHERE tripId = t.id AND currency != 'TWD'
                       UNION SELECT currency FROM trip_currency_rate WHERE tripId = t.id)) AS currencies
           FROM trip t WHERE t.archived = 0 ORDER BY t.startDate DESC"""
    )
    fun observeSummaries(): Flow<List<TripSummary>>

    @Query("SELECT * FROM trip WHERE archived = 0 ORDER BY startDate DESC")
    suspend fun getTrips(): List<Trip>

    @Query("SELECT * FROM trip WHERE id = :id")
    fun observeTrip(id: Long): Flow<Trip?>

    @Query("SELECT * FROM member WHERE tripId = :tripId ORDER BY id")
    fun observeMembers(tripId: Long): Flow<List<Member>>

    @Insert suspend fun insertTrip(trip: Trip): Long
    @Insert suspend fun insertMembers(members: List<Member>)

    @Transaction
    suspend fun createTrip(trip: Trip, memberNames: List<String>, rates: List<TripCurrencyRate>): Long {
        val id = insertTrip(trip)
        insertMembers(memberNames.map { Member(tripId = id, name = it) })
        rates.forEach { upsertRate(it.copy(tripId = id)) }
        return id
    }

    @Query("SELECT * FROM member WHERE tripId = :tripId ORDER BY id")
    suspend fun getMembers(tripId: Long): List<Member>

    @Query("DELETE FROM member WHERE id = :id")
    suspend fun deleteMember(id: Long)

    @Update suspend fun updateTrip(trip: Trip)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: TripCurrencyRate)

    @Query("DELETE FROM trip_currency_rate WHERE tripId = :tripId")
    suspend fun deleteRates(tripId: Long)

    /** Keeps existing members that still appear by name so expense references stay valid. */
    @Transaction
    suspend fun updateTrip(trip: Trip, memberNames: List<String>, rates: List<TripCurrencyRate>) {
        updateTrip(trip)
        val existing = getMembers(trip.id)
        existing.filter { it.name !in memberNames }.forEach { deleteMember(it.id) }
        val kept = existing.map { it.name }.toSet()
        insertMembers(memberNames.filter { it !in kept }.map { Member(tripId = trip.id, name = it) })
        deleteRates(trip.id)
        rates.forEach { upsertRate(it.copy(tripId = trip.id)) }
    }

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("SELECT coverPath FROM trip WHERE id = :id")
    suspend fun coverPath(id: Long): String?
}

data class ExpenseRow(
    val id: Long,
    val date: Long,
    val minuteOfDay: Int?,
    val title: String,
    val amount: Double,
    val currency: String,
    val rate: Double,
    val homeAmount: Double,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Int?,
    val paymentMethodName: String?,
    val note: String,
    val thumbPath: String?,
    val planItemId: Long?,
)

/** A previously used expense title and the category it was last filed under. */
data class TitleSuggestion(val title: String, val categoryId: Long?, val lastUsed: Long)

@Dao
interface ExpenseDao {
    @Query(
        """SELECT e.id, e.date, e.minuteOfDay, e.title, e.amount, e.currency, e.rate, e.homeAmount,
                  e.categoryId, c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor,
                  p.name AS paymentMethodName, e.note,
                  (SELECT path FROM photo WHERE expenseId = e.id ORDER BY id LIMIT 1) AS thumbPath, e.planItemId
           FROM expense e
           LEFT JOIN category c ON c.id = e.categoryId
           LEFT JOIN payment_method p ON p.id = e.paymentMethodId
           WHERE e.tripId = :tripId
           ORDER BY e.date DESC, COALESCE(e.minuteOfDay, -1) DESC, e.id DESC"""
    )
    fun observeRows(tripId: Long): Flow<List<ExpenseRow>>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Query("SELECT * FROM expense WHERE tripId = :tripId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(tripId: Long): Expense?

    /** SQLite returns the other columns from the row holding MAX(createdAt). */
    @Query(
        """SELECT title, categoryId, MAX(createdAt) AS lastUsed FROM expense
           WHERE title != '' GROUP BY title ORDER BY lastUsed DESC LIMIT 300"""
    )
    suspend fun titleSuggestions(): List<TitleSuggestion>

    @Insert suspend fun insert(expense: Expense): Long
    @Update suspend fun update(expense: Expense)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM trip_currency_rate WHERE tripId = :tripId ORDER BY currency")
    fun observeRates(tripId: Long): Flow<List<TripCurrencyRate>>

    @Query("SELECT * FROM trip_currency_rate WHERE tripId = :tripId ORDER BY currency")
    suspend fun getRates(tripId: Long): List<TripCurrencyRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: TripCurrencyRate)
}

/** A plan item with its category look and what has been spent on it so far. */
data class PlanRow(
    val id: Long,
    val title: String,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Int?,
    val date: Long?,
    val minuteOfDay: Int?,
    val status: String,
    val reservation: String,
    val reservationNote: String,
    val location: String,
    val estCost: Double?,
    val spent: Double,
)

@Dao
interface PlanDao {
    @Query(
        """SELECT pl.id, pl.title, pl.categoryId, c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor,
                  pl.date, pl.minuteOfDay, pl.status, pl.reservation, pl.reservationNote, pl.location, pl.estCost,
                  COALESCE((SELECT SUM(homeAmount) FROM expense WHERE planItemId = pl.id), 0) AS spent
           FROM plan_item pl LEFT JOIN category c ON c.id = pl.categoryId
           WHERE pl.tripId = :tripId
           ORDER BY pl.date IS NULL, pl.date, pl.minuteOfDay IS NULL, pl.minuteOfDay, pl.id"""
    )
    fun observeRows(tripId: Long): Flow<List<PlanRow>>

    @Query("SELECT * FROM plan_item WHERE id = :id")
    suspend fun get(id: Long): PlanItem?

    @Insert suspend fun insert(item: PlanItem): Long
    @Insert suspend fun insertAll(items: List<PlanItem>)
    @Update suspend fun update(item: PlanItem)

    @Query("UPDATE plan_item SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE plan_item SET date = :date WHERE id = :id")
    suspend fun setDate(id: Long, date: Long?)

    @Query("UPDATE expense SET planItemId = NULL WHERE planItemId = :id")
    suspend fun unlinkExpenses(id: Long)

    @Query("DELETE FROM plan_item WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        unlinkExpenses(id)
        deleteRow(id)
    }
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo WHERE expenseId = :expenseId ORDER BY id")
    suspend fun forExpense(expenseId: Long): List<Photo>

    @Query("SELECT path FROM photo WHERE expenseId IN (SELECT id FROM expense WHERE tripId = :tripId)")
    suspend fun pathsForTrip(tripId: Long): List<String>

    @Insert suspend fun insert(photo: Photo): Long
    @Update suspend fun update(photo: Photo)

    @Query("DELETE FROM photo WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LookupDao {
    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM payment_method ORDER BY sortOrder, id")
    fun observePaymentMethods(): Flow<List<PaymentMethod>>

    @Insert suspend fun insertCategory(item: Category): Long
    @Insert suspend fun insertPaymentMethod(item: PaymentMethod): Long
    @Update suspend fun updateCategories(items: List<Category>)
    @Update suspend fun updatePaymentMethods(items: List<PaymentMethod>)

    @Query("SELECT id FROM category WHERE name = :name LIMIT 1")
    suspend fun findCategoryId(name: String): Long?

    @Query("UPDATE expense SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long?)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteCategoryRow(id: Long)

    @Query("UPDATE expense SET paymentMethodId = NULL WHERE paymentMethodId = :id")
    suspend fun clearPaymentMethod(id: Long)

    @Query("DELETE FROM payment_method WHERE id = :id")
    suspend fun deletePaymentMethodRow(id: Long)

    /** Expenses using the deleted category fall back to "其他" (or no category if that is gone). */
    @Transaction
    suspend fun deleteCategory(id: Long) {
        val other = findCategoryId("其他")
        reassignCategory(id, other?.takeIf { it != id })
        deleteCategoryRow(id)
    }

    @Transaction
    suspend fun deletePaymentMethod(id: Long) {
        clearPaymentMethod(id)
        deletePaymentMethodRow(id)
    }
}
