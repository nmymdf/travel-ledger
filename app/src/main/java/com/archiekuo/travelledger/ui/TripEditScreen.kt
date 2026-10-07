@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.archiekuo.travelledger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.HOME_CURRENCY
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Single-line text input styled to sit inside a [FieldBox]. */
@Composable
fun FieldInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    textAlign: TextAlign = TextAlign.Start,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = textAlign)
    BasicTextField(
        value, onChange, modifier.fillMaxWidth(), singleLine = true, textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            Box(contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style, color = ledger.textMuted)
                inner()
            }
        },
    )
}

@Composable
fun TripEditScreen(
    initial: Trip?,
    initialMembers: List<String>,
    initialRates: List<TripCurrencyRate>,
    coverPath: String?,
    coverBusy: Boolean,
    onPickCover: () -> Unit,
    onBack: () -> Unit,
    onSave: (Trip, List<String>, List<TripCurrencyRate>) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var budget by rememberSaveable { mutableStateOf(initial?.budget?.let { fmtNumber(it) } ?: "") }
    var split by rememberSaveable { mutableStateOf(initial?.splitEnabled ?: false) }
    val members = remember { mutableStateListOf<String>().apply { addAll(initialMembers) } }
    val rates = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(initialRates.map { it.currency to fmtNumber(it.rate) }) } }
    var start by rememberSaveable { mutableStateOf(initial?.startDate ?: today.toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(initial?.endDate ?: today.plusDays(4).toEpochDay()) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" or "end"
    var addingMember by remember { mutableStateOf(false) }
    var addingCurrency by remember { mutableStateOf(false) }

    fun save() {
        val trip = (initial ?: Trip(name = "", startDate = 0, endDate = 0)).copy(
            name = name.trim(), startDate = start, endDate = end,
            budget = budget.toDoubleOrNull()?.takeIf { it > 0 }, splitEnabled = split, coverPath = coverPath,
        )
        val r = rates.mapNotNull { (c, v) -> v.toDoubleOrNull()?.takeIf { it > 0 }?.let { TripCurrencyRate(trip.id, c, it) } }
        onSave(trip, members.toList(), r)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                if (initial == null) "新增旅程" else "編輯旅程", onBack = onBack, closeIcon = true,
                actions = { ConfirmButton(name.isNotBlank()) { save() } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onPickCover)) {
                TripCover(coverPath, name.ifBlank { "旅程" }, Modifier.matchParentSize(), iconSize = 120.dp)
                if (coverBusy) {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (coverPath == null) "從相簿選封面" else "更換封面", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            FieldBox("旅程名稱") { FieldInput(name, { name = it }, "例如:東京美食之旅") }

            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox("出發", Modifier.weight(1f).fillMaxHeight(), onClick = { picking = "start" }, trailing = { CalendarIcon() }) {
                    Text(fmtShortDate(start), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                FieldBox("回程", Modifier.weight(1f).fillMaxHeight(), onClick = { picking = "end" }, trailing = { CalendarIcon() }) {
                    Text(fmtShortDate(end), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
            Text(
                "${LocalDate.ofEpochDay(start).year} 年 · 共 ${end - start + 1} 天",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 0.dp),
            )

            SectionHeader("旅行成員 · ${members.size} 人")
            members.plus("+").chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { m ->
                        if (m == "+") AddCell("新增成員", Modifier.weight(1f)) { addingMember = true }
                        else MemberCell(m, Modifier.weight(1f)) { members.remove(m) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SectionHeader("預設匯率") {
                Text("1 外幣 = ? 新台幣", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rates.isNotEmpty()) {
                LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
                    rates.forEachIndexed { i, (code, value) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = ledger.hairline)
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CurrencyBadge(code, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(CURRENCY_NAMES[code] ?: code, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.width(110.dp).clip(RoundedCornerShape(10.dp)).background(ledger.field).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                FieldInput(
                                    value, { v -> rates[i] = code to v.filter { it.isDigit() || it == '.' } }, "匯率",
                                    keyboardType = KeyboardType.Decimal, textAlign = TextAlign.End,
                                )
                            }
                            IconButton({ rates.removeAt(i) }) { Icon(Icons.Rounded.Close, "移除", Modifier.size(18.dp), tint = ledger.textMuted) }
                        }
                    }
                }
            }
            AddChip("新增幣別") { addingCurrency = true }
            Text(
                "所有總計以新台幣計算;記帳時每筆的匯率仍可個別修改。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FieldBox("預算(選填)", trailing = { Text("NT$", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                FieldInput(budget, { v -> budget = v.filter { it.isDigit() || it == '.' } }, "不設定", keyboardType = KeyboardType.Decimal)
            }

            LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Group, Palette[4], size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("分帳功能", style = MaterialTheme.typography.titleSmall)
                        Text("與朋友旅遊時開啟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(split, { split = it })
                }
            }
        }
    }

    when (picking) {
        "start" -> DayPickerDialog("出發日期", start, onDismiss = { picking = null }) { d ->
            // Keep the trip length when the departure moves.
            end = d + (end - start).coerceAtLeast(0)
            start = d
            picking = null
        }
        "end" -> DayPickerDialog("回程日期", end, minDay = start, onDismiss = { picking = null }) { d ->
            end = d
            picking = null
        }
    }
    if (addingMember) {
        TextInputDialog("新增成員", "名字", onDismiss = { addingMember = false }) { n ->
            if (n !in members) members.add(n)
            addingMember = false
        }
    }
    if (addingCurrency) {
        CurrencyDialog("", { addingCurrency = false }) { c ->
            if (c != HOME_CURRENCY && rates.none { it.first == c }) rates.add(c to "")
            addingCurrency = false
        }
    }
}

@Composable
private fun CalendarIcon() = Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun MemberCell(name: String, modifier: Modifier, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(52.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, ledger.hairline, shape)
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        IconButton(onRemove, Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, "移除", Modifier.size(16.dp), tint = ledger.textMuted) }
    }
}

@Composable
private fun AddCell(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(52.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.PersonAdd, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AddChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    decimal: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { v -> text = if (decimal) v.filter { it.isDigit() || it == '.' } else v },
                label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
            )
        },
        confirmButton = { TextButton({ onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("確定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
