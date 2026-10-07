package com.archiekuo.travelledger.ui

import android.app.Application
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
import com.archiekuo.travelledger.BuildConfig
import com.archiekuo.travelledger.cover.CoverRepository
import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import com.archiekuo.travelledger.photo.PhotoProcessor
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

@Composable
fun AppNav(db: AppDatabase, settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val listFactory = viewModelFactory { initializer { TripListViewModel(db) } }
    val lookupFactory = viewModelFactory { initializer { LookupViewModel(db) } }
    fun detailFactory(id: Long) = viewModelFactory { initializer { TripDetailViewModel(db, id) } }

    // On a cold start, jump straight into the current trip; the trip list stays underneath.
    var launched by rememberSaveable { mutableStateOf(false) }
    val listVm: TripListViewModel = viewModel(factory = listFactory)
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            listVm.currentTripId(LocalDate.now())?.let { nav.navigate("trip/$it") }
        }
    }

    NavHost(nav, startDestination = "trips") {
        composable("trips") {
            val trips by listVm.trips.collectAsStateWithLifecycle()
            TripListScreen(
                trips,
                onOpen = { nav.navigate("trip/$it") },
                onAdd = { nav.navigate("trip/new") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("trip/new") {
            TripEditRoute(null, emptyList(), emptyList(), onBack = { nav.popBackStack() }) { trip, members, rates ->
                listVm.create(trip, members, rates); nav.popBackStack()
            }
        }
        composable("trip/{id}/edit", listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = detailFactory(id))
            val trip by vm.trip.collectAsStateWithLifecycle()
            val members by vm.members.collectAsStateWithLifecycle()
            val rates by vm.rates.collectAsStateWithLifecycle()
            trip?.let { t ->
                TripEditRoute(t, members.map { m -> m.name }, rates, onBack = { nav.popBackStack() }) { updated, names, r ->
                    vm.update(updated, names, r); nav.popBackStack()
                }
            }
        }
        composable("trip/{id}", listOf(longArg("id"))) {
            val id = it.arguments!!.getLong("id")
            val vm: TripDetailViewModel = viewModel(key = "trip$id", factory = detailFactory(id))
            val trip by vm.trip.collectAsStateWithLifecycle()
            val members by vm.members.collectAsStateWithLifecycle()
            val expenses by vm.expenses.collectAsStateWithLifecycle()
            TripDetailScreen(
                trip, members.size, expenses,
                onSwitchTrip = { if (!nav.popBackStack("trips", inclusive = false)) nav.navigate("trips") },
                onEdit = { nav.navigate("trip/$id/edit") },
                onAddExpense = { nav.navigate("trip/$id/expense/0") },
                onOpenExpense = { eid -> nav.navigate("trip/$id/expense/$eid") },
                onDelete = { vm.delete(); nav.popBackStack("trips", inclusive = false) },
            )
        }
        composable("trip/{id}/expense/{eid}", listOf(longArg("id"), longArg("eid"))) {
            val id = it.arguments!!.getLong("id")
            val eid = it.arguments!!.getLong("eid").takeIf { v -> v != 0L }
            val app = context.applicationContext as Application
            val vm: ExpenseEditViewModel = viewModel(
                key = "expense$id/$eid",
                factory = viewModelFactory { initializer { ExpenseEditViewModel(app, db, id, eid, settings.saveMemoriesToGallery) } },
            )
            ExpenseEditRoute(vm, eid == null, onDone = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                settings, onSettings,
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

/** Expense editor wired to the camera, photo picker and gallery. */
@Composable
private fun ExpenseEditRoute(vm: ExpenseEditViewModel, isNew: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val methods by vm.methods.collectAsStateWithLifecycle()
    var captureFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = captureFile
        if (ok && f != null) vm.addPhoto(android.net.Uri.fromFile(f), cleanup = f) else f?.delete()
        captureFile = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPhoto(uri)
    }
    fun takePhoto() {
        val (file, uri) = PhotoProcessor.newCaptureUri(context)
        captureFile = file
        camera.launch(uri)
    }
    androidx.activity.compose.BackHandler { vm.cancel(); onDone() }
    ExpenseEditScreen(
        state = vm.state, isNew = isNew, categories = categories, methods = methods,
        photos = vm.photos, suggestions = vm.suggestions, currencies = vm.tripCurrencies,
        receipt = vm.receipt, processing = vm.processing,
        actions = ExpenseActions(
            edit = vm::edit, key = vm::key, currency = vm::setCurrency, pickSuggestion = vm::pickSuggestion,
            takePhoto = ::takePhoto,
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            removePhoto = vm::removePhoto, setPhotoType = vm::setPhotoType,
            savePhotoToGallery = { i ->
                vm.savePhotoToGallery(i) { ok ->
                    android.widget.Toast.makeText(context, if (ok) "已存到相簿" else "存檔失敗", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            applyReceipt = vm::applyReceipt, dismissReceipt = vm::dismissReceipt,
            save = { vm.save(onDone) }, delete = { vm.delete(onDone) },
            back = { vm.cancel(); onDone() },
        ),
    )
}

/** Trip editor with cover suggestions and the system photo picker; cleans up unsaved cover files. */
@Composable
private fun TripEditRoute(
    initial: Trip?,
    initialMembers: List<String>,
    initialRates: List<TripCurrencyRate>,
    onBack: () -> Unit,
    onSave: (Trip, List<String>, List<TripCurrencyRate>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by rememberSaveable { mutableStateOf(initial?.coverPath) }
    var results by remember { mutableStateOf<CoverResults>(CoverResults.Idle) }
    var busy by remember { mutableStateOf(false) }
    fun setCover(path: String) {
        if (cover != initial?.coverPath) CoverStore.delete(cover)
        cover = path
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            CoverStore.import(context, uri)?.let(::setCover)
            busy = false
        }
    }
    TripEditScreen(
        initial, initialMembers, initialRates, cover, results, busy,
        onSearchCovers = { k ->
            scope.launch {
                results = CoverResults.Loading
                results = CoverResults.Found(k, CoverRepository.search(k))
            }
        },
        onPickCandidate = { c ->
            scope.launch {
                busy = true
                CoverRepository.download(context, c)?.let(::setCover)
                busy = false
            }
        },
        onPickCover = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onBack = {
            if (cover != initial?.coverPath) CoverStore.delete(cover)
            onBack()
        },
        onSave = { trip, members, rates ->
            if (initial?.coverPath != null && initial.coverPath != cover) CoverStore.delete(initial.coverPath)
            onSave(trip, members, rates)
        },
    )
}
