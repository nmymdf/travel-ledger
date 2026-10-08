package com.archiekuo.travelledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archiekuo.travelledger.data.AppDatabase.Companion.setup

/** In-memory database with the same seeding as the app; queries run on the calling thread. */
fun testDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .setup()
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()
