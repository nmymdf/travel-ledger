package com.example.travelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.travelledger.data.TripSummary
import java.time.LocalDate

enum class TripFilter(val label: String) { ACTIVE("進行中"), ENDED("已結束"), ALL("全部") }

private fun TripSummary.ended(today: LocalDate) = endDate < today.toEpochDay()

/** "還有 12 天出發" / "旅行中 · 第 3 天" / "已結束". */
fun tripStatus(start: Long, end: Long, today: LocalDate): String {
    val t = today.toEpochDay()
    return when {
        t < start -> if (start - t == 1L) "明天出發" else "還有 ${start - t} 天出發"
        t <= end -> "旅行中 · 第 ${t - start + 1} 天"
        else -> "已結束"
    }
}

@Composable
fun TripListScreen(
    trips: List<TripSummary>,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val activeCount = trips.count { !it.ended(today) }
    var filter by rememberSaveable { mutableStateOf(if (activeCount > 0 || trips.isEmpty()) TripFilter.ACTIVE else TripFilter.ALL) }
    val shown = when (filter) {
        TripFilter.ACTIVE -> trips.filter { !it.ended(today) }.sortedBy { it.startDate }
        TripFilter.ENDED -> trips.filter { it.ended(today) }
        TripFilter.ALL -> trips
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HomeHeader(onSettings) }
            item { PillButton("新增旅程", onAdd, icon = Icons.Rounded.Add) }
            if (trips.isNotEmpty()) {
                item {
                    FilterTabs(
                        filter,
                        counts = mapOf(
                            TripFilter.ACTIVE to activeCount,
                            TripFilter.ENDED to trips.size - activeCount,
                            TripFilter.ALL to trips.size,
                        ),
                        onSelect = { filter = it },
                    )
                }
            }
            if (shown.isEmpty()) {
                item { EmptyTrips(if (trips.isEmpty()) "還沒有旅程" else "這裡沒有旅程", trips.isEmpty()) }
            }
            items(shown, key = { it.id }) { t ->
                if (t == shown.first()) FeaturedTripCard(t, today) { onOpen(t.id) }
                else CompactTripCard(t, today) { onOpen(t.id) }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Rounded.Luggage, MaterialTheme.colorScheme.primary, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("旅帳", style = MaterialTheme.typography.headlineSmall)
            Text("每一趟旅程的每一筆花費", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onSettings) { Icon(Icons.Rounded.Settings, "設定") }
    }
}

@Composable
private fun FilterTabs(selected: TripFilter, counts: Map<TripFilter, Int>, onSelect: (TripFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        TripFilter.entries.forEach { f ->
            val on = f == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(f) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${f.label} ${counts[f] ?: 0}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyTrips(title: String, firstRun: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(Icons.Rounded.FlightTakeoff, MaterialTheme.colorScheme.primary, size = 72.dp, corner = 24.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (firstRun) "建立第一趟旅程,開始記錄每一筆花費" else "切換上方分頁看看其他旅程",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TripMeta(t: TripSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (t.memberCount > 0) MetaItem(Icons.Rounded.Group, "${t.memberCount} 人")
        t.currencies?.takeIf { it.isNotBlank() }?.let { MetaItem(Icons.Rounded.CurrencyExchange, it.replace(",", " · ")) }
    }
}

@Composable
private fun FeaturedTripCard(t: TripSummary, today: LocalDate, onClick: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(164.dp)) {
            TripCover(t.coverPath, t.name, Modifier.matchParentSize(), iconSize = 140.dp)
            CoverBadge(tripStatus(t.startDate, t.endDate, today), Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtRange(t.startDate, t.endDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TripMeta(t)
            Spacer(Modifier.height(6.dp))
            val budget = t.budget?.takeIf { it > 0 }
            if (budget != null) {
                val f = t.totalHome / budget
                BudgetBar(f)
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("已花費", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtMoney(t.totalHome), style = MaterialTheme.typography.titleLarge)
                }
                if (budget != null) {
                    val f = t.totalHome / budget
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${Math.round(f * 100)}%", style = MaterialTheme.typography.titleSmall, color = budgetColor(f))
                        Text("預算 ${fmtMoney(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTripCard(t: TripSummary, today: LocalDate, onClick: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TripCover(t.coverPath, t.name, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), iconSize = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmtRange(t.startDate, t.endDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TripMeta(t)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
                Text(fmtMoney(t.totalHome), style = MaterialTheme.typography.titleSmall)
                Text(tripStatus(t.startDate, t.endDate, today), style = MaterialTheme.typography.bodySmall, color = ledger.textMuted)
            }
        }
    }
}
