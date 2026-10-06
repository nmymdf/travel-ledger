@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.travelledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.travelledger.data.AppDatabase
import com.example.travelledger.data.HOME_CURRENCY
import com.example.travelledger.data.Trip
import com.example.travelledger.data.TripSummary
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId

private fun fmtDate(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString()
private fun fmtMoney(v: Double) =
    "$HOME_CURRENCY " + NumberFormat.getIntegerInstance().format(Math.round(v))

@Composable
fun AppNav(db: AppDatabase) {
    val nav = rememberNavController()
    val factory = TripViewModelFactory(db.tripDao())
    NavHost(nav, startDestination = "trips") {
        composable("trips") {
            val vm: TripListViewModel = viewModel(factory = factory)
            val trips by vm.trips.collectAsStateWithLifecycle()
            TripListScreen(
                trips,
                onOpen = { nav.navigate("trip/$it") },
                onAdd = { nav.navigate("trip/new") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("trip/new") {
            val vm: TripListViewModel = viewModel(factory = factory)
            TripEditScreen(
                initial = null,
                initialMembers = emptyList(),
                onBack = { nav.popBackStack() },
                onSave = { trip, members -> vm.create(trip, members); nav.popBackStack() },
            )
        }
        composable("trip/{id}/edit", listOf(navArgument("id") { type = NavType.LongType })) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = factory.detail(id))
            val trip by vm.trip.collectAsStateWithLifecycle()
            val members by vm.members.collectAsStateWithLifecycle()
            trip?.let { t ->
                TripEditScreen(
                    initial = t,
                    initialMembers = members.map { m -> m.name },
                    onBack = { nav.popBackStack() },
                    onSave = { updated, names -> vm.update(updated, names); nav.popBackStack() },
                )
            }
        }
        composable("trip/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = factory.detail(id))
            val trip by vm.trip.collectAsStateWithLifecycle()
            TripDetailScreen(trip, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("trip/$id/edit") }, onDelete = {
                vm.delete(); nav.popBackStack()
            })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
fun TripListScreen(
    trips: List<TripSummary>,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("旅帳") },
                actions = { IconButton(onSettings) { Icon(Icons.Default.Settings, "設定") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onAdd) { Icon(Icons.Default.Add, "新增旅程") }
        },
    ) { pad ->
        if (trips.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("還沒有旅程,點右下角新增")
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(trips, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(t.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text("${fmtDate(t.startDate)} ~ ${fmtDate(t.endDate)}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("總花費 ${fmtMoney(t.totalHome)}")
                            t.budget?.let { Text("預算 ${fmtMoney(it)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripEditScreen(
    initial: Trip?,
    initialMembers: List<String>,
    onBack: () -> Unit,
    onSave: (Trip, List<String>) -> Unit,
) {
    val today = LocalDate.now(ZoneId.systemDefault())
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var budget by remember { mutableStateOf(initial?.budget?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var split by remember { mutableStateOf(initial?.splitEnabled ?: false) }
    var membersText by remember { mutableStateOf(initialMembers.joinToString(", ")) }
    var start by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.startDate) } ?: today) }
    var end by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.endDate) } ?: today.plusDays(4)) }
    var picking by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (initial == null) "新增旅程" else "編輯旅程") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("旅程名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton({ picking = true }, Modifier.fillMaxWidth()) { Text("日期:$start ~ $end") }
            OutlinedTextField(
                membersText, { membersText = it },
                label = { Text("旅行成員(以逗號分隔,可留空)") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                budget, { budget = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("預算($HOME_CURRENCY,選填)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("啟用分帳")
                    Text("與朋友旅遊時開啟,可記錄誰付款、誰分攤", style = MaterialTheme.typography.bodySmall)
                }
                Switch(split, { split = it })
            }
            Text("所有總計皆以 $HOME_CURRENCY 計算,每筆支出可自選幣別。", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val trip = (initial ?: Trip(name = "", startDate = 0, endDate = 0)).copy(
                        name = name.trim(), startDate = start.toEpochDay(), endDate = end.toEpochDay(),
                        budget = budget.toDoubleOrNull(), splitEnabled = split,
                    )
                    onSave(trip, membersText.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() })
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (initial == null) "建立旅程" else "儲存變更") }
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = end.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        start = java.time.Instant.ofEpochMilli(s).atZone(ZoneId.of("UTC")).toLocalDate()
                        end = java.time.Instant.ofEpochMilli(e).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    picking = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("取消") } },
        ) { DateRangePicker(state, Modifier.height(500.dp)) }
    }
}

@Composable
fun TripDetailScreen(trip: Trip?, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(trip?.name ?: "") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                TextButton(onEdit) { Text("編輯") }
                TextButton({ confirm = true }) { Text("刪除") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            if (trip != null) {
                Text("${fmtDate(trip.startDate)} ~ ${fmtDate(trip.endDate)}")
                Text(if (trip.splitEnabled) "分帳:開啟" else "分帳:關閉")
                Spacer(Modifier.height(16.dp))
                Text("支出記錄將於階段 2 加入。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("刪除旅程?") },
            text = { Text("此旅程的所有帳目都會被刪除,無法復原。") },
            confirmButton = { TextButton({ confirm = false; onDelete() }) { Text("刪除") } },
            dismissButton = { TextButton({ confirm = false }) { Text("取消") } },
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("設定") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("結算幣別:$HOME_CURRENCY")
            Text("旅帳 0.1.0", style = MaterialTheme.typography.bodySmall)
        }
    }
}
