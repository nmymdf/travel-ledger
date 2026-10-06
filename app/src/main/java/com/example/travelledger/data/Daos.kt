package com.example.travelledger.data

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
    val totalHome: Double,
)

@Dao
interface TripDao {
    @Query(
        """SELECT t.id, t.name, t.startDate, t.endDate, t.budget,
                  COALESCE((SELECT SUM(homeAmount) FROM expense WHERE tripId = t.id), 0) AS totalHome
           FROM trip t WHERE t.archived = 0 ORDER BY t.startDate DESC"""
    )
    fun observeSummaries(): Flow<List<TripSummary>>

    @Query("SELECT * FROM trip WHERE id = :id")
    fun observeTrip(id: Long): Flow<Trip?>

    @Query("SELECT * FROM member WHERE tripId = :tripId ORDER BY id")
    fun observeMembers(tripId: Long): Flow<List<Member>>

    @Insert suspend fun insertTrip(trip: Trip): Long
    @Insert suspend fun insertMembers(members: List<Member>)

    @Transaction
    suspend fun createTrip(trip: Trip, memberNames: List<String>): Long {
        val id = insertTrip(trip)
        insertMembers(memberNames.map { Member(tripId = id, name = it) })
        return id
    }

    @Query("SELECT * FROM member WHERE tripId = :tripId ORDER BY id")
    suspend fun getMembers(tripId: Long): List<Member>

    @Query("DELETE FROM member WHERE id = :id")
    suspend fun deleteMember(id: Long)

    @Update suspend fun updateTrip(trip: Trip)

    /** Keeps existing members that still appear by name so expense references stay valid. */
    @Transaction
    suspend fun updateTripWithMembers(trip: Trip, memberNames: List<String>) {
        updateTrip(trip)
        val existing = getMembers(trip.id)
        existing.filter { it.name !in memberNames }.forEach { deleteMember(it.id) }
        val kept = existing.map { it.name }.toSet()
        insertMembers(memberNames.filter { it !in kept }.map { Member(tripId = trip.id, name = it) })
    }

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTrip(id: Long)
}

data class ExpenseRow(
    val id: Long,
    val date: Long,
    val amount: Double,
    val currency: String,
    val rate: Double,
    val homeAmount: Double,
    val categoryName: String?,
    val paymentMethodName: String?,
    val note: String,
)

@Dao
interface ExpenseDao {
    @Query(
        """SELECT e.id, e.date, e.amount, e.currency, e.rate, e.homeAmount,
                  c.name AS categoryName, p.name AS paymentMethodName, e.note
           FROM expense e
           LEFT JOIN category c ON c.id = e.categoryId
           LEFT JOIN payment_method p ON p.id = e.paymentMethodId
           WHERE e.tripId = :tripId ORDER BY e.date DESC, e.id DESC"""
    )
    fun observeRows(tripId: Long): Flow<List<ExpenseRow>>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Query("SELECT * FROM expense WHERE tripId = :tripId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(tripId: Long): Expense?

    @Insert suspend fun insert(expense: Expense): Long
    @Update suspend fun update(expense: Expense)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM trip_currency_rate WHERE tripId = :tripId ORDER BY currency")
    fun observeRates(tripId: Long): Flow<List<TripCurrencyRate>>

    @Query("SELECT * FROM trip_currency_rate WHERE tripId = :tripId")
    suspend fun getRates(tripId: Long): List<TripCurrencyRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: TripCurrencyRate)

    @Query("DELETE FROM trip_currency_rate WHERE tripId = :tripId AND currency = :currency")
    suspend fun deleteRate(tripId: Long, currency: String)
}

@Dao
interface LookupDao {
    @Query("SELECT COUNT(*) FROM category") suspend fun categoryCount(): Int
    @Insert suspend fun insertCategories(items: List<Category>)
    @Insert suspend fun insertPaymentMethods(items: List<PaymentMethod>)

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
