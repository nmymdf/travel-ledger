package com.example.travelledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val MAX_INT_DIGITS = 9
private const val MAX_DECIMALS = 2

/** Pure reducer for the amount keypad: digits, ".", and "⌫". */
fun applyKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1)
    "." -> when {
        '.' in current -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
    else -> {
        val dot = current.indexOf('.')
        when {
            dot >= 0 && current.length - dot - 1 >= MAX_DECIMALS -> current
            dot < 0 && current.length >= MAX_INT_DIGITS -> current
            current == "0" -> key
            else -> current + key
        }
    }
}

@Composable
fun NumberKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton({ onKey(key) }, Modifier.weight(1f).height(56.dp)) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
