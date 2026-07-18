package com.example.data.repository

import android.content.Context
import android.util.Log
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.example.data.api.GoogleSheetsClient
import com.example.data.database.*
import com.example.widget.PortfolioWidgetProvider
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PortfolioRepository(
    private val context: Context,
    private val portfolioDao: PortfolioDao
) {
    val currentHoldings: Flow<List<CurrentHolding>> = portfolioDao.getCurrentHoldingsFlow()
    val stockList: Flow<List<StockItem>> = portfolioDao.getStockListFlow()
    val portfolioHistory: Flow<List<PortfolioHistory>> = portfolioDao.getPortfolioHistoryFlow()
    val watchList: Flow<List<WatchStock>> = portfolioDao.getWatchListFlow()

    private val sharedPrefs = context.getSharedPreferences("portfolio_tracker_prefs", Context.MODE_PRIVATE)

    fun getGoogleAccountEmail(): String? {
        return sharedPrefs.getString("google_account_email", null)
    }

    fun saveGoogleAccountEmail(email: String?) {
        if (email == null) {
            sharedPrefs.edit().remove("google_account_email").apply()
        } else {
            sharedPrefs.edit().putString("google_account_email", email.trim()).apply()
        }
    }

    fun getSpreadsheetId(): String {
        return sharedPrefs.getString("spreadsheet_id", "1mLrFwh9Jbv0WzgaPDemuwKXSyrcMtEOadrA1FR7nDKQ") ?: "1mLrFwh9Jbv0WzgaPDemuwKXSyrcMtEOadrA1FR7nDKQ"
    }

    fun saveSpreadsheetId(id: String) {
        sharedPrefs.edit().putString("spreadsheet_id", id.trim()).apply()
    }

    fun getLastSyncTime(): String {
        return sharedPrefs.getString("last_sync_time", "Never") ?: "Never"
    }

    private fun saveLastSyncTime(time: String) {
        sharedPrefs.edit().putString("last_sync_time", time).apply()
    }

    fun getE5Value(): Double? {
        return if (sharedPrefs.contains("e5_value_str")) {
            sharedPrefs.getString("e5_value_str", null)?.toDoubleOrNull()
        } else null
    }

    fun saveE5Value(value: Double) {
        sharedPrefs.edit().putString("e5_value_str", value.toString()).apply()
    }

    fun getO5Value(): Double? {
        return if (sharedPrefs.contains("o5_value_str")) {
            sharedPrefs.getString("o5_value_str", null)?.toDoubleOrNull()
        } else null
    }

    fun saveO5Value(value: Double) {
        sharedPrefs.edit().putString("o5_value_str", value.toString()).apply()
    }

    suspend fun isDatabaseEmpty(): Boolean {
        return withContext(Dispatchers.IO) {
            portfolioDao.getCurrentHoldings().isEmpty()
        }
    }

    suspend fun loadDemoData() {
        withContext(Dispatchers.IO) {
            val demoHoldings = listOf(
                CurrentHolding("AAPL", "Apple Inc.", 50.0, 170.0, 185.50, 9275.0, 775.0, 9.12),
                CurrentHolding("MSFT", "Microsoft Corp.", 30.0, 320.0, 350.20, 10506.0, 906.0, 9.44),
                CurrentHolding("GOOGL", "Alphabet Inc.", 40.0, 115.0, 130.40, 5216.0, 616.0, 13.39),
                CurrentHolding("AMZN", "Amazon.com Inc.", 25.0, 120.0, 135.80, 3395.0, 395.0, 13.17)
            )

            val demoStocks = listOf(
                StockItem("AAPL", "Apple Inc.", 185.50, 2.50, 1.36, "Technology", 52000000),
                StockItem("MSFT", "Microsoft Corp.", 350.20, 4.10, 1.18, "Technology", 23000000),
                StockItem("GOOGL", "Alphabet Inc.", 130.40, -0.80, -0.61, "Technology", 28000000),
                StockItem("AMZN", "Amazon.com Inc.", 135.80, 1.20, 0.89, "Retail", 31000000),
                StockItem("TSLA", "Tesla Inc.", 245.30, -5.40, -2.15, "Automotive", 85000000),
                StockItem("NVDA", "NVIDIA Corp.", 450.10, 12.30, 2.81, "Semiconductors", 41000000),
                StockItem("NFLX", "Netflix Inc.", 410.50, 3.20, 0.79, "Entertainment", 12000000),
                StockItem("AMD", "Advanced Micro Devices", 112.40, -1.10, -0.97, "Semiconductors", 38000000)
            )

            val demoHistory = listOf(
                PortfolioHistory(date = "2026-07-10", value = 24500.0),
                PortfolioHistory(date = "2026-07-11", value = 24650.0),
                PortfolioHistory(date = "2026-07-12", value = 24400.0),
                PortfolioHistory(date = "2026-07-13", value = 24900.0),
                PortfolioHistory(date = "2026-07-14", value = 25150.0),
                PortfolioHistory(date = "2026-07-15", value = 25420.0),
                PortfolioHistory(date = "2026-07-16", value = 28392.0)
            )

            val demoWatch = listOf(
                WatchStock("TSLA", "Tesla Inc.", 220.0, 245.30, "Wait for correction to buy"),
                WatchStock("NVDA", "NVIDIA Corp.", 420.0, 450.10, "Leader in AI accelerators"),
                WatchStock("AMD", "Advanced Micro Devices", 100.0, 112.40, "Value play in semiconductors")
            )

            portfolioDao.refreshAll(demoHoldings, demoStocks, demoHistory, demoWatch)
            saveE5Value(25700.0)
            saveO5Value(2692.0)
            saveLastSyncTime("Loaded Demo Data")
            updateWidget()
        }
    }

    suspend fun refreshFromGoogleSheets(): Boolean {
        val spreadsheetId = getSpreadsheetId()
        if (spreadsheetId.isBlank()) {
            throw IllegalArgumentException("Spreadsheet ID is blank. Set it in settings.")
        }

        val email = getGoogleAccountEmail()
        var accessToken: String? = null
        if (!email.isNullOrBlank()) {
            try {
                accessToken = withContext(Dispatchers.IO) {
                    val scope = "oauth2:https://www.googleapis.com/auth/spreadsheets.readonly"
                    GoogleAuthUtil.getToken(context, email, scope)
                }
                Log.d("Repository", "Successfully fetched OAuth token for $email")
            } catch (e: Exception) {
                Log.e("Repository", "OAuth Token fetch failed: ${e.message}", e)
                throw Exception("Google account authorization failed: ${e.message}. Please sign in again in Settings.", e)
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d("Repository", "Starting refresh from sheets for ID: $spreadsheetId")
                val holdings = GoogleSheetsClient.fetchCurrentHoldings(spreadsheetId, accessToken)
                val stocks = GoogleSheetsClient.fetchStockList(spreadsheetId, accessToken)
                val history = GoogleSheetsClient.fetchPortfolioHistory(spreadsheetId, accessToken)
                val watch = GoogleSheetsClient.fetchWatchList(spreadsheetId, accessToken)

                if (holdings.isNotEmpty() || stocks.isNotEmpty() || history.isNotEmpty() || watch.isNotEmpty()) {
                    portfolioDao.refreshAll(holdings, stocks, history, watch)
                    
                    // Save E5 and O5 cell values if parsed
                    GoogleSheetsClient.e5Value?.let { saveE5Value(it) }
                    GoogleSheetsClient.o5Value?.let { saveO5Value(it) }

                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    saveLastSyncTime(format.format(Date()))
                    Log.d("Repository", "Sync successful! Saved records.")
                    updateWidget()
                    true
                } else {
                    Log.e("Repository", "Synced sheets but parsed empty data rows")
                    false
                }
            } catch (e: Exception) {
                // If we get an authorization/unauthorized exception and had an active token, clear it so we fetch a fresh one next time
                if (accessToken != null && (e.message?.contains("unauthorized", ignoreCase = true) == true || e.message?.contains("authentication", ignoreCase = true) == true)) {
                    try {
                        GoogleAuthUtil.clearToken(context, accessToken)
                        Log.d("Repository", "Cleared stale OAuth token from Google cache")
                    } catch (ex: Exception) {
                        Log.e("Repository", "Error clearing token", ex)
                    }
                }
                Log.e("Repository", "Failed to refresh from sheets", e)
                throw e
            }
        }
    }

    fun updateWidget() {
        try {
            val intent = Intent(context, PortfolioWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PortfolioWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
            Log.d("Repository", "Triggered widget broadcast with ${ids.size} widget IDs")
        } catch (e: Exception) {
            Log.e("Repository", "Failed to broadcast widget update", e)
        }
    }
}
