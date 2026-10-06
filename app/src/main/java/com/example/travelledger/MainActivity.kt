package com.example.travelledger

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.travelledger.ui.AppNav
import com.example.travelledger.ui.AppTheme
import com.example.travelledger.ui.ThemePrefs
import com.example.travelledger.ui.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as TravelLedgerApp).db
        setContent {
            var mode by remember { mutableStateOf(ThemePrefs.load(this)) }
            val dark = isDarkTheme(mode)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(mode) {
                AppNav(db, mode) { m -> mode = m; ThemePrefs.save(this, m) }
            }
        }
    }
}
