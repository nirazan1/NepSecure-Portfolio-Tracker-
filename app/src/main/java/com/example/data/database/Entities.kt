package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_holdings")
data class CurrentHolding(
    @PrimaryKey val ticker: String,
    val name: String,
    val shares: Double,
    val avgPrice: Double,
    val currentPrice: Double,
    val marketValue: Double,
    val gainLoss: Double,
    val gainLossPercent: Double
)

@Entity(tableName = "stock_list")
data class StockItem(
    @PrimaryKey val ticker: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val sector: String,
    val volume: Long,
    val open: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val prevClose: Double = 0.0
)

@Entity(tableName = "portfolio_history")
data class PortfolioHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val value: Double
)

@Entity(tableName = "watch_list")
data class WatchStock(
    @PrimaryKey val ticker: String,
    val name: String,
    val targetPrice: Double,
    val currentPrice: Double,
    val notes: String
)
