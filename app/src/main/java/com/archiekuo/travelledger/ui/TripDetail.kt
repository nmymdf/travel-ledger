package com.archiekuo.travelledger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.ExpenseRow
import com.archiekuo.travelledger.data.PlanRow
import com.archiekuo.travelledger.data.HOME_CURRENCY
import com.archiekuo.travelledger.data.Trip
import java.time.LocalDate

enum class TripTab(val label: String) { TODAY("今天"), PLAN("行程"), LEDGER("帳本"), STATS("統計") }

/** Before the trip: plan; during: today; after: stats. */
fun defaultTab(trip: Trip, today: LocalDate): TripTab {
    val t = today.toEpochDay()
    return when {
        t < trip.startDate -> TripTab.PLAN
        t <= trip.endDate -> TripTab.TODAY
        else -> TripTab.STATS
    }
}

/** Everything the trip screen can ask for; defaults keep previews and snapshots short. */
data class TripActions(
    val switchTrip: () -> Unit = {},
    val edit: () -> Unit = {},
    val delete: () -> Unit = {},
    val addExpense: (planId: Long?) -> Unit = {},
    val openExpense: (Long) -> Unit = {},
    val addPlan: (date: Long?) -> Unit = {},
    val openPlan: (Long) -> Unit = {},
    val setPlanStatus: (Long, String) -> Unit = { _, _ -> },
    val movePlans: (List<Long>, Long?) -> Unit = { _, _ -> },
    val pastePlans: (String, Long?) -> Unit = { _, _ -> },
    val openMap: (PlanRow) -> Unit = {},
)

/** The trip shell: switcher header, four tabs and a central "記一筆" button. */
@Composable
fun TripScreen(
    trip: Trip?,
    memberCount: Int,
    expenses: List<ExpenseRow>,
    plans: List<PlanRow>,
    tab: TripTab?,
    onTab: (TripTab) -> Unit,
    actions: TripActions,
    today: LocalDate = LocalDate.now(),
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = tab ?: trip?.let { defaultTab(it, today) } ?: TripTab.LEDGER

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = actions.switchTrip).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trip != null) TripCover(trip.coverPath, trip.name, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), trip.startDate)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(trip?.name ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Rounded.ExpandMore, "切換旅程", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (trip != null) {
                            Text(
                                "${tripStatus(trip.startDate, trip.endDate, today)} · ${fmtShortDate(trip.startDate)} – ${fmtShortDate(trip.endDate)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                    }
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("編輯旅程") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; actions.edit() })
                        DropdownMenuItem(text = { Text("所有旅程") }, leadingIcon = { Icon(Icons.Rounded.Luggage, null) }, onClick = { menu = false; actions.switchTrip() })
                        DropdownMenuItem(
                            text = { Text("刪除旅程", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
        bottomBar = { TripBottomBar(current, onTab) { actions.addExpense(null) } },
    ) { pad ->
        val t = trip ?: return@Scaffold
        val inner = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
        when (current) {
            TripTab.TODAY -> TodayTab(t, plans, expenses, today, inner, actions) { onTab(it) }
            TripTab.PLAN -> PlanTab(t, plans, inner, actions)
            TripTab.LEDGER -> LedgerTab(t, memberCount, expenses, today, inner, actions)
            TripTab.STATS -> StatsTab(t, memberCount, expenses, inner)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("刪除這趟旅程?") },
            text = { Text("旅程內的所有帳目、行程與照片都會一併刪除,無法復原。") },
            confirmButton = { TextButton({ confirmDelete = false; actions.delete() }) { Text("刪除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TripBottomBar(current: TripTab, onTab: (TripTab) -> Unit, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(cs.surfaceContainerLowest)) {
            HorizontalDivider(color = ledger.hairline)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(TripTab.TODAY, TripTab.PLAN).forEach { TabItem(it, it == current, Modifier.weight(1f)) { onTab(it) } }
                Spacer(Modifier.weight(1f))
                listOf(TripTab.LEDGER, TripTab.STATS).forEach { TabItem(it, it == current, Modifier.weight(1f)) { onTab(it) } }
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).navigationBarsPadding().offset(y = (-14).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(60.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(cs.primary).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "記一筆", Modifier.size(30.dp), tint = cs.onPrimary) }
            Text("記一筆", style = MaterialTheme.typography.labelMedium, color = cs.primary)
        }
    }
}

@Composable
private fun TabItem(tab: TripTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val icon = when (tab) {
        TripTab.TODAY -> Icons.Rounded.WbSunny
        TripTab.PLAN -> Icons.Rounded.Map
        TripTab.LEDGER -> Icons.Rounded.ReceiptLong
        TripTab.STATS -> Icons.Rounded.PieChart
    }
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) cs.primaryContainer else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 3.dp),
        ) { Icon(icon, null, tint = if (selected) cs.primary else cs.onSurfaceVariant) }
        Text(tab.label, style = MaterialTheme.typography.labelLarge, color = if (selected) cs.primary else cs.onSurfaceVariant)
    }
}

/** Spending summary, category breakdown (tap to filter) and expenses grouped by day. */
@Composable
private fun LedgerTab(trip: Trip, memberCount: Int, expenses: List<ExpenseRow>, today: LocalDate, pad: PaddingValues, actions: TripActions) {
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    val shown = remember(expenses, filter) { if (filter == null) expenses else expenses.filter { it.categoryId == filter } }
    val days = remember(shown) { shown.groupBy { it.date }.toList() }
    val t = trip
    LazyColumn(
        Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(t, memberCount, expenses, today) }
        if (expenses.isNotEmpty()) item { CategoryCard(expenses, filter) { filter = if (filter == it) null else it } }
        if (expenses.isEmpty()) {
            item { EmptyHint(Icons.Rounded.ReceiptLong, "還沒有支出", "點下方「記一筆」記下第一筆") }
        }
        days.forEach { (date, rows) ->
            item(key = "h$date") { DayHeader(t, date, fmtMoney(rows.sumOf { it.homeAmount })) }
            item(key = "d$date") {
                LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
                    rows.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = ledger.hairline)
                        ExpenseRowItem(e) { actions.openExpense(e.id) }
                    }
                }
            }
        }
    }
}

/** "6月12日 週三  第 3 天 ............ NT$ 1,707" */
@Composable
fun DayHeader(trip: Trip, date: Long, trailing: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(fmtDayHeader(date), style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                date < trip.startDate -> "  出發前"
                date > trip.endDate -> "  旅程後"
                else -> "  第 ${date - trip.startDate + 1} 天"
            },
            style = MaterialTheme.typography.bodySmall, color = ledger.textMuted, modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyHint(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, MaterialTheme.colorScheme.primary, size = 64.dp, corner = 20.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryCard(trip: Trip, memberCount: Int, expenses: List<ExpenseRow>, today: LocalDate) {
    val total = expenses.sumOf { it.homeAmount }
    val length = (trip.endDate - trip.startDate + 1).coerceAtLeast(1)
    val t = today.toEpochDay()
    val elapsed = (t - trip.startDate + 1).coerceIn(1, length)
    val onTrip = t in trip.startDate..trip.endDate
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("總花費(新台幣)", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtMoney(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            trip.budget?.takeIf { it > 0 }?.let { budget ->
                val f = total / budget
                Column(horizontalAlignment = Alignment.End) {
                    Text("${Math.round(f * 100)}%", style = MaterialTheme.typography.titleMedium, color = budgetColor(f))
                    Text("預算 ${fmtMoney(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trip.budget?.takeIf { it > 0 }?.let {
            Spacer(Modifier.height(10.dp))
            BudgetBar(total / it)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            if (onTrip) Stat("今天", fmtPlain(Math.round(expenses.filter { it.date == t }.sumOf { it.homeAmount }).toDouble()), Modifier.weight(1f))
            Stat("日均", fmtPlain(Math.round(total / elapsed).toDouble()), Modifier.weight(1f))
            Stat("人均", if (memberCount > 0) fmtPlain(Math.round(total / memberCount).toDouble()) else "—", Modifier.weight(1f))
            if (!onTrip) Stat("筆數", "${expenses.size}", Modifier.weight(0.7f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

private data class CatTotal(val id: Long?, val name: String, val style: IconStyle, val amount: Double)

/** Stacked share bar plus a two-column legend; tapping a category filters the list below. */
@Composable
private fun CategoryCard(expenses: List<ExpenseRow>, selected: Long?, onSelect: (Long?) -> Unit) {
    val total = expenses.sumOf { it.homeAmount }.takeIf { it > 0 } ?: return
    val cats = remember(expenses) {
        expenses.groupBy { it.categoryId }.map { (id, rows) ->
            val r = rows.first()
            CatTotal(id, r.categoryName ?: "未分類", categoryStyle(r.categoryName, r.categoryIcon, r.categoryColor), rows.sumOf { it.homeAmount })
        }.sortedByDescending { it.amount }
    }
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            cats.forEach { c ->
                Box(
                    Modifier.weight((c.amount / total).toFloat().coerceAtLeast(0.001f)).fillMaxHeight()
                        .background(if (selected == null || selected == c.id) c.style.color else c.style.color.copy(alpha = 0.25f)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        cats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { c ->
                    val on = selected == c.id
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) c.style.color.copy(alpha = if (ledger.dark) 0.2f else 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (on) c.style.color else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onSelect(c.id) }.padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(c.style.icon, c.style.color, size = 30.dp, corner = 9.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                                Text("${Math.round(c.amount / total * 100)}%", style = MaterialTheme.typography.labelMedium, color = c.style.color)
                            }
                            Text(fmtMoney(c.amount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (selected != null) {
            TextButton({ onSelect(null) }, Modifier.align(Alignment.End)) { Text("顯示全部") }
        }
    }
}

@Composable
fun ExpenseRowItem(e: ExpenseRow, onClick: () -> Unit) {
    val style = categoryStyle(e.categoryName, e.categoryIcon, e.categoryColor)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(style.icon, style.color, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title.ifBlank { e.categoryName ?: "未分類" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(e.categoryName ?: "未分類", e.paymentMethodName, e.minuteOfDay?.let { fmtTime(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (e.currency != HOME_CURRENCY) {
                Text(fmtAmount(e.amount, e.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(fmtMoney(e.homeAmount), style = MaterialTheme.typography.titleSmall)
        }
        e.thumbPath?.let { path ->
            Spacer(Modifier.width(10.dp))
            val img by rememberLocalImage(path, targetPx = 160)
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                img?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
            }
        }
    }
}

@Composable
fun CurrencyBadge(code: String, size: androidx.compose.ui.unit.Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
