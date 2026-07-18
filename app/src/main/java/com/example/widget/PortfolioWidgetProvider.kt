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

            if (holdings.isNotEmpty()) {
                var totalVal = 0.0
                var totalCost = 0.0
                var todayDiff = 0.0

                for (h in holdings) {
                    totalVal += h.marketValue
                    totalCost += (h.shares * h.avgPrice)
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

                val holdingsWithChanges = holdings.map { h ->
                    val s = stocksMap[h.ticker.uppercase().trim()]
                    Triple(h, s, s?.changePercent ?: 0.0)
                }

                val sortedByChange = holdingsWithChanges.sortedBy { it.third }

                val topGainer = sortedByChange.lastOrNull()
                val topLoser = sortedByChange.firstOrNull()

                if (topGainer != null) {
                    views.setViewVisibility(R.id.widget_gainer_row, View.VISIBLE)
                    val h = topGainer.first
                    views.setTextViewText(R.id.widget_gainer_ticker, h.ticker)
                    views.setTextViewText(R.id.widget_gainer_price, df.format(h.currentPrice))
                    
                    val changePercent = topGainer.third
                    val gainerText = if (changePercent >= 0) {
                        views.setTextColor(R.id.widget_gainer_change, 0xFF10B981.toInt())
                        "+" + String.format("%.2f%%", changePercent)
                    } else {
                        views.setTextColor(R.id.widget_gainer_change, 0xFFEF4444.toInt())
                        String.format("%.2f%%", changePercent)
                    }
                    views.setTextViewText(R.id.widget_gainer_change, gainerText)
                } else {
                    views.setViewVisibility(R.id.widget_gainer_row, View.GONE)
                }

                if (topLoser != null && sortedByChange.size > 1) {
                    views.setViewVisibility(R.id.widget_loser_row, View.VISIBLE)
                    val h = topLoser.first
                    views.setTextViewText(R.id.widget_loser_ticker, h.ticker)
                    views.setTextViewText(R.id.widget_loser_price, df.format(h.currentPrice))
                    
                    val changePercent = topLoser.third
                    val loserText = if (changePercent >= 0) {
                        views.setTextColor(R.id.widget_loser_change, 0xFF10B981.toInt())
                        "+" + String.format("%.2f%%", changePercent)
                    } else {
                        views.setTextColor(R.id.widget_loser_change, 0xFFEF4444.toInt())
                        String.format("%.2f%%", changePercent)
                    }
                    views.setTextViewText(R.id.widget_loser_change, loserText)
                } else {
                    views.setViewVisibility(R.id.widget_loser_row, View.GONE)
                }
            } else {
                views.setTextColor(R.id.widget_today_value, 0xFF94A3B8.toInt())
                views.setTextViewText(R.id.widget_today_value, "Rs 0.00")
                views.setTextColor(R.id.widget_today_percent, 0xFF94A3B8.toInt())
                views.setTextViewText(R.id.widget_today_percent, "0.00%")
                views.setViewVisibility(R.id.widget_gainer_row, View.GONE)
                views.setViewVisibility(R.id.widget_loser_row, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e("Widget", "Error rendering app widget", e)
        }
    }
}
