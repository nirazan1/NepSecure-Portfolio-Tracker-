package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.CurrentHolding
import com.example.data.database.PortfolioDatabase
import com.example.data.database.PortfolioHistory
import com.example.data.database.StockItem
import com.example.data.database.WatchStock
import com.example.data.repository.PortfolioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    object Success : SyncState
    data class Error(val message: String) : SyncState
}

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PortfolioDatabase.getDatabase(application)
    private val repository = PortfolioRepository(application, database.portfolioDao())

    val currentHoldings: StateFlow<List<CurrentHolding>> = repository.currentHoldings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val stockList: StateFlow<List<StockItem>> = repository.stockList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val portfolioHistory: StateFlow<List<PortfolioHistory>> = repository.portfolioHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val watchList: StateFlow<List<WatchStock>> = repository.watchList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _spreadsheetId = MutableStateFlow(repository.getSpreadsheetId())
    val spreadsheetId: StateFlow<String> = _spreadsheetId.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(repository.getLastSyncTime())
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow(repository.getGoogleAccountEmail())
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _e5Value = MutableStateFlow<Double?>(repository.getE5Value())
    val e5Value: StateFlow<Double?> = _e5Value.asStateFlow()

    private val _o5Value = MutableStateFlow<Double?>(repository.getO5Value())
    val o5Value: StateFlow<Double?> = _o5Value.asStateFlow()

    fun updateGoogleAccount(email: String?) {
        _googleAccountEmail.value = email
        repository.saveGoogleAccountEmail(email)
    }

    fun signOutGoogle() {
        _googleAccountEmail.value = null
        repository.saveGoogleAccountEmail(null)
    }

    init {
        viewModelScope.launch {
            if (repository.isDatabaseEmpty()) {
                Log.d("ViewModel", "Database is empty, loading demo data")
                repository.loadDemoData()
                _lastSyncTime.value = repository.getLastSyncTime()
            }
            _e5Value.value = repository.getE5Value()
            _o5Value.value = repository.getO5Value()
        }
    }

    fun updateSpreadsheetId(id: String) {
        _spreadsheetId.value = id
        repository.saveSpreadsheetId(id)
    }

    fun refreshFromSheets() {
        val id = _spreadsheetId.value
        if (id.isBlank()) {
            _syncState.value = SyncState.Error("Google Spreadsheet ID cannot be blank.")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val success = repository.refreshFromGoogleSheets()
                if (success) {
                    _syncState.value = SyncState.Success
                    _lastSyncTime.value = repository.getLastSyncTime()
                    _e5Value.value = repository.getE5Value()
                    _o5Value.value = repository.getO5Value()
                } else {
                    _syncState.value = SyncState.Error("Parsed spreadsheet but found no records. Please check sheet names match exactly: 'Current', 'Stocks', 'PortfolioHistory', 'Watch List'.")
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Sync failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Unknown sync error. Verify sheet sharing and connection.")
            }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun loadDemoData() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                repository.loadDemoData()
                _lastSyncTime.value = repository.getLastSyncTime()
                _e5Value.value = repository.getE5Value()
                _o5Value.value = repository.getO5Value()
                _syncState.value = SyncState.Success
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Failed to load demo data: ${e.message}")
            }
        }
    }
}
