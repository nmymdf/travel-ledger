package com.example.travelledger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelledger.data.AppDatabase
import com.example.travelledger.data.Category
import com.example.travelledger.data.Expense
import com.example.travelledger.data.ExpenseRow
import com.example.travelledger.data.HOME_CURRENCY
import com.example.travelledger.data.Member
import com.example.travelledger.data.PaymentMethod
import com.example.travelledger.data.Trip
import com.example.travelledger.data.TripCurrencyRate
import com.example.travelledger.data.TripSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun <T> Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

class TripListViewModel(private val db: AppDatabase) : ViewModel() {
    val trips: StateFlow<List<TripSummary>> = db.tripDao().observeSummaries().stateIn(this, emptyList())

    fun create(trip: Trip, members: List<String>) {
        viewModelScope.launch { db.tripDao().createTrip(trip, members) }
    }
}

class TripDetailViewModel(private val db: AppDatabase, private val id: Long) : ViewModel() {
    val trip: StateFlow<Trip?> = db.tripDao().observeTrip(id).stateIn(this, null)
    val members: StateFlow<List<Member>> = db.tripDao().observeMembers(id).stateIn(this, emptyList())
    val expenses: StateFlow<List<ExpenseRow>> = db.expenseDao().observeRows(id).stateIn(this, emptyList())
    val rates: StateFlow<List<TripCurrencyRate>> = db.expenseDao().observeRates(id).stateIn(this, emptyList())

    fun update(trip: Trip, memberNames: List<String>) {
        viewModelScope.launch { db.tripDao().updateTripWithMembers(trip, memberNames) }
    }

    fun delete() {
        viewModelScope.launch {
            val cover = db.tripDao().coverPath(id)
            db.tripDao().deleteTrip(id)
            CoverStore.delete(cover)
        }
    }

    fun saveRate(currency: String, rate: Double) {
        viewModelScope.launch { db.expenseDao().upsertRate(TripCurrencyRate(id, currency, rate)) }
    }

    fun deleteRate(currency: String) {
        viewModelScope.launch { db.expenseDao().deleteRate(id, currency) }
    }
}

data class EditState(
    val amount: String = "",
    val currency: String = HOME_CURRENCY,
    val rate: String = "1",
    val categoryId: Long? = null,
    val paymentId: Long? = null,
    val date: Long = LocalDate.now().toEpochDay(),
    val note: String = "",
) {
    val amountValue: Double? get() = amount.toDoubleOrNull()?.takeIf { it > 0 }
    val rateValue: Double? get() = rate.toDoubleOrNull()?.takeIf { it > 0 }
    val homeAmount: Double? get() = amountValue?.let { a -> rateValue?.let { toHomeAmount(a, it) } }
    val canSave: Boolean get() = homeAmount != null
}

class ExpenseEditViewModel(
    private val db: AppDatabase,
    private val tripId: Long,
    private val expenseId: Long?,
) : ViewModel() {
    var state by mutableStateOf<EditState?>(null)
        private set
    val categories: StateFlow<List<Category>> = db.lookupDao().observeCategories().stateIn(this, emptyList())
    val methods: StateFlow<List<PaymentMethod>> = db.lookupDao().observePaymentMethods().stateIn(this, emptyList())

    private var tripRates: Map<String, Double> = emptyMap()
    private var original: Expense? = null

    init {
        viewModelScope.launch {
            tripRates = db.expenseDao().getRates(tripId).associate { it.currency to it.rate }
            val existing = expenseId?.let { db.expenseDao().get(it) }
            original = existing
            state = if (existing != null) {
                EditState(
                    amount = fmtNumber(existing.amount), currency = existing.currency,
                    rate = fmtNumber(existing.rate), categoryId = existing.categoryId,
                    paymentId = existing.paymentMethodId, date = existing.date, note = existing.note,
                )
            } else {
                // New expense: default to the previous expense's currency, category and payment method.
                val last = db.expenseDao().latest(tripId)
                val currency = last?.currency ?: HOME_CURRENCY
                EditState(
                    currency = currency,
                    rate = rateFor(currency, last),
                    categoryId = last?.categoryId ?: db.lookupDao().observeCategories().first().firstOrNull()?.id,
                    paymentId = last?.paymentMethodId,
                )
            }
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

    fun setCurrency(code: String) = edit { it.copy(currency = code, rate = rateFor(code)) }

    fun key(k: String) = edit { it.copy(amount = applyKey(it.amount, k)) }

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
                date = s.date, amount = amount, currency = s.currency, rate = rate, homeAmount = home,
                categoryId = s.categoryId, paymentMethodId = s.paymentId, note = s.note.trim(),
            )
            if (original == null) dao.insert(e) else dao.update(e)
            // First time a foreign currency is used in this trip: remember its rate as the trip default.
            if (s.currency != HOME_CURRENCY && dao.getRates(tripId).none { it.currency == s.currency }) {
                dao.upsertRate(TripCurrencyRate(tripId, s.currency, rate))
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = expenseId ?: return
        viewModelScope.launch { db.expenseDao().delete(id); onDone() }
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
