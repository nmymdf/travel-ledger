@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.travelledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.travelledger.data.ExpenseRow
import com.example.travelledger.data.HOME_CURRENCY
import com.example.travelledger.data.Trip
import com.example.travelledger.data.TripCurrencyRate

@Composable
fun TripDetailScreen(
    vm: TripDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var rateDialog by remember { mutableStateOf<TripCurrencyRate?>(null) }
    var newRate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    TextButton(onEdit) { Text("編輯") }
                    TextButton({ confirmDelete = true }) { Text("刪除") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("記一筆")
            }
        },
    ) { pad ->
        val t = trip
        if (t == null) return@Scaffold
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SummaryCard(t, expenses) }
            item {
                RatesSection(
                    rates,
                    onEdit = { rateDialog = it },
                    onAdd = { newRate = true },
                )
            }
            item { Text("支出", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (expenses.isEmpty()) {
                item { Text("還沒有支出,點右下角「記一筆」", style = MaterialTheme.typography.bodySmall) }
            }
            items(expenses, key = { it.id }) { e -> ExpenseItem(e) { onOpenExpense(e.id) } }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除旅程?") },
            text = { Text("此旅程的所有帳目都會被刪除,無法復原。") },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("刪除") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
    if (newRate || rateDialog != null) {
        RateDialog(
            initial = rateDialog,
            onDismiss = { newRate = false; rateDialog = null },
            onSave = { cur, r -> vm.saveRate(cur, r); newRate = false; rateDialog = null },
            onDelete = { cur -> vm.deleteRate(cur); newRate = false; rateDialog = null },
        )
    }
}

@Composable
private fun SummaryCard(trip: Trip, expenses: List<ExpenseRow>) {
    val total = expenses.sumOf { it.homeAmount }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${fmtDate(trip.startDate)} ~ ${fmtDate(trip.endDate)}", style = MaterialTheme.typography.bodySmall)
            Text("總花費", style = MaterialTheme.typography.labelMedium)
            Text(fmtMoney(total), style = MaterialTheme.typography.headlineMedium)
            trip.budget?.takeIf { it > 0 }?.let { budget ->
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (total / budget).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val left = budget - total
                Text(
                    if (left >= 0) "預算 ${fmtMoney(budget)},剩 ${fmtMoney(left)}"
                    else "預算 ${fmtMoney(budget)},超支 ${fmtMoney(-left)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Text(if (trip.splitEnabled) "分帳:開啟" else "分帳:關閉", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RatesSection(rates: List<TripCurrencyRate>, onEdit: (TripCurrencyRate) -> Unit, onAdd: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("預設匯率", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onAdd) { Text("新增") }
        }
        if (rates.isEmpty()) {
            Text("記第一筆外幣支出時會自動記下匯率", style = MaterialTheme.typography.bodySmall)
        }
        rates.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { onEdit(r) }.padding(vertical = 6.dp)) {
                Text("1 ${r.currency}", Modifier.weight(1f))
                Text("= ${fmtNumber(r.rate)} $HOME_CURRENCY")
            }
        }
    }
}

@Composable
private fun ExpenseItem(e: ExpenseRow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(e.categoryName, e.paymentMethodName).joinToString(" · ").ifEmpty { "未分類" })
                val sub = listOf(fmtDate(e.date), e.note).filter { it.isNotBlank() }.joinToString("  ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtMoney(e.homeAmount), style = MaterialTheme.typography.titleMedium)
                if (e.currency != HOME_CURRENCY) {
                    Text(
                        "${fmtAmount(e.amount, e.currency)} @${fmtNumber(e.rate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
        title = { Text(if (initial == null) "新增匯率" else "編輯匯率") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    currency, { currency = it.uppercase().filter { c -> c.isLetter() }.take(3) },
                    label = { Text("外幣代碼(例如 JPY)") }, singleLine = true, enabled = initial == null,
                )
                OutlinedTextField(
                    rate, { rate = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("1 ${currency.ifEmpty { "外幣" }} = ? $HOME_CURRENCY") }, singleLine = true,
                )
                Text("只影響之後新增的支出,已記的支出匯率不變。", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
            }
        },
        confirmButton = { TextButton({ onSave(currency, rateValue!!) }, enabled = valid) { Text("儲存") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton({ onDelete(initial.currency) }) { Text("刪除") }
                TextButton(onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit, onMethods: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("設定") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            ListItem(headlineContent = { Text("分類管理") }, modifier = Modifier.clickable(onClick = onCategories))
            ListItem(headlineContent = { Text("付款方式管理") }, modifier = Modifier.clickable(onClick = onMethods))
            ListItem(
                headlineContent = { Text("結算幣別") },
                supportingContent = { Text("所有總計皆以 $HOME_CURRENCY 計算") },
            )
            ListItem(headlineContent = { Text("旅帳") }, supportingContent = { Text("版本 0.2.0") })
        }
    }
}
