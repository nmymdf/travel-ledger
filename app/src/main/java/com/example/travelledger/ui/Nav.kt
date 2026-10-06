package com.example.travelledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.travelledger.BuildConfig
import com.example.travelledger.data.AppDatabase
import com.example.travelledger.data.Trip
import kotlinx.coroutines.launch

private val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

@Composable
fun AppNav(db: AppDatabase, themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val listFactory = viewModelFactory { initializer { TripListViewModel(db) } }
    val lookupFactory = viewModelFactory { initializer { LookupViewModel(db) } }
    fun detailFactory(id: Long) = viewModelFactory { initializer { TripDetailViewModel(db, id) } }

    NavHost(nav, startDestination = "trips") {
        composable("trips") {
            val vm: TripListViewModel = viewModel(factory = listFactory)
            val trips by vm.trips.collectAsStateWithLifecycle()
            TripListScreen(
                trips,
                onOpen = { nav.navigate("trip/$it") },
                onAdd = { nav.navigate("trip/new") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("trip/new") {
            val vm: TripListViewModel = viewModel(factory = listFactory)
            TripEditRoute(null, emptyList(), onBack = { nav.popBackStack() }) { trip, members ->
                vm.create(trip, members); nav.popBackStack()
            }
        }
        composable("trip/{id}/edit", listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = detailFactory(id))
            val trip by vm.trip.collectAsStateWithLifecycle()
            val members by vm.members.collectAsStateWithLifecycle()
            trip?.let { t ->
                TripEditRoute(t, members.map { m -> m.name }, onBack = { nav.popBackStack() }) { updated, names ->
                    vm.update(updated, names); nav.popBackStack()
                }
            }
        }
        composable("trip/{id}", listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = detailFactory(id))
            val trip by vm.trip.collectAsStateWithLifecycle()
            val members by vm.members.collectAsStateWithLifecycle()
            val expenses by vm.expenses.collectAsStateWithLifecycle()
            val rates by vm.rates.collectAsStateWithLifecycle()
            TripDetailScreen(
                trip, members, expenses, rates,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("trip/$id/edit") },
                onAddExpense = { nav.navigate("trip/$id/expense/0") },
                onOpenExpense = { eid -> nav.navigate("trip/$id/expense/$eid") },
                onDelete = { vm.delete(); nav.popBackStack() },
                onSaveRate = vm::saveRate,
                onDeleteRate = vm::deleteRate,
            )
        }
        composable("trip/{id}/expense/{eid}", listOf(longArg("id"), longArg("eid"))) {
            val id = it.arguments!!.getLong("id")
            val eid = it.arguments!!.getLong("eid").takeIf { v -> v != 0L }
            val vm: ExpenseEditViewModel = viewModel(
                key = "expense$id/$eid",
                factory = viewModelFactory { initializer { ExpenseEditViewModel(db, id, eid) } },
            )
            val categories by vm.categories.collectAsStateWithLifecycle()
            val methods by vm.methods.collectAsStateWithLifecycle()
            var keypadOpen by rememberSaveable { mutableStateOf(eid == null) }
            ExpenseEditScreen(
                vm.state, categories, methods, isNew = eid == null,
                keypadOpen = keypadOpen, onKeypadOpen = { open -> keypadOpen = open },
                onEdit = vm::edit, onKey = vm::key, onCurrency = vm::setCurrency,
                onSave = { vm.save { nav.popBackStack() } },
                onDelete = { vm.delete { nav.popBackStack() } },
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                themeMode, onThemeMode,
                onBack = { nav.popBackStack() },
                onCategories = { nav.navigate("settings/categories") },
                onMethods = { nav.navigate("settings/methods") },
                version = BuildConfig.VERSION_NAME,
            )
        }
        composable("settings/categories") {
            val vm: LookupViewModel = viewModel(factory = lookupFactory)
            val items by vm.categories.collectAsStateWithLifecycle()
            ManageListScreen(
                title = "分類管理",
                items = items.map { c ->
                    val st = categoryStyle(c.name, c.icon, c.color)
                    ManageItem(c.id, c.name, st.icon, st.color, c.icon, c.color)
                },
                styleEditable = true,
                protectedName = "其他",
                deleteHint = "使用此分類的支出會改歸「其他」。",
                onBack = { nav.popBackStack() },
                onAdd = { n, i, c -> vm.addCategory(n, i, c) },
                onUpdate = { id, n, i, c -> vm.updateCategory(id, n, i, c) },
                onDelete = { id -> vm.deleteCategory(id) },
                onMove = { a, b -> vm.moveCategory(a, b) },
            )
        }
        composable("settings/methods") {
            val vm: LookupViewModel = viewModel(factory = lookupFactory)
            val items by vm.methods.collectAsStateWithLifecycle()
            ManageListScreen(
                title = "付款方式管理",
                items = items.map { m -> ManageItem(m.id, m.name, paymentIcon(m.name), Palette[0]) },
                styleEditable = false,
                protectedName = null,
                deleteHint = "使用此付款方式的支出會變成未指定。",
                onBack = { nav.popBackStack() },
                onAdd = { n, _, _ -> vm.addMethod(n) },
                onUpdate = { id, n, _, _ -> vm.renameMethod(id, n) },
                onDelete = { id -> vm.deleteMethod(id) },
                onMove = { a, b -> vm.moveMethod(a, b) },
            )
        }
    }
}

/** Trip editor with the system photo picker for the cover; cleans up unsaved cover files. */
@Composable
private fun TripEditRoute(
    initial: Trip?,
    initialMembers: List<String>,
    onBack: () -> Unit,
    onSave: (Trip, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by rememberSaveable { mutableStateOf(initial?.coverPath) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            CoverStore.import(context, uri)?.let { path ->
                if (cover != initial?.coverPath) CoverStore.delete(cover)
                cover = path
            }
        }
    }
    TripEditScreen(
        initial, initialMembers, cover,
        onPickCover = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onBack = {
            if (cover != initial?.coverPath) CoverStore.delete(cover)
            onBack()
        },
        onSave = { trip, members ->
            if (initial?.coverPath != null && initial.coverPath != cover) CoverStore.delete(initial.coverPath)
            onSave(trip, members)
        },
    )
}
