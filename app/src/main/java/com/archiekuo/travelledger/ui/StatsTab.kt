package com.archiekuo.travelledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.ExpenseRow
import com.archiekuo.travelledger.data.Trip

/** Trip statistics: overview numbers, category donut, spending per day, payment methods. */
@Composable
fun StatsTab(trip: Trip, memberCount: Int, expenses: List<ExpenseRow>, pad: PaddingValues) {
    val total = expenses.sumOf { it.homeAmount }
    LazyColumn(
        Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expenses.isEmpty()) {
            item { EmptyHint(Icons.Rounded.PieChart, "還沒有資料", "記幾筆之後,這裡會出現圖表") }
            return@LazyColumn
        }
        item { Overview(trip, memberCount, expenses, total) }
        item { CategoryDonut(expenses, total) }
        item { DailyBars(trip, expenses) }
        item { PaymentBreakdown(expenses, total) }
    }
}

@Composable
private fun Overview(trip: Trip, memberCount: Int, expenses: List<ExpenseRow>, total: Double) {
    val days = (trip.endDate - trip.startDate + 1).coerceAtLeast(1)
    val biggest = expenses.maxByOrNull { it.homeAmount }
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Text("總花費(新台幣)", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmtMoney(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Row {
            Num("日均", fmtPlain(Math.round(total / days).toDouble()), Modifier.weight(1f))
            Num("人均", if (memberCount > 0) fmtPlain(Math.round(total / memberCount).toDouble()) else "—", Modifier.weight(1f))
            Num("筆數", "${expenses.size}", Modifier.weight(0.7f))
        }
        biggest?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                "最大一筆:${it.title.ifBlank { it.categoryName ?: "" }} ${fmtMoney(it.homeAmount)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun CategoryDonut(expenses: List<ExpenseRow>, total: Double) {
    val cats = remember(expenses) {
        expenses.groupBy { it.categoryId }.map { (_, rows) ->
            val r = rows.first()
            Triple(r.categoryName ?: "未分類", categoryStyle(r.categoryName, r.categoryIcon, r.categoryColor), rows.sumOf { it.homeAmount })
        }.sortedByDescending { it.third }
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Text("分類", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 26.dp.toPx()
                    val arc = Size(size.width - w, size.height - w)
                    val tl = Offset(w / 2, w / 2)
                    drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(w))
                    var start = -90f
                    cats.forEach { (_, st, amt) ->
                        val sweep = (amt / total * 360).toFloat()
                        drawArc(st.color, start, (sweep - 1.2f).coerceAtLeast(0.5f), false, tl, arc, style = Stroke(w))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("總計", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtPlain(Math.round(total).toDouble()), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.take(7).forEach { (name, st, amt) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(st.color))
                        Spacer(Modifier.width(6.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${Math.round(amt / total * 100)}%", style = MaterialTheme.typography.labelLarge, color = st.color)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        cats.forEach { (name, st, amt) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(st.icon, st.color, size = 30.dp, corner = 9.dp)
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(fmtMoney(amt), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun DailyBars(trip: Trip, expenses: List<ExpenseRow>) {
    val days = (trip.startDate..trip.endDate).toList()
    val pre = expenses.filter { it.date < trip.startDate }.sumOf { it.homeAmount }
    val bars = buildList {
        if (pre > 0) add("出發前" to pre)
        days.forEach { d -> add("D${d - trip.startDate + 1}\n${fmtShortDate(d).substringBefore("(")}" to expenses.filter { it.date == d }.sumOf { it.homeAmount }) }
    }
    val max = bars.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Text("每天花多少", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            bars.forEach { (label, v) ->
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (v > 0) fmtPlain(Math.round(v).toDouble()) else "", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(26.dp).height((120 * (v / max)).dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(if (label == "出發前") MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun PaymentBreakdown(expenses: List<ExpenseRow>, total: Double) {
    val methods = expenses.groupBy { it.paymentMethodName ?: "未指定" }.mapValues { e -> e.value.sumOf { it.homeAmount } }
        .toList().sortedByDescending { it.second }
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Text("付款方式", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        methods.forEach { (name, amt) ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(paymentIcon(name), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${fmtMoney(amt)} · ${Math.round(amt / total * 100)}%", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((amt / total).toFloat()).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}
