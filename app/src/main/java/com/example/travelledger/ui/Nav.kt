package com.example.travelledger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.travelledger.data.AppDatabase

private val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

@Composable
fun AppNav(db: AppDatabase) {
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
            TripEditScreen(
                initial = null,
                initialMembers = emptyList(),
                onBack = { nav.popBackStack() },
                onSave = { trip, members -> vm.create(trip, members); nav.popBackStack() },
            )
        }
        composable("trip/{id}/edit", listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = detailFactory(id))
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
        composable("trip/{id}", listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = detailFactory(id))
            TripDetailScreen(
                vm,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("trip/$id/edit") },
                onAddExpense = { nav.navigate("trip/$id/expense/0") },
                onOpenExpense = { eid -> nav.navigate("trip/$id/expense/$eid") },
                onDelete = { vm.delete(); nav.popBackStack() },
            )
        }
        composable("trip/{id}/expense/{eid}", listOf(longArg("id"), longArg("eid"))) {
            val id = it.arguments!!.getLong("id")
            val eid = it.arguments!!.getLong("eid").takeIf { v -> v != 0L }
            val vm: ExpenseEditViewModel = viewModel(
                key = "expense$id/$eid",
                factory = viewModelFactory { initializer { ExpenseEditViewModel(db, id, eid) } },
            )
            ExpenseEditScreen(vm, isNew = eid == null, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onCategories = { nav.navigate("settings/categories") },
                onMethods = { nav.navigate("settings/methods") },
            )
        }
        composable("settings/categories") {
            val vm: LookupViewModel = viewModel(factory = lookupFactory)
            val items by vm.categories.collectAsStateWithLifecycle()
            ManageListScreen(
                title = "分類管理",
                items = items.map { c -> NamedItem(c.id, c.name) },
                protectedName = "其他",
                deleteHint = "使用此分類的支出會改歸「其他」。",
                onBack = { nav.popBackStack() },
                onAdd = vm::addCategory, onRename = vm::renameCategory,
                onDelete = vm::deleteCategory, onMove = vm::moveCategory,
            )
        }
        composable("settings/methods") {
            val vm: LookupViewModel = viewModel(factory = lookupFactory)
            val items by vm.methods.collectAsStateWithLifecycle()
            ManageListScreen(
                title = "付款方式管理",
                items = items.map { m -> NamedItem(m.id, m.name) },
                protectedName = null,
                deleteHint = "使用此付款方式的支出會變成未指定。",
                onBack = { nav.popBackStack() },
                onAdd = vm::addMethod, onRename = vm::renameMethod,
                onDelete = vm::deleteMethod, onMove = vm::moveMethod,
            )
        }
    }
}
