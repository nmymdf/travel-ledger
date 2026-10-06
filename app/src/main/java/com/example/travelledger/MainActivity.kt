package com.example.travelledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.travelledger.ui.AppNav
import com.example.travelledger.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as TravelLedgerApp).db
        setContent { AppTheme { AppNav(db) } }
    }
}
