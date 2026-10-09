package com.archiekuo.travelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.HOME_CURRENCY
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanStatus
import com.archiekuo.travelledger.data.Reservation
import com.archiekuo.travelledger.data.Trip
import java.time.Instant
import java.time.ZoneId

/** Shown on a trip someone shared with us: who sent it and when; nothing here can be changed. */
@Composable
fun ReadOnlyBanner(trip: Trip, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val at = trip.sharedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Visibility, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(
            "唯讀 · ${trip.sharedBy} 分享" + (at?.let { " · %d/%d %02d:%02d 更新".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer,
        )
    }
}

@Composable
private fun DetailLine(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailCard(lines: List<Triple<ImageVector, String, String>>) {
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
        lines.forEachIndexed { i, (icon, label, value) ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 50.dp), color = ledger.hairline)
            DetailLine(icon, label, value)
        }
    }
}

/** An expense in a shared trip. */
@Composable
fun ExpenseDetailScreen(
    state: EditState?,
    categories: List<Category>,
    methods: List<PaymentMethod>,
    photos: List<PhotoItem>,
    onBack: () -> Unit,
    onSavePhoto: (Int) -> Unit,
) {
    var viewing by remember { mutableStateOf<Int?>(null) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { AppTopBar("支出明細", onBack = onBack, closeIcon = true) }) { pad ->
            val s = state ?: return@Scaffold
            val category = categories.firstOrNull { it.id == s.categoryId }
            val style = categoryStyle(category?.name, category?.icon, category?.color)
            Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(style.icon, style.color, size = 48.dp, corner = 14.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title.ifBlank { category?.name ?: "支出" }, style = MaterialTheme.typography.titleLarge)
                            Text(fmtShortDateTime(s.date, s.minuteOfDay), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(fmtAmount(s.amountValue ?: 0.0, s.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (s.currency != HOME_CURRENCY) {
                        Text(
                            "≈ ${fmtMoney(s.homeAmount ?: 0.0)} · 匯率 ${s.rate}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (photos.isNotEmpty()) PhotoStrip(photos, onOpen = { viewing = it })
                DetailCard(
                    buildList {
                        add(Triple(Icons.Rounded.Category, "分類", category?.name ?: "未分類"))
                        add(Triple(Icons.Rounded.CreditCard, "付款", methods.firstOrNull { it.id == s.paymentId }?.name ?: "未指定"))
                        if (s.note.isNotBlank()) add(Triple(Icons.Rounded.Notes, "備註", s.note))
                    },
                )
            }
        }
        viewing?.let { i ->
            if (i in photos.indices) PhotoViewer(photos, i, onClose = { viewing = null }, onType = { _, _ -> }, onDelete = {}, onSave = onSavePhoto, editable = false)
        }
    }
}

/** A plan item in a shared trip. */
@Composable
fun PlanDetailScreen(item: PlanItem?, trip: Trip?, categories: List<Category>, onBack: () -> Unit, onMap: (PlanItem) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { AppTopBar("行程", subtitle = trip?.name, onBack = onBack, closeIcon = true) }) { pad ->
        val p = item ?: return@Scaffold
        val t = trip ?: return@Scaffold
        val category = categories.firstOrNull { it.id == p.categoryId }
        val style = categoryStyle(category?.name, category?.icon, category?.color)
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LedgerCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(style.icon, style.color, size = 48.dp, corner = 14.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            (p.date?.let { "Day ${it - t.startDate + 1} · ${fmtShortDate(it)}" } ?: "待排") + (p.minuteOfDay?.let { " · ${fmtTime(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DetailCard(
                buildList {
                    add(Triple(Icons.Rounded.Category, "分類", category?.name ?: "未分類"))
                    add(Triple(Icons.Rounded.CheckCircleOutline, "狀態", when (p.status) { PlanStatus.DONE -> "已去"; PlanStatus.SKIPPED -> "跳過"; else -> "未去" }))
                    when (p.reservation) {
                        Reservation.NEEDED -> add(Triple(Icons.Rounded.EventSeat, "訂位", "需要訂位" + p.reservationNote.let { if (it.isBlank()) "" else " · $it" }))
                        Reservation.BOOKED -> add(Triple(Icons.Rounded.EventSeat, "訂位", "已訂好" + p.reservationNote.let { if (it.isBlank()) "" else " · $it" }))
                    }
                    if (p.location.isNotBlank()) add(Triple(Icons.Rounded.Place, "地點", p.location))
                    p.estCost?.let { add(Triple(Icons.Rounded.Payments, "預估", fmtMoney(it))) }
                    if (p.note.isNotBlank()) add(Triple(Icons.Rounded.Notes, "筆記", p.note))
                },
            )
            FilledTonalButton({ onMap(p) }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Map, null)
                Spacer(Modifier.width(6.dp))
                Text("在地圖開啟")
            }
        }
    }
}
