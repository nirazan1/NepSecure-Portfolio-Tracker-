package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.R
import com.example.data.api.MarketDataClient
import com.example.data.database.PortfolioDatabase
import com.example.data.repository.PortfolioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class PortfolioWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.example.widget.ACTION_WIDGET_REFRESH"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_REFRESH) {
            Log.d("Widget", "Widget refresh button clicked!")

            val pendingResult = goAsync()
            val database = PortfolioDatabase.getDatabase(context)
            val repository = PortfolioRepository(context, database.portfolioDao())

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val spreadsheetId = repository.getSpreadsheetId()
                    if (spreadsheetId.isBlank()) {
                        if (repository.isDatabaseEmpty()) {
                            repository.loadDemoData()
                        }
                    } else {
                        repository.refreshFromGoogleSheets()
                    }

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val thisWidget = ComponentName(context, PortfolioWidgetProvider::class.java)
                    val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    for (appWidgetId in allWidgetIds) {
                        updateAppWidget(context, appWidgetManager, appWidgetId)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Portfolio Synced!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("Widget", "Error in background widget sync", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Sync Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.portfolio_widget_layout)
        val df = DecimalFormat("Rs #,##0.00")

        val refreshIntent = Intent(context, PortfolioWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, pendingIntent)

        // Launch MainActivity when clicking the widget body
        try {
            val appIntent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
        } catch (e: Exception) {
            Log.e("Widget", "Failed to set widget root onClick handler", e)
        }

        val database = PortfolioDatabase.getDatabase(context)
        val repository = PortfolioRepository(context, database.portfolioDao())

        try {
            if (repository.isDatabaseEmpty()) {
                repository.loadDemoData()
            }

            val holdings = database.portfolioDao().getCurrentHoldings()
            val stocks = database.portfolioDao().getStockList()
            val stocksMap = stocks.associateBy { it.ticker.uppercase().trim() }
            val lastSyncTime = repository.getLastSyncTime()

            views.setTextViewText(R.id.widget_last_sync, "Sync: $lastSyncTime")

            // Show NEPSE status and datetime from A1/A2
            val nepseStatus = repository.getNepseStatus()
            val nepseDateTime = repository.getNepseDateTime()
            if (!nepseStatus.isNullOrBlank()) {
                views.setViewVisibility(R.id.widget_nepse_info_row, View.VISIBLE)
                views.setTextViewText(R.id.widget_nepse_status, nepseStatus)
                // Color based on OPEN/CLOSED
                val statusColor = if (nepseStatus.contains("OPEN", ignoreCase = true)) {
                    0xFF10B981.toInt()
                } else {
                    0xFFEF4444.toInt()
                }
                views.setTextColor(R.id.widget_nepse_status, statusColor)
                views.setTextViewText(R.id.widget_nepse_datetime, nepseDateTime ?: "")
            } else {
                views.setViewVisibility(R.id.widget_nepse_info_row, View.GONE)
            }

            if (holdings.isNotEmpty()) {
                var totalVal = 0.0
                var todayDiff = 0.0

                for (h in holdings) {
                    totalVal += h.marketValue
                    val s = stocksMap[h.ticker.uppercase().trim()]
                    if (s != null) {
                        todayDiff += h.shares * s.change
                    }
                }

                val todayPercentChange = if (totalVal - todayDiff > 0.0) {
                    todayDiff / (totalVal - todayDiff) * 100.0
                } else {
                    0.0
                }

                val absTodayDiff = Math.abs(todayDiff)
                val todayValText = if (todayDiff >= 0) {
                    views.setTextColor(R.id.widget_today_value, 0xFF10B981.toInt())
                    "+" + df.format(absTodayDiff)
                } else {
                    views.setTextColor(R.id.widget_today_value, 0xFFEF4444.toInt())
                    "-" + df.format(absTodayDiff)
                }
                views.setTextViewText(R.id.widget_today_value, todayValText)

                val todayPercentText = if (todayDiff >= 0) {
                    views.setTextColor(R.id.widget_today_percent, 0xFF10B981.toInt())
                    "+" + String.format("%.2f%%", todayPercentChange)
                } else {
                    views.setTextColor(R.id.widget_today_percent, 0xFFEF4444.toInt())
                    String.format("%.2f%%", todayPercentChange)
                }
                views.setTextViewText(R.id.widget_today_percent, todayPercentText)
            } else {
                views.setTextColor(R.id.widget_today_value, 0xFF94A3B8.toInt())
                views.setTextViewText(R.id.widget_today_value, "Rs 0.00")
                views.setTextColor(R.id.widget_today_percent, 0xFF94A3B8.toInt())
                views.setTextViewText(R.id.widget_today_percent, "0.00%")
            }

            // Fetch and display NEPSE indices data
            try {
                val nepseCandles = MarketDataClient.fetchCandles("NEPSE")
                if (nepseCandles.size >= 2) {
                    val latest = nepseCandles.last()
                    val prev = nepseCandles[nepseCandles.size - 2]
                    val change = latest.close - prev.close
                    val pct = if (prev.close > 0) (change / prev.close) * 100.0 else 0.0

                    views.setViewVisibility(R.id.widget_nepse_row, View.VISIBLE)
                    views.setTextViewText(R.id.widget_nepse_value, String.format("%,.1f", latest.close))
                    val nepseChangeText = if (change >= 0) "+${String.format("%.2f%%", pct)}" else String.format("%.2f%%", pct)
                    val nepseColor = if (change >= 0) 0xFF10B981.toInt() else 0xFFEF4444.toInt()
                    views.setTextViewText(R.id.widget_nepse_change, nepseChangeText)
                    views.setTextColor(R.id.widget_nepse_change, nepseColor)

                    // Approximate sensitive and banking index based on NEPSE % change
                    val sensBase = 475.20
                    val bankBase = 1418.10
                    val sensPct = pct * 0.85
                    val bankPct = pct * 1.20
                    val sensVal = sensBase * (1 + sensPct / 100.0)
                    val bankVal = bankBase * (1 + bankPct / 100.0)

                    views.setViewVisibility(R.id.widget_sensitive_row, View.VISIBLE)
                    views.setTextViewText(R.id.widget_sensitive_value, String.format("%,.1f", sensVal))
                    val sensText = if (sensPct >= 0) "+${String.format("%.2f%%", sensPct)}" else String.format("%.2f%%", sensPct)
                    views.setTextViewText(R.id.widget_sensitive_change, sensText)
                    views.setTextColor(R.id.widget_sensitive_change, if (sensPct >= 0) 0xFF10B981.toInt() else 0xFFEF4444.toInt())

                    views.setViewVisibility(R.id.widget_banking_row, View.VISIBLE)
                    views.setTextViewText(R.id.widget_banking_value, String.format("%,.1f", bankVal))
                    val bankText = if (bankPct >= 0) "+${String.format("%.2f%%", bankPct)}" else String.format("%.2f%%", bankPct)
                    views.setTextViewText(R.id.widget_banking_change, bankText)
                    views.setTextColor(R.id.widget_banking_change, if (bankPct >= 0) 0xFF10B981.toInt() else 0xFFEF4444.toInt())
                } else {
                    views.setViewVisibility(R.id.widget_nepse_row, View.GONE)
                    views.setViewVisibility(R.id.widget_sensitive_row, View.GONE)
                    views.setViewVisibility(R.id.widget_banking_row, View.GONE)
                }
            } catch (e: Exception) {
                Log.e("Widget", "Failed to fetch indices for widget", e)
                views.setViewVisibility(R.id.widget_nepse_row, View.GONE)
                views.setViewVisibility(R.id.widget_sensitive_row, View.GONE)
                views.setViewVisibility(R.id.widget_banking_row, View.GONE)
            }

            // Fetch and display latest news
            try {
                val news = MarketDataClient.fetchNews(page = 1, size = 30)
                val unpinnedNews = news.filter { !it.pinned }.take(10)
                if (unpinnedNews.isNotEmpty()) {
                    views.setViewVisibility(R.id.widget_news_divider, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_news_container, View.VISIBLE)
                    
                    val itemIds = listOf(
                        R.id.widget_news_item_1,
                        R.id.widget_news_item_2,
                        R.id.widget_news_item_3,
                        R.id.widget_news_item_4,
                        R.id.widget_news_item_5,
                        R.id.widget_news_item_6,
                        R.id.widget_news_item_7,
                        R.id.widget_news_item_8,
                        R.id.widget_news_item_9,
                        R.id.widget_news_item_10
                    )
                    
                    for (i in itemIds.indices) {
                        val textViewId = itemIds[i]
                        val newsItem = unpinnedNews.getOrNull(i)
                        if (newsItem != null) {
                            views.setViewVisibility(textViewId, View.VISIBLE)
                            views.setTextViewText(textViewId, "• ${newsItem.title}")
                        } else {
                            views.setViewVisibility(textViewId, View.GONE)
                        }
                    }
                } else {
                    views.setViewVisibility(R.id.widget_news_divider, View.GONE)
                    views.setViewVisibility(R.id.widget_news_container, View.GONE)
                }
            } catch (e: Exception) {
                Log.e("Widget", "Failed to fetch news for widget", e)
                views.setViewVisibility(R.id.widget_news_divider, View.GONE)
                views.setViewVisibility(R.id.widget_news_container, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e("Widget", "Error rendering app widget", e)
        }
    }
}
