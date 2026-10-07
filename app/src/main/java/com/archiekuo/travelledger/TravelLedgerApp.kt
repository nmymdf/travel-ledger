package com.archiekuo.travelledger

import android.app.Application
import com.archiekuo.travelledger.data.AppDatabase

class TravelLedgerApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
}
