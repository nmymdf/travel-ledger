package com.example.travelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.travelledger.data.ExpenseRow
import com.example.travelledger.data.HOME_CURRENCY
import com.example.travelledger.data.Member
import com.example.travelledger.data.Trip
import com.example.travelledger.data.TripCurrencyRate
import java.time.LocalDate

private enum class DetailTab(val label: String) { EXPENSES("支出"), MEMBERS("成員"), RATES("匯率") }

@Composable
fun TripDetailScreen(
    trip: Trip?,
    members: List<Member>,
    expenses: List<ExpenseRow>,
    rates: List<TripCurrencyRate>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onDelete: () -> Unit,
    onSaveRate: (String, Double) -> Unit,
    onDeleteRate: (String) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var tab by rememberSaveable { mutableStateOf(DetailTab.EXPENSES) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rateDialog by remember { mutableStateOf<TripCurrencyRate?>(null) }
    var newRate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppTopBar(
                    trip?.name ?: "", subtitle = trip?.let { fmtRange(it.startDate, it.endDate) }, onBack = onBack,
                    actions = {
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("編輯旅程") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menu = false; onEdit() },
                                )
                                DropdownMenuItem(
                                    text = { Text("刪除旅程", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    },
                )
                DetailTabs(tab) { tab = it }
            }
        },
        floatingActionButton = {
            if (tab == DetailTab.EXPENSES) {
                FloatingActionButton(
                    onAddExpense, shape = CircleShape, modifier = Modifier.size(60.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp, 4.dp, 4.dp, 4.dp),
                ) { Icon(Icons.Rounded.Add, "記一筆", Modifier.size(28.dp)) }
            }
        },
    ) { pad ->
        val t = trip ?: return@Scaffold
        when (tab) {
            DetailTab.EXPENSES -> ExpensesTab(t, members.size, expenses, today, pad, onOpenExpense)
            DetailTab.MEMBERS -> MembersTab(t, members, pad, onEdit)
            DetailTab.RATES -> RatesTab(rates, pad, onEdit = { rateDialog = it }, onAdd = { newRate = true })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("刪除這趟旅程?") },
            text = { Text("旅程內的所有帳目都會一併刪除,無法復原。") },
            confirmButton = {
                TextButton({ confirmDelete = false; onDelete() }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
    if (newRate || rateDialog != null) {
        RateDialog(
            initial = rateDialog,
            onDismiss = { newRate = false; rateDialog = null },
            onSave = { cur, r -> onSaveRate(cur, r); newRate = false; rateDialog = null },
            onDelete = { cur -> onDeleteRate(cur); newRate = false; rateDialog = null },
        )
    }
}

@Composable
private fun DetailTabs(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        DetailTab.entries.forEach { t ->
            val on = t == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(t) }.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t.label, style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.width(28.dp).height(3.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
    HorizontalDivider(color = ledger.hairline)
}

@Composable
private fun ExpensesTab(
    trip: Trip,
    memberCount: Int,
    expenses: List<ExpenseRow>,
    today: LocalDate,
    pad: PaddingValues,
    onOpen: (Long) -> Unit,
) {
    val days = remember(expenses) { expenses.groupBy { it.date }.toList() }
    LazyColumn(
        Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(trip, memberCount, expenses, today) }
        if (expenses.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Rounded.ReceiptLong, MaterialTheme.colorScheme.primary, size = 64.dp, corner = 20.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("還沒有支出", style = MaterialTheme.typography.titleMedium)
                    Text("點右下角 + 記下第一筆", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        days.forEach { (date, rows) ->
            item(key = "h$date") {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDayHeader(date), style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            date < trip.startDate -> "  出發前"
                            date > trip.endDate -> "  旅程後"
                            else -> "  第 ${date - trip.startDate + 1} 天"
                        },
                        style = MaterialTheme.typography.bodySmall, color = ledger.textMuted, modifier = Modifier.weight(1f),
                    )
                    Text(fmtMoney(rows.sumOf { it.homeAmount }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "d$date") {
                LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
                    rows.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = ledger.hairline)
                        ExpenseRowItem(e) { onOpen(e.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(trip: Trip, memberCount: Int, expenses: List<ExpenseRow>, today: LocalDate) {
    val total = expenses.sumOf { it.homeAmount }
    val length = (trip.endDate - trip.startDate + 1).coerceAtLeast(1)
    val elapsed = (today.toEpochDay() - trip.startDate + 1).coerceIn(1, length)
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Text("總花費", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmtMoney(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Row {
            Stat("日均", fmtMoney(total / elapsed), Modifier.weight(1f))
            Stat("人均", if (memberCount > 0) fmtMoney(total / memberCount) else "—", Modifier.weight(1f))
            Stat("筆數", "${expenses.size}", Modifier.weight(0.6f))
        }
        trip.budget?.takeIf { it > 0 }?.let { budget ->
            val f = total / budget
            Spacer(Modifier.height(16.dp))
            BudgetBar(f)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (f <= 1) "預算 ${fmtMoney(budget)} · 剩 ${fmtMoney(budget - total)}"
                    else "預算 ${fmtMoney(budget)} · 超支 ${fmtMoney(total - budget)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("${Math.round(f * 100)}%", style = MaterialTheme.typography.labelLarge, color = budgetColor(f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ExpenseRowItem(e: ExpenseRow, onClick: () -> Unit) {
    val style = categoryStyle(e.categoryName, e.categoryIcon, e.categoryColor)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(style.icon, style.color, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.note.ifBlank { e.categoryName ?: "未分類" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(e.categoryName ?: "未分類", e.paymentMethodName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtMoney(e.homeAmount), style = MaterialTheme.typography.titleSmall)
            if (e.currency != HOME_CURRENCY) {
                Text(fmtAmount(e.amount, e.currency), style = MaterialTheme.typography.bodySmall, color = ledger.textMuted)
            }
        }
    }
}

@Composable
private fun MembersTab(trip: Trip, members: List<Member>, pad: PaddingValues, onEdit: () -> Unit) {
    LazyColumn(
        Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LedgerCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Group, Palette[4])
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (trip.splitEnabled) "分帳已開啟" else "分帳未開啟", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (trip.splitEnabled) "記帳時可選擇付款人與分攤對象" else "所有支出視為全員均攤",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onEdit) { Text("設定") }
                }
            }
        }
        item { SectionHeader("${members.size} 位成員") }
        item {
            if (members.isEmpty()) {
                Text("尚未加入成員,可在「編輯旅程」中新增", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
                    members.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = ledger.hairline)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.name, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(m.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatesTab(rates: List<TripCurrencyRate>, pad: PaddingValues, onEdit: (TripCurrencyRate) -> Unit, onAdd: () -> Unit) {
    LazyColumn(
        Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "旅程預設匯率,新增支出時自動帶入,每筆仍可個別修改。第一次記某種外幣時會自動記下。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rates.isNotEmpty()) {
            item {
                LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
                    rates.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = ledger.hairline)
                        Row(
                            Modifier.fillMaxWidth().clickable { onEdit(r) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CurrencyBadge(r.currency)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(currencyLabel(r.currency), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                Text("1 ${r.currency} = ${fmtRate(r.rate)} NT$", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(fmtRate(r.rate), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        item { AddChip("新增幣別", onAdd) }
    }
}

@Composable
fun CurrencyBadge(code: String) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun RateDialog(
    initial: TripCurrencyRate?,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
    onDelete: (String) -> Unit,
) {
    var currency by remember { mutableStateOf(initial?.currency ?: "") }
    var rate by remember { mutableStateOf(initial?.rate?.let { fmtNumber(it) } ?: "") }
    val rateValue = rate.toDoubleOrNull()?.takeIf { it > 0 }
    val valid = currency.length == 3 && currency != HOME_CURRENCY && rateValue != null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CurrencyExchange, null) },
        title = { Text(if (initial == null) "新增匯率" else "編輯 ${currencyLabel(initial.currency)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initial == null) {
                    OutlinedTextField(
                        currency, { currency = it.uppercase().filter { c -> c.isLetter() }.take(3) },
                        label = { Text("外幣代碼(例如 JPY)") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    )
                }
                OutlinedTextField(
                    rate, { rate = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("1 ${currency.ifEmpty { "外幣" }} = ? 新台幣") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Text("只影響之後新增的支出,已記的支出不變。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onSave(currency, rateValue!!) }, enabled = valid) { Text("儲存") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton({ onDelete(initial.currency) }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                TextButton(onDismiss) { Text("取消") }
            }
        },
    )
}
