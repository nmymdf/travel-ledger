@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.travelledger.ui

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.travelledger.data.HOME_CURRENCY
import com.example.travelledger.data.Trip
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
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value, onChange, modifier.fillMaxWidth(), singleLine = true, textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = style, color = ledger.textMuted)
            inner()
        },
    )
}

private fun LocalDate.utcMillis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun TripEditScreen(
    initial: Trip?,
    initialMembers: List<String>,
    coverPath: String?,
    onPickCover: () -> Unit,
    onBack: () -> Unit,
    onSave: (Trip, List<String>) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var budget by rememberSaveable { mutableStateOf(initial?.budget?.let { fmtNumber(it) } ?: "") }
    var split by rememberSaveable { mutableStateOf(initial?.splitEnabled ?: false) }
    val members = remember { mutableStateListOf<String>().apply { addAll(initialMembers) } }
    var start by rememberSaveable { mutableStateOf(initial?.startDate ?: today.toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(initial?.endDate ?: today.plusDays(4).toEpochDay()) }
    var pickingDates by remember { mutableStateOf(false) }
    var addingMember by remember { mutableStateOf(false) }

    fun save() {
        val trip = (initial ?: Trip(name = "", startDate = 0, endDate = 0)).copy(
            name = name.trim(), startDate = start, endDate = end,
            budget = budget.toDoubleOrNull()?.takeIf { it > 0 }, splitEnabled = split, coverPath = coverPath,
        )
        onSave(trip, members.toList())
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
            Box(Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.large).clickable(onClick = onPickCover)) {
                TripCover(coverPath, name.ifBlank { "旅程" }, Modifier.matchParentSize(), iconSize = 130.dp)
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (coverPath == null) "加入封面" else "更換封面", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }

            FieldBox("旅程名稱") { FieldInput(name, { name = it }, "例如:東京美食之旅") }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldBox("開始日期", Modifier.weight(1f), onClick = { pickingDates = true }, trailing = { CalendarIcon() }) {
                    Text(fmtDate(start), style = MaterialTheme.typography.bodyLarge)
                }
                FieldBox("結束日期", Modifier.weight(1f), onClick = { pickingDates = true }, trailing = { CalendarIcon() }) {
                    Text(fmtDate(end), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "共 ${end - start + 1} 天",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            SectionHeader("旅行成員")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                members.forEach { m ->
                    MemberChip(m) { members.remove(m) }
                }
                AddChip("新增成員") { addingMember = true }
            }

            FieldBox(
                "結算幣別",
                trailing = { Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = ledger.textMuted) },
            ) {
                Text(currencyLabel(HOME_CURRENCY), style = MaterialTheme.typography.bodyLarge)
                Text("所有總計以新台幣計算,每筆支出可用任何幣別", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            FieldBox("預算(選填)", trailing = { Text("NT$", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                FieldInput(budget, { v -> budget = v.filter { it.isDigit() || it == '.' } }, "不設定", keyboardType = KeyboardType.Decimal)
            }

            LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Group, Palette[4], size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("分帳功能", style = MaterialTheme.typography.titleSmall)
                        Text("與朋友旅遊時開啟,記錄誰付款、誰分攤", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(split, { split = it })
                }
            }
        }
    }

    if (pickingDates) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = LocalDate.ofEpochDay(start).utcMillis(),
            initialSelectedEndDateMillis = LocalDate.ofEpochDay(end).utcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                TextButton({
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        start = s.utcDate().toEpochDay()
                        end = e.utcDate().toEpochDay()
                    }
                    pickingDates = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton({ pickingDates = false }) { Text("取消") } },
        ) { DateRangePicker(state, Modifier.height(500.dp), title = { Text("選擇旅程日期", Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }
    if (addingMember) {
        TextInputDialog("新增成員", "名字", onDismiss = { addingMember = false }) { n ->
            if (n !in members) members.add(n)
            addingMember = false
        }
    }
}

@Composable
private fun CalendarIcon() = Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun MemberChip(name: String, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, ledger.hairline, shape)
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.padding(start = 4.dp).size(24.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "移除", Modifier.size(14.dp), tint = ledger.textMuted)
        }
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
        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun TextInputDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.medium)
        },
        confirmButton = { TextButton({ onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("確定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
