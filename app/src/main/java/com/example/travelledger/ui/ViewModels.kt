package com.example.travelledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.travelledger.data.Trip
import com.example.travelledger.data.TripDao
import com.example.travelledger.data.TripSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripListViewModel(private val dao: TripDao) : ViewModel() {
    val trips: StateFlow<List<TripSummary>> =
        dao.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(trip: Trip, members: List<String>) {
        viewModelScope.launch { dao.createTrip(trip, members) }
    }
}

class TripDetailViewModel(private val dao: TripDao, private val id: Long) : ViewModel() {
    val trip: StateFlow<Trip?> =
        dao.observeTrip(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete() {
        viewModelScope.launch { dao.deleteTrip(id) }
    }
}

class TripViewModelFactory(private val dao: TripDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TripListViewModel(dao) as T

    fun detail(id: Long) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TripDetailViewModel(dao, id) as T
    }
}
