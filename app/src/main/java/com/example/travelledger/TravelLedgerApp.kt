package com.example.travelledger

import android.app.Application
import com.example.travelledger.data.AppDatabase

class TravelLedgerApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
}
