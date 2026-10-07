package com.archiekuo.travelledger

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
import com.archiekuo.travelledger.ui.AppNav
import com.archiekuo.travelledger.ui.AppPrefs
import com.archiekuo.travelledger.ui.AppTheme
import com.archiekuo.travelledger.ui.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as TravelLedgerApp).db
        setContent {
            var settings by remember { mutableStateOf(AppPrefs.load(this)) }
            val dark = isDarkTheme(settings.theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(settings.theme, settings.fontSize) {
                AppNav(db, settings) { s -> settings = s; AppPrefs.save(this, s) }
            }
        }
    }
}
