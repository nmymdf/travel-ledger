package com.example.travelledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTrip(id: Long)
}

@Dao
interface LookupDao {
    @Query("SELECT COUNT(*) FROM category") suspend fun categoryCount(): Int
    @Insert suspend fun insertCategories(items: List<Category>)
    @Insert suspend fun insertPaymentMethods(items: List<PaymentMethod>)
}
