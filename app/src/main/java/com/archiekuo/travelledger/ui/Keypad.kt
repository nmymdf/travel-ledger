package com.archiekuo.travelledger.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archiekuo.travelledger.data.HOME_CURRENCY

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
 * Bottom calculator (about half the screen): currency switch + amount, conversion line, keys and Save.
 * Tapping the currency opens the trip's currencies; tapping the conversion line edits the rate.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadPanel(
    amount: String,
    currency: String,
    currencies: List<String>,
    rate: String,
    homeAmount: Double?,
    canSave: Boolean,
    onKey: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onOtherCurrency: () -> Unit,
    onEditRate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Height of one key; the handle on top changes it (four rows, so dragging by d moves each key by d/4). */
    keyHeight: Dp = KEY_HEIGHT_DEFAULT.dp,
    onKeyHeight: ((Dp) -> Unit)? = null,
    onKeyHeightDone: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val density = LocalDensity.current
    val latest by rememberUpdatedState(keyHeight)
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().shadow(16.dp, shape).clip(shape)
            .background(cs.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = if (onKeyHeight != null) 0.dp else 12.dp, bottom = 10.dp),
    ) {
        if (onKeyHeight != null) {
            // Drag handle: pull up for bigger keys, push down for more room above.
            Box(
                Modifier.fillMaxWidth().height(22.dp)
                    .draggable(
                        rememberDraggableState { px ->
                            val d = with(density) { px.toDp() }
                            onKeyHeight((latest - d / 4).coerceIn(KEY_HEIGHT_MIN.dp, KEY_HEIGHT_MAX.dp))
                        },
                        Orientation.Vertical,
                        onDragStopped = { onKeyHeightDone() },
                    )
                    .semantics { contentDescription = "拖曳調整計算機高度" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(cs.outline)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer)
                        .clickable { menu = true }.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(currency, style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
                    Icon(Icons.Rounded.ExpandMore, "選擇幣別", Modifier.size(20.dp), tint = cs.onPrimaryContainer)
                }
                DropdownMenu(menu, { menu = false }) {
                    currencies.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(currencyLabel(c), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { menu = false; onCurrency(c) },
                            trailingIcon = { if (c == currency) Text("✓", color = cs.primary) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("其他幣別…") }, onClick = { menu = false; onOtherCurrency() })
                }
            }
            Text(
                fmtTyped(amount),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                color = if (amount.isEmpty()) ledger.textMuted else cs.onSurface,
                textAlign = TextAlign.End, maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currency != HOME_CURRENCY) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onEditRate).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val rateOk = rate.toDoubleOrNull()?.let { it > 0 } == true
                    Text(
                        if (rateOk) "匯率 $rate" else "請設定匯率",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (rateOk) cs.onSurfaceVariant else cs.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Edit, "修改匯率", Modifier.size(15.dp), tint = cs.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "≈ ${fmtMoney(homeAmount ?: 0.0)}",
                    style = MaterialTheme.typography.titleSmall, color = cs.primary,
                )
            } else {
                Text("新台幣", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        val gap = 8.dp
        val keyH = keyHeight
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf("00", "0", ".")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { k -> Key(k, keyH, Modifier.weight(1f)) { onKey(k) } }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(
                    Modifier.fillMaxWidth().height(keyH).clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant)
                        .combinedClickable(onClick = { onKey("⌫") }, onLongClick = { onKey("C") }),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Backspace, "刪除(長按清除)", Modifier.size(24.dp)) }
                Key("C", keyH, Modifier.fillMaxWidth(), muted = true) { onKey("C") }
                Box(
                    Modifier.fillMaxWidth().height(keyH * 2 + gap).clip(RoundedCornerShape(16.dp))
                        .background(if (canSave) cs.primary else cs.surfaceVariant)
                        .clickable(enabled = canSave, onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "儲存", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = if (canSave) cs.onPrimary else ledger.textMuted,
                    )
                }
            }
        }
    }
}

const val KEY_HEIGHT_DEFAULT = 52f
const val KEY_HEIGHT_MIN = 40f
const val KEY_HEIGHT_MAX = 80f

@Composable
private fun Key(label: String, height: Dp, modifier: Modifier, muted: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.height(height).clip(RoundedCornerShape(16.dp))
            .background(if (muted) cs.surfaceVariant else cs.background)
            .border(1.dp, ledger.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = if (muted) 18.sp else 24.sp, fontWeight = FontWeight.Medium,
            color = if (muted) cs.onSurfaceVariant else cs.onSurface,
        )
    }
}
