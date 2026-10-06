package com.example.travelledger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_INT_DIGITS = 9
private const val MAX_DECIMALS = 2

/** Pure reducer for the amount keypad: digits, "00", ".", "⌫" and "C" (clear). */
fun applyKey(current: String, key: String): String = when (key) {
    "C" -> ""
    "⌫" -> current.dropLast(1)
    "." -> when {
        '.' in current -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
    "00" -> if (current.isEmpty() || current == "0") current else applyKey(applyKey(current, "0"), "0")
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

/**
 * Bottom keypad panel: typed amount, live home-currency preview, round keys and a Done button.
 * Backspace clears everything on long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadPanel(
    amount: String,
    currency: String,
    preview: String?,
    onKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier.fillMaxWidth().shadow(24.dp, shape).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(currencyLabel(currency), style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtTyped(amount), style = MaterialTheme.typography.displaySmall, maxLines = 1)
                Text(preview ?: " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(onClick = { onKey("⌫") }, onLongClick = { onKey("C") }),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Backspace, "刪除", Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        val rows = listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf("00", "0", "."))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(onClick = { onKey(key) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton("完成", onDone)
    }
}
