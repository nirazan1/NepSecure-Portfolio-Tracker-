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
    val chatMessages: Flow<List<ChatMessageEntity>> = portfolioDao.getChatMessagesFlow()

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

    fun getNepseStatus(): String? {
        return sharedPrefs.getString("nepse_status", null)
    }

    fun saveNepseStatus(status: String?) {
        if (status == null) sharedPrefs.edit().remove("nepse_status").apply()
        else sharedPrefs.edit().putString("nepse_status", status).apply()
    }

    fun getNepseDateTime(): String? {
        return sharedPrefs.getString("nepse_datetime", null)
    }

    fun saveNepseDateTime(dt: String?) {
        if (dt == null) sharedPrefs.edit().remove("nepse_datetime").apply()
        else sharedPrefs.edit().putString("nepse_datetime", dt).apply()
    }

    // Auto-sync schedule settings
    // activeDays: comma-separated day indices (0=Sun..6=Sat). Default = "1,2,3,4,5" (Mon-Fri)
    fun getAutoSyncDays(): Set<Int> {
        val raw = sharedPrefs.getString("auto_sync_days", "1,2,3,4,5") ?: "1,2,3,4,5"
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun saveAutoSyncDays(days: Set<Int>) {
        sharedPrefs.edit().putString("auto_sync_days", days.sorted().joinToString(",")).apply()
    }

    // startMinutes / endMinutes: minutes since midnight. Default: 10:45=645, 15:00=900
    fun getAutoSyncStartMinutes(): Int = sharedPrefs.getInt("auto_sync_start", 645)
    fun saveAutoSyncStartMinutes(minutes: Int) { sharedPrefs.edit().putInt("auto_sync_start", minutes).apply() }

    fun getAutoSyncEndMinutes(): Int = sharedPrefs.getInt("auto_sync_end", 900)
    fun saveAutoSyncEndMinutes(minutes: Int) { sharedPrefs.edit().putInt("auto_sync_end", minutes).apply() }

    suspend fun isDatabaseEmpty(): Boolean {
        return withContext(Dispatchers.IO) {
            val holdings = portfolioDao.getCurrentHoldings()
            holdings.isEmpty() || holdings.any { it.ticker == "AAPL" || it.ticker == "MSFT" || it.ticker == "GOOGL" }
        }
    }

    suspend fun loadDemoData() {
        withContext(Dispatchers.IO) {
            val demoHoldings = listOf(
                CurrentHolding("NABIL", "Nabil Bank Limited", 200.0, 240.0, 255.50, 51100.0, 3100.0, 6.46),
                CurrentHolding("NICA", "NIC Asia Bank Limited", 150.0, 340.0, 325.20, 48780.0, -2220.0, -4.35),
                CurrentHolding("GBIME", "Global IME Bank Limited", 500.0, 175.0, 182.40, 91200.0, 3700.0, 4.23),
                CurrentHolding("EBL", "Everest Bank Limited", 100.0, 440.0, 455.80, 45580.0, 1580.0, 3.59),
                CurrentHolding("SCB", "Standard Chartered Bank Nepal Limited", 100.0, 500.0, 524.30, 52430.0, 2430.0, 4.86)
            )

            val demoStocks = listOf(
                StockItem("NABIL", "Nabil Bank Limited", 255.50, 3.80, 1.51, "Commercial Banks", 5200000),
                StockItem("NICA", "NIC Asia Bank Limited", 325.20, -1.60, -0.49, "Commercial Banks", 2300000),
                StockItem("GBIME", "Global IME Bank Limited", 182.40, 1.40, 0.77, "Commercial Banks", 2800000),
                StockItem("EBL", "Everest Bank Limited", 455.80, 5.40, 1.20, "Commercial Banks", 3100000),
                StockItem("SCB", "Standard Chartered Bank Nepal Limited", 524.30, 11.80, 2.30, "Commercial Banks", 1200000),
                StockItem("NRIC", "Nepal Reinsurance Company Limited", 650.00, -7.20, -1.10, "Reinsurance", 850000),
                StockItem("CIT", "Citizen Investment Trust", 2200.00, 32.50, 1.50, "Others", 410000),
                StockItem("NLIC", "Nepal Life Insurance Company Limited", 780.00, -3.10, -0.40, "Life Insurance", 1200000),
                StockItem("HIDCL", "Hydroelectricity Investment and Development Company Limited", 170.00, 0.80, 0.47, "Hydro Power", 3800000),
                StockItem("AHPC", "Arun Valley Hydropower Development Company Limited", 210.00, -3.80, -1.78, "Hydro Power", 950000)
            )

            val demoHistory = listOf(
                PortfolioHistory(date = "2026-07-10", value = 275000.0),
                PortfolioHistory(date = "2026-07-11", value = 276500.0),
                PortfolioHistory(date = "2026-07-12", value = 274000.0),
                PortfolioHistory(date = "2026-07-13", value = 279000.0),
                PortfolioHistory(date = "2026-07-14", value = 281500.0),
                PortfolioHistory(date = "2026-07-15", value = 284200.0),
                PortfolioHistory(date = "2026-07-16", value = 289090.0)
            )

            val demoWatch = listOf(
                WatchStock("NRIC", "Nepal Reinsurance Company Limited", 600.0, 650.00, "Wait for bounce support"),
                WatchStock("CIT", "Citizen Investment Trust", 2100.0, 2200.00, "Blue chip long term hold"),
                WatchStock("NLIC", "Nepal Life Insurance Company Limited", 750.0, 780.00, "Watch insurance sector rally")
            )

            portfolioDao.refreshAll(demoHoldings, demoStocks, demoHistory, demoWatch)
            saveE5Value(289090.0)
            saveO5Value(2677.54)
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

                    // Save NEPSE status and datetime from A1, A2
                    saveNepseStatus(GoogleSheetsClient.nepseStatus)
                    saveNepseDateTime(GoogleSheetsClient.nepseDateTime)

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

    fun getGeminiApiKey(): String? {
        return sharedPrefs.getString("gemini_api_key", null)
    }

    fun saveGeminiApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            sharedPrefs.edit().remove("gemini_api_key").apply()
        } else {
            sharedPrefs.edit().putString("gemini_api_key", key.trim()).apply()
        }
    }

    suspend fun insertChatMessage(message: ChatMessageEntity) {
        withContext(Dispatchers.IO) {
            portfolioDao.insertChatMessage(message)
        }
    }

    suspend fun clearChatMessages() {
        withContext(Dispatchers.IO) {
            portfolioDao.clearChatMessages()
        }
    }
}
