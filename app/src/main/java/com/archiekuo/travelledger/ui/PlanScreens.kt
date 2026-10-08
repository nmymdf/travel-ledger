@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.archiekuo.travelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.ExpenseRow
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanRow
import com.archiekuo.travelledger.data.PlanStatus
import com.archiekuo.travelledger.data.Reservation
import com.archiekuo.travelledger.data.Trip
import java.time.LocalDate

// ───────────────────────────── Today ─────────────────────────────

@Composable
fun TodayTab(
    trip: Trip,
    plans: List<PlanRow>,
    expenses: List<ExpenseRow>,
    today: LocalDate,
    pad: PaddingValues,
    actions: TripActions,
    onTab: (TripTab) -> Unit,
) {
    val t = today.toEpochDay()
    LazyColumn(
        Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            t < trip.startDate -> beforeTrip(trip, plans, expenses, t, actions, onTab)
            t > trip.endDate -> afterTrip(trip, plans, expenses, onTab, actions)
            else -> onTrip(trip, plans, expenses, t, actions)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.onTrip(
    trip: Trip, plans: List<PlanRow>, expenses: List<ExpenseRow>, t: Long, actions: TripActions,
) {
    val todays = plans.filter { it.date == t }
    val spent = expenses.filter { it.date == t }
    val done = todays.count { it.status == PlanStatus.DONE }
    item {
        LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
            Text("第 ${t - trip.startDate + 1} 天 · ${fmtDayHeader(t)}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("今天已花", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtMoney(spent.sumOf { it.homeAmount }), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (todays.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("行程", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$done / ${todays.size} 完成", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    item { SectionHeader("今天的行程") { SmallAdd("新增") { actions.addPlan(t) } } }
    if (todays.isEmpty()) {
        item { Text("今天還沒有安排,可以從「行程」的待排清單排進來。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        item { PlanCard(todays, trip, showDate = false, withActions = true, actions = actions) }
    }
    val missed = plans.filter { it.date != null && it.date < t && it.date >= trip.startDate && it.status == PlanStatus.TODO }
    if (missed.isNotEmpty()) {
        item {
            SectionHeader("之前沒去的 · ${missed.size} 項") {
                SmallAdd("全部移到今天", Icons.Rounded.Redo) { actions.movePlans(missed.map { it.id }, t) }
            }
        }
        item { PlanCard(missed, trip, showDate = true, withActions = false, actions = actions) }
    }
    item { SectionHeader("今天的支出") }
    if (spent.isEmpty()) {
        item { Text("還沒有支出", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        item {
            LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
                spent.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 66.dp), color = ledger.hairline)
                    ExpenseRowItem(e) { actions.openExpense(e.id) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.beforeTrip(
    trip: Trip, plans: List<PlanRow>, expenses: List<ExpenseRow>, t: Long, actions: TripActions, onTab: (TripTab) -> Unit,
) {
    val needBooking = plans.filter { it.reservation == Reservation.NEEDED && it.status == PlanStatus.TODO }
    val unscheduled = plans.count { it.date == null }
    item {
        LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
            Text("出發倒數", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${trip.startDate - t}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(" 天", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(
                "${fmtShortDate(trip.startDate)} 出發 · 共 ${trip.endDate - trip.startDate + 1} 天",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val prepaid = expenses.sumOf { it.homeAmount }
            if (prepaid > 0) {
                Spacer(Modifier.height(10.dp))
                Text("目前已付 ${fmtMoney(prepaid)}", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    item {
        LedgerCard(Modifier.fillMaxWidth(), onClick = { onTab(TripTab.PLAN) }, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Map, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("行程準備", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (plans.isEmpty()) "還沒有任何行程,把做好的功課放進來" else "已排 ${plans.size - unscheduled} 項 · 待排 $unscheduled 項",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = ledger.textMuted)
            }
        }
    }
    if (needBooking.isNotEmpty()) {
        item { SectionHeader("還沒訂位 · ${needBooking.size} 項") }
        item { PlanCard(needBooking, trip, showDate = true, withActions = false, actions = actions) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.afterTrip(
    trip: Trip, plans: List<PlanRow>, expenses: List<ExpenseRow>, onTab: (TripTab) -> Unit, actions: TripActions,
) {
    val visited = plans.count { it.status == PlanStatus.DONE }
    val missed = plans.filter { it.status != PlanStatus.DONE }
    item {
        LedgerCard(Modifier.fillMaxWidth(), onClick = { onTab(TripTab.STATS) }, padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
            Text("旅程已結束", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(fmtMoney(expenses.sumOf { it.homeAmount }), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "共 ${trip.endDate - trip.startDate + 1} 天 · ${expenses.size} 筆支出" + if (plans.isNotEmpty()) " · 去了 $visited 個地方" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (missed.isNotEmpty()) {
        item { SectionHeader("沒去成的 · ${missed.size} 項(下次的口袋名單)") }
        item { PlanCard(missed, trip, showDate = false, withActions = false, actions = actions) }
    }
}

// ───────────────────────────── Plan ─────────────────────────────

@Composable
fun PlanTab(trip: Trip, plans: List<PlanRow>, pad: PaddingValues, actions: TripActions) {
    var byType by rememberSaveable { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    val needBooking = plans.filter { it.reservation == Reservation.NEEDED && it.status == PlanStatus.TODO }
    LazyColumn(
        Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("依天", "依類型"), if (byType) 1 else 0, Modifier.weight(1f)) { byType = it == 1 }
                IconButton({ pasting = true }) { Icon(Icons.Rounded.ContentPaste, "貼上多筆") }
                Button(
                    { actions.addPlan(null) }, shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
        }
        if (plans.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Rounded.Map, MaterialTheme.colorScheme.primary, size = 64.dp, corner = 20.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("把出發前做的功課放進來", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "想去的景點、要訂位的餐廳都可以先放進「待排」,之後再排到某一天。\n在 Google 地圖按「分享 → 旅帳」也能直接加入。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton({ pasting = true }, shape = RoundedCornerShape(14.dp)) { Text("貼上多筆") }
                        Button({ actions.addPlan(null) }, shape = RoundedCornerShape(14.dp)) { Text("新增行程") }
                    }
                }
            }
            return@LazyColumn
        }
        if (needBooking.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(ledger.danger.copy(alpha = if (ledger.dark) 0.18f else 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.NotificationImportant, null, tint = ledger.danger)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${needBooking.size} 項需要訂位:" + needBooking.joinToString("、") { it.title },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!byType) {
            val unscheduled = plans.filter { it.date == null }
            if (unscheduled.isNotEmpty()) {
                item { SectionHeader("待排 · ${unscheduled.size} 項") }
                item { PlanCard(unscheduled, trip, showDate = false, withActions = false, actions = actions) }
            }
            for (day in trip.startDate..trip.endDate) {
                val rows = plans.filter { it.date == day }
                item(key = "d$day") {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Day ${day - trip.startDate + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("  ${fmtShortDate(day)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        SmallAdd("加入") { actions.addPlan(day) }
                    }
                }
                item(key = "c$day") {
                    if (rows.isEmpty()) {
                        Text("還沒有安排", style = MaterialTheme.typography.bodyMedium, color = ledger.textMuted, modifier = Modifier.padding(start = 4.dp))
                    } else {
                        PlanCard(rows, trip, showDate = false, withActions = false, actions = actions)
                    }
                }
            }
            val outside = plans.filter { it.date != null && (it.date < trip.startDate || it.date > trip.endDate) }
            if (outside.isNotEmpty()) {
                item { SectionHeader("旅程日期以外") }
                item { PlanCard(outside, trip, showDate = true, withActions = false, actions = actions) }
            }
        } else {
            plans.groupBy { it.categoryName ?: "未分類" }.toList().sortedByDescending { it.second.size }.forEach { (name, rows) ->
                val style = categoryStyle(rows.first().categoryName, rows.first().categoryIcon, rows.first().categoryColor)
                item(key = "t$name") {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(style.icon, null, Modifier.size(20.dp), tint = style.color)
                        Spacer(Modifier.width(6.dp))
                        Text("$name · ${rows.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val pending = rows.count { it.reservation == Reservation.NEEDED && it.status == PlanStatus.TODO }
                        if (pending > 0) Text("$pending 項待訂位", style = MaterialTheme.typography.labelMedium, color = ledger.danger)
                    }
                }
                item(key = "l$name") { PlanCard(rows, trip, showDate = true, withActions = false, actions = actions) }
            }
        }
    }
    if (pasting) PasteDialog(onDismiss = { pasting = false }) { text -> actions.pastePlans(text, null); pasting = false }
}

@Composable
private fun SmallAdd(text: String, icon: ImageVector = Icons.Rounded.Add, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanCard(rows: List<PlanRow>, trip: Trip, showDate: Boolean, withActions: Boolean, actions: TripActions) {
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
        rows.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = ledger.hairline)
            PlanRowItem(p, trip, showDate, withActions, actions)
        }
    }
}

@Composable
private fun PlanRowItem(p: PlanRow, trip: Trip, showDate: Boolean, withActions: Boolean, actions: TripActions) {
    val style = categoryStyle(p.categoryName, p.categoryIcon, p.categoryColor)
    val done = p.status == PlanStatus.DONE
    val skipped = p.status == PlanStatus.SKIPPED
    Row(
        Modifier.fillMaxWidth().clickable { actions.openPlan(p.id) }.padding(start = 8.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status: tap toggles done.
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable {
                actions.setPlanStatus(p.id, if (done) PlanStatus.TODO else PlanStatus.DONE)
            },
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Box(Modifier.size(26.dp).clip(CircleShape).background(ledger.success), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "已去", Modifier.size(18.dp), tint = Color.White)
                }
                skipped -> Icon(Icons.Rounded.RemoveCircleOutline, "跳過", Modifier.size(26.dp), tint = ledger.textMuted)
                else -> Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, style.color, CircleShape))
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                p.minuteOfDay?.let {
                    Text(fmtTime(it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(style.icon, null, Modifier.size(18.dp), tint = style.color)
                Spacer(Modifier.width(6.dp))
                Text(
                    p.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (skipped) TextDecoration.LineThrough else null,
                    ),
                    color = if (done || skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val tags = buildList {
                if (showDate) add((p.date?.let { "Day ${it - trip.startDate + 1} ${fmtShortDate(it)}" } ?: "待排") to MaterialTheme.colorScheme.onSurfaceVariant)
                when (p.reservation) {
                    Reservation.NEEDED -> add("需訂位" to ledger.danger)
                    Reservation.BOOKED -> add(("已訂" + p.reservationNote.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()) to ledger.success)
                }
                if (p.spent > 0) add("已花 ${fmtMoney(p.spent)}" to MaterialTheme.colorScheme.primary)
                else p.estCost?.let { add("預估 ${fmtMoney(it)}" to MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (tags.isNotEmpty()) {
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { (text, color) -> Tag(text, color) }
                }
            }
        }
        if (withActions) {
            if (p.location.isNotBlank()) {
                IconButton({ actions.openMap(p) }) { Icon(Icons.Rounded.Directions, "導航", tint = MaterialTheme.colorScheme.primary) }
            }
            IconButton({ actions.addExpense(p.id) }) { Icon(Icons.Rounded.AddCard, "記一筆", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = if (ledger.dark) 0.2f else 0.1f)).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1,
    )
}

@Composable
internal fun PasteDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("貼上多筆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("一行一個地點,會加到「待排」,並自動猜分類。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    placeholder = { Text("一蘭拉麵 本店\n淺草寺\n晴空塔\n敘敘苑 燒肉") },
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = { TextButton({ onAdd(text) }, enabled = text.isNotBlank()) { Text("加入待排") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

// ───────────────────────────── Plan editor ─────────────────────────────

data class PlanActions(
    val edit: ((PlanItem) -> PlanItem) -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
    val back: () -> Unit = {},
    val record: () -> Unit = {},
    val openMap: (PlanItem) -> Unit = {},
)

@Composable
fun PlanEditScreen(item: PlanItem?, trip: Trip?, categories: List<Category>, isNew: Boolean, actions: PlanActions) {
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                if (isNew) "新增行程" else "編輯行程", subtitle = trip?.name, onBack = actions.back, closeIcon = true,
                actions = { ConfirmButton(item?.title?.isNotBlank() == true, actions.save) },
            )
        },
    ) { pad ->
        val p = item ?: return@Scaffold
        val t = trip ?: return@Scaffold
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TitleField(p.title, "景點、餐廳或活動名稱") { v -> actions.edit { it.copy(title = v) } }
            CategoryRow(categories, p.categoryId) { id -> actions.edit { it.copy(categoryId = id) } }

            SectionHeader("哪一天") {
                SmallAdd(p.minuteOfDay?.let { "時間 ${fmtTime(it)}" } ?: "加時間", Icons.Rounded.Schedule) { pickTime = true }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SelectPill("待排", p.date == null, { actions.edit { it.copy(date = null) } }) }
                items((t.startDate..t.endDate).toList()) { d ->
                    SelectPill("D${d - t.startDate + 1} ${fmtShortDate(d)}", p.date == d, { actions.edit { it.copy(date = d) } })
                }
            }

            if (!isNew) {
                SectionHeader("狀態")
                Segmented(listOf("未去", "已去", "跳過"), listOf(PlanStatus.TODO, PlanStatus.DONE, PlanStatus.SKIPPED).indexOf(p.status).coerceAtLeast(0)) { i ->
                    actions.edit { it.copy(status = listOf(PlanStatus.TODO, PlanStatus.DONE, PlanStatus.SKIPPED)[i]) }
                }
            }

            SectionHeader("訂位")
            Segmented(listOf("不用", "需要訂位", "已訂好"), listOf(Reservation.NONE, Reservation.NEEDED, Reservation.BOOKED).indexOf(p.reservation).coerceAtLeast(0)) { i ->
                actions.edit { it.copy(reservation = listOf(Reservation.NONE, Reservation.NEEDED, Reservation.BOOKED)[i]) }
            }
            if (p.reservation != Reservation.NONE) {
                FieldBox("訂位資訊") { FieldInput(p.reservationNote, { v -> actions.edit { it.copy(reservationNote = v) } }, "例如:19:00 · 4 位 · 確認碼 AB123") }
            }

            FieldBox(
                "地點",
                trailing = {
                    IconButton({ actions.openMap(p) }, enabled = p.title.isNotBlank() || p.location.isNotBlank()) {
                        Icon(Icons.Rounded.Map, "在地圖開啟", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            ) { FieldInput(p.location, { v -> actions.edit { it.copy(location = v) } }, "地址或 Google 地圖連結") }

            FieldBox("預估花費(選填)", trailing = { Text("NT$", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                FieldInput(
                    p.estCost?.let { fmtNumber(it) } ?: "",
                    { v -> actions.edit { it.copy(estCost = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull()) } },
                    "不填", keyboardType = KeyboardType.Decimal,
                )
            }

            FieldBox("筆記") {
                val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                BasicTextField(
                    p.note, { v -> actions.edit { it.copy(note = v) } }, Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    textStyle = style, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (p.note.isEmpty()) Text("必點菜色、營業時間、注意事項…", style = style, color = ledger.textMuted)
                        inner()
                    },
                )
            }

            if (!isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(actions.record, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.AddCard, null)
                        Spacer(Modifier.width(6.dp))
                        Text("為這個行程記一筆")
                    }
                    TextButton({ confirmDelete = true }, Modifier.height(50.dp)) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (pickTime) {
            val m = p.minuteOfDay ?: 12 * 60
            val st = rememberTimePickerState(m / 60, m % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = { pickTime = false },
                title = { Text("時間") },
                text = { TimePicker(st) },
                confirmButton = { TextButton({ actions.edit { it.copy(minuteOfDay = st.hour * 60 + st.minute) }; pickTime = false }) { Text("確定") } },
                dismissButton = { TextButton({ actions.edit { it.copy(minuteOfDay = null) }; pickTime = false }) { Text("不指定時間") } },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("刪除「${p.title}」?") },
                text = { Text("已記的支出會保留,只是不再連到這個行程。") },
                confirmButton = { TextButton({ confirmDelete = false; actions.delete() }) { Text("刪除", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
            )
        }
    }
}

/** Large single-line title input used at the top of editors. */
@Composable
fun TitleField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(shape).background(cs.surfaceContainerLowest)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) cs.primary else ledger.hairline, shape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.titleMedium.copy(color = cs.onSurface)
        BasicTextField(
            value, onChange, Modifier.weight(1f), singleLine = true, textStyle = style,
            cursorBrush = SolidColor(cs.primary), interactionSource = interaction,
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = style.copy(fontWeight = FontWeight.Normal), color = ledger.textMuted)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            IconButton({ onChange("") }, Modifier.size(40.dp)) { Icon(Icons.Rounded.Cancel, "清除", Modifier.size(20.dp), tint = ledger.textMuted) }
        }
    }
}
