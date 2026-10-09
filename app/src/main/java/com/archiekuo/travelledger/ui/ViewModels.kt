package com.archiekuo.travelledger.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.Expense
import com.archiekuo.travelledger.data.ExpenseRow
import com.archiekuo.travelledger.data.HOME_CURRENCY
import com.archiekuo.travelledger.data.Member
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.Photo
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.PlanRow
import com.archiekuo.travelledger.data.PlanStatus
import com.archiekuo.travelledger.data.TitleSuggestion
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import com.archiekuo.travelledger.data.TripSummary
import com.archiekuo.travelledger.logic.ParsedPlace
import com.archiekuo.travelledger.logic.ReceiptGuess
import com.archiekuo.travelledger.photo.PhotoProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

private fun <T> Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

/** Days before departure during which a trip already counts as the current one (pre-paid flights, hotels). */
const val PRE_TRIP_DAYS = 30L

/** The trip to open on launch: ongoing, else the nearest one starting within [PRE_TRIP_DAYS]. */
fun currentTrip(trips: List<Trip>, today: LocalDate): Trip? {
    val t = today.toEpochDay()
    // A trip of our own wins over one a companion shared for the same dates.
    return trips.filter { t in (it.startDate - PRE_TRIP_DAYS)..it.endDate }.minWithOrNull(compareBy({ it.readOnly }, { it.startDate }))
}

class TripListViewModel(private val db: AppDatabase) : ViewModel() {
    val trips: StateFlow<List<TripSummary>> = db.tripDao().observeSummaries().stateIn(this, emptyList())

    fun create(trip: Trip, members: List<String>, rates: List<TripCurrencyRate>, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(db.tripDao().createTrip(trip, members, rates)) }
    }

    suspend fun currentTripId(today: LocalDate, ownOnly: Boolean = false): Long? =
        currentTrip(db.tripDao().getTrips().filter { !ownOnly || !it.readOnly }, today)?.id
}

class TripDetailViewModel(private val db: AppDatabase, private val id: Long) : ViewModel() {
    val trip: StateFlow<Trip?> = db.tripDao().observeTrip(id).stateIn(this, null)
    val members: StateFlow<List<Member>> = db.tripDao().observeMembers(id).stateIn(this, emptyList())
    val expenses: StateFlow<List<ExpenseRow>> = db.expenseDao().observeRows(id).stateIn(this, emptyList())
    val rates: StateFlow<List<TripCurrencyRate>> = db.expenseDao().observeRates(id).stateIn(this, emptyList())
    val plans: StateFlow<List<PlanRow>> = db.planDao().observeRows(id).stateIn(this, emptyList())
    val categories: StateFlow<List<Category>> = db.lookupDao().observeCategories().stateIn(this, emptyList())

    fun setPlanStatus(planId: Long, status: String) = viewModelScope.launch { db.planDao().setStatus(planId, status) }

    fun movePlans(planIds: List<Long>, date: Long?) = viewModelScope.launch { planIds.forEach { db.planDao().setDate(it, date) } }

    /** Pasted lines become unscheduled items (or items on [date]), with a guessed category. */
    fun addParsed(places: List<ParsedPlace>, date: Long?) = viewModelScope.launch {
        val byName = categories.value.associateBy { it.name }
        db.planDao().insertAll(
            places.map { p ->
                PlanItem(tripId = id, title = p.title, location = p.location, date = date, categoryId = p.categoryHint?.let { byName[it]?.id })
            },
        )
    }

    fun update(trip: Trip, memberNames: List<String>, rates: List<TripCurrencyRate>) {
        viewModelScope.launch { db.tripDao().updateTrip(trip, memberNames, rates) }
    }

    fun delete() {
        viewModelScope.launch {
            val cover = db.tripDao().coverPath(id)
            val photos = db.photoDao().pathsForTrip(id)
            db.tripDao().deleteTrip(id)
            CoverStore.delete(cover)
            photos.forEach { PhotoProcessor.delete(it) }
        }
    }
}

data class EditState(
    val title: String = "",
    val amount: String = "",
    val currency: String = HOME_CURRENCY,
    val rate: String = "1",
    val categoryId: Long? = null,
    val paymentId: Long? = null,
    val date: Long = LocalDate.now().toEpochDay(),
    val minuteOfDay: Int? = LocalTime.now().let { it.hour * 60 + it.minute },
    val note: String = "",
    val noteOpen: Boolean = false,
) {
    val amountValue: Double? get() = amount.toDoubleOrNull()?.takeIf { it > 0 }
    val rateValue: Double? get() = rate.toDoubleOrNull()?.takeIf { it > 0 }
    val homeAmount: Double? get() = amountValue?.let { a -> rateValue?.let { toHomeAmount(a, it) } }
    val canSave: Boolean get() = homeAmount != null
}

/** A photo attached to the expense being edited; [id] is null until saved. */
data class PhotoItem(val id: Long?, val path: String, val type: String, val text: String = "")

const val MAX_PHOTOS = 3

class ExpenseEditViewModel(
    private val app: Application,
    private val db: AppDatabase,
    private val tripId: Long,
    private val expenseId: Long?,
    private val saveMemoriesToGallery: Boolean,
    /** Recording money for this itinerary item: prefill from it and tick it off on save. */
    private val planItemId: Long? = null,
) : ViewModel() {
    var state by mutableStateOf<EditState?>(null)
        private set
    val photos = mutableStateListOf<PhotoItem>()
    var receipt by mutableStateOf<ReceiptGuess?>(null)
        private set
    var processing by mutableStateOf(false)
        private set
    var suggestions by mutableStateOf<List<TitleSuggestion>>(emptyList())
        private set
    var tripCurrencies by mutableStateOf(listOf(HOME_CURRENCY))
        private set
    val categories: StateFlow<List<Category>> = db.lookupDao().observeCategories().stateIn(this, emptyList())
    val methods: StateFlow<List<PaymentMethod>> = db.lookupDao().observePaymentMethods().stateIn(this, emptyList())

    private var tripRates: Map<String, Double> = emptyMap()
    private var original: Expense? = null
    private val originalPhotos = mutableListOf<Photo>()
    private var ocrText = ""

    init {
        viewModelScope.launch {
            tripRates = db.expenseDao().getRates(tripId).associate { it.currency to it.rate }
            suggestions = db.expenseDao().titleSuggestions()
            val existing = expenseId?.let { db.expenseDao().get(it) }
            original = existing
            if (existing != null) {
                ocrText = existing.ocrText
                originalPhotos += db.photoDao().forExpense(existing.id)
                photos += originalPhotos.map { PhotoItem(it.id, it.path, it.type) }
                state = EditState(
                    title = existing.title, amount = fmtNumber(existing.amount), currency = existing.currency,
                    rate = fmtNumber(existing.rate), categoryId = existing.categoryId,
                    paymentId = existing.paymentMethodId, date = existing.date, minuteOfDay = existing.minuteOfDay,
                    note = existing.note, noteOpen = existing.note.isNotBlank(),
                )
            } else {
                // New expense: default to the previous expense's currency, category and payment method.
                val last = db.expenseDao().latest(tripId)
                val plan = planItemId?.let { db.planDao().get(it) }
                val currency = last?.currency ?: tripRates.keys.firstOrNull() ?: HOME_CURRENCY
                state = EditState(
                    title = plan?.title ?: "",
                    currency = currency,
                    rate = rateFor(currency, last),
                    categoryId = plan?.categoryId ?: last?.categoryId ?: db.lookupDao().observeCategories().first().firstOrNull()?.id,
                    paymentId = last?.paymentMethodId ?: db.lookupDao().observePaymentMethods().first().firstOrNull()?.id,
                )
            }
            tripCurrencies = (listOf(HOME_CURRENCY) + tripRates.keys + listOfNotNull(state?.currency)).distinct()
        }
    }

    private fun rateFor(currency: String, fallbackFrom: Expense? = null): String = when {
        currency == HOME_CURRENCY -> "1"
        tripRates[currency] != null -> fmtNumber(tripRates.getValue(currency))
        fallbackFrom?.currency == currency -> fmtNumber(fallbackFrom.rate)
        else -> ""
    }

    fun edit(transform: (EditState) -> EditState) {
        state = state?.let(transform)
    }

    fun setCurrency(code: String) {
        edit { it.copy(currency = code, rate = rateFor(code)) }
        if (code !in tripCurrencies) tripCurrencies = tripCurrencies + code
    }

    fun key(k: String) = edit { it.copy(amount = applyKey(it.amount, k)) }

    /** Picking a remembered title also restores the category it was filed under. */
    fun pickSuggestion(s: TitleSuggestion) = edit { it.copy(title = s.title, categoryId = s.categoryId ?: it.categoryId) }

    /** [mode]: RECEIPT reads it, MEMORY just keeps it, null decides from the content (gallery picks). */
    fun addPhoto(uri: Uri, mode: String?, cleanup: File? = null) {
        if (photos.size >= MAX_PHOTOS) return
        processing = true
        viewModelScope.launch {
            runCatching { PhotoProcessor.process(app, uri, state?.currency?.takeIf { it != HOME_CURRENCY }, mode) }
                .onSuccess { p ->
                    photos += PhotoItem(null, p.path, p.type, p.text)
                    if (p.text.isNotBlank()) ocrText = listOf(ocrText, p.text).filter { it.isNotBlank() }.joinToString("\n")
                    if (p.type == PhotoType.RECEIPT && p.guess.hasData) receipt = p.guess
                }
            cleanup?.delete()
            processing = false
        }
    }

    fun removePhoto(index: Int) {
        val p = photos.getOrNull(index) ?: return
        photos.removeAt(index)
        if (p.id == null) PhotoProcessor.delete(p.path) // saved photos are removed on save
    }

    fun setPhotoType(index: Int, type: String) {
        val p = photos.getOrNull(index) ?: return
        photos[index] = p.copy(type = type)
    }

    fun savePhotoToGallery(index: Int, onDone: (Boolean) -> Unit) {
        val p = photos.getOrNull(index) ?: return
        viewModelScope.launch { onDone(PhotoProcessor.saveToGallery(app, Uri.fromFile(File(p.path)))) }
    }

    /** Fills empty or default fields from the recognized receipt. */
    fun applyReceipt() {
        val g = receipt ?: return
        edit { s ->
            var n = s
            g.store?.let { if (s.title.isBlank()) n = n.copy(title = it) }
            g.currency?.let { if (it != s.currency) n = n.copy(currency = it, rate = rateFor(it)) }
            g.total?.let { n = n.copy(amount = fmtNumber(it)) }
            g.date?.let { n = n.copy(date = it.toEpochDay()) }
            g.minuteOfDay?.let { n = n.copy(minuteOfDay = it) }
            n
        }
        g.currency?.let { if (it !in tripCurrencies) tripCurrencies = tripCurrencies + it }
        receipt = null
    }

    fun dismissReceipt() {
        receipt = null
    }

    fun save(onDone: () -> Unit) {
        val s = state ?: return
        val amount = s.amountValue ?: return
        val rate = s.rateValue ?: return
        val home = s.homeAmount ?: return
        viewModelScope.launch {
            val dao = db.expenseDao()
            val base = original ?: Expense(
                tripId = tripId, date = s.date, amount = amount, currency = s.currency, rate = rate,
                homeAmount = home, categoryId = s.categoryId, paymentMethodId = s.paymentId, payerId = null,
            )
            val e = base.copy(
                date = s.date, minuteOfDay = s.minuteOfDay, title = s.title.trim(), amount = amount,
                currency = s.currency, rate = rate, homeAmount = home, categoryId = s.categoryId,
                paymentMethodId = s.paymentId, note = s.note.trim(), ocrText = ocrText,
                planItemId = original?.planItemId ?: planItemId,
            )
            val id = if (original == null) dao.insert(e) else { dao.update(e); e.id }
            if (original == null && planItemId != null) db.planDao().setStatus(planItemId, PlanStatus.DONE)

            val photoDao = db.photoDao()
            val keptIds = photos.mapNotNull { it.id }.toSet()
            originalPhotos.filter { it.id !in keptIds }.forEach { photoDao.delete(it.id); PhotoProcessor.delete(it.path) }
            photos.forEach { p ->
                val path = PhotoProcessor.moveToType(app, p.path, p.type)
                val old = originalPhotos.find { it.id == p.id }
                if (old == null) {
                    var saved = false
                    if (p.type == PhotoType.MEMORY && saveMemoriesToGallery) {
                        saved = PhotoProcessor.saveToGallery(app, Uri.fromFile(File(path)))
                    }
                    photoDao.insert(Photo(expenseId = id, type = p.type, path = path, savedToGallery = saved))
                } else if (old.type != p.type || old.path != path) {
                    photoDao.update(old.copy(type = p.type, path = path))
                }
            }

            // First time a foreign currency is used in this trip: remember its rate as the trip default.
            if (s.currency != HOME_CURRENCY && s.currency !in tripRates) {
                dao.upsertRate(TripCurrencyRate(tripId, s.currency, rate))
            }
            onDone()
        }
    }

    /** Leaving without saving: drop photos taken in this session. */
    fun cancel() {
        photos.filter { it.id == null }.forEach { PhotoProcessor.delete(it.path) }
    }

    fun delete(onDone: () -> Unit) {
        val id = expenseId ?: return
        viewModelScope.launch {
            photos.forEach { PhotoProcessor.delete(it.path) }
            originalPhotos.forEach { PhotoProcessor.delete(it.path) }
            db.expenseDao().delete(id)
            onDone()
        }
    }
}

class LookupViewModel(private val db: AppDatabase) : ViewModel() {
    val categories: StateFlow<List<Category>> = db.lookupDao().observeCategories().stateIn(this, emptyList())
    val methods: StateFlow<List<PaymentMethod>> = db.lookupDao().observePaymentMethods().stateIn(this, emptyList())

    fun addCategory(name: String, icon: String, color: Int) = viewModelScope.launch {
        val order = (categories.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        db.lookupDao().insertCategory(Category(name = name, sortOrder = order, icon = icon, color = color))
    }

    fun updateCategory(id: Long, name: String, icon: String, color: Int) = viewModelScope.launch {
        categories.value.find { it.id == id }?.let {
            db.lookupDao().updateCategories(listOf(it.copy(name = name, icon = icon, color = color)))
        }
    }

    fun deleteCategory(id: Long) = viewModelScope.launch { db.lookupDao().deleteCategory(id) }

    fun moveCategory(from: Int, to: Int) = viewModelScope.launch {
        val list = categories.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return@launch
        list.add(to, list.removeAt(from))
        db.lookupDao().updateCategories(list.mapIndexed { i, c -> c.copy(sortOrder = i) })
    }

    fun addMethod(name: String) = viewModelScope.launch {
        db.lookupDao().insertPaymentMethod(PaymentMethod(name = name, sortOrder = (methods.value.maxOfOrNull { it.sortOrder } ?: -1) + 1))
    }

    fun renameMethod(id: Long, name: String) = viewModelScope.launch {
        methods.value.find { it.id == id }?.let { db.lookupDao().updatePaymentMethods(listOf(it.copy(name = name))) }
    }

    fun deleteMethod(id: Long) = viewModelScope.launch { db.lookupDao().deletePaymentMethod(id) }

    fun moveMethod(from: Int, to: Int) = viewModelScope.launch {
        val list = methods.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return@launch
        list.add(to, list.removeAt(from))
        db.lookupDao().updatePaymentMethods(list.mapIndexed { i, m -> m.copy(sortOrder = i) })
    }
}

/** Editing one itinerary item (new when [planId] is null; may be prefilled from a map share). */
class PlanEditViewModel(
    private val db: AppDatabase,
    private val tripId: Long,
    private val planId: Long?,
    initialDate: Long?,
    shared: ParsedPlace?,
) : ViewModel() {
    var item by mutableStateOf<PlanItem?>(null)
        private set
    var trip by mutableStateOf<Trip?>(null)
        private set
    val categories: StateFlow<List<Category>> = db.lookupDao().observeCategories().stateIn(this, emptyList())

    init {
        viewModelScope.launch {
            trip = db.tripDao().observeTrip(tripId).first()
            item = planId?.let { db.planDao().get(it) } ?: run {
                val cats = db.lookupDao().observeCategories().first()
                PlanItem(
                    tripId = tripId, title = shared?.title ?: "", location = shared?.location ?: "", date = initialDate,
                    categoryId = shared?.categoryHint?.let { h -> cats.find { it.name == h }?.id },
                )
            }
        }
    }

    fun edit(transform: (PlanItem) -> PlanItem) {
        item = item?.let(transform)
    }

    fun save(onDone: () -> Unit) {
        val i = item?.takeIf { it.title.isNotBlank() } ?: return
        viewModelScope.launch {
            val clean = i.copy(title = i.title.trim(), location = i.location.trim(), note = i.note.trim())
            if (planId == null) db.planDao().insert(clean) else db.planDao().update(clean)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = planId ?: return
        viewModelScope.launch { db.planDao().delete(id); onDone() }
    }
}

/** A map place shared into the app, waiting to be added to a trip. */
object SharedPlaceInbox {
    var pending by mutableStateOf<ParsedPlace?>(null)
}
