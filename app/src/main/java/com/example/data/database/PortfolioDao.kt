package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {

    // Current Holdings
    @Query("SELECT * FROM current_holdings")
    fun getCurrentHoldingsFlow(): Flow<List<CurrentHolding>>

    @Query("SELECT * FROM current_holdings")
    suspend fun getCurrentHoldings(): List<CurrentHolding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrentHoldings(holdings: List<CurrentHolding>)

    @Query("DELETE FROM current_holdings")
    suspend fun clearCurrentHoldings()

    // Stock List
    @Query("SELECT * FROM stock_list")
    fun getStockListFlow(): Flow<List<StockItem>>

    @Query("SELECT * FROM stock_list")
    suspend fun getStockList(): List<StockItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockList(stocks: List<StockItem>)

    @Query("DELETE FROM stock_list")
    suspend fun clearStockList()

    // Portfolio History
    @Query("SELECT * FROM portfolio_history ORDER BY id ASC")
    fun getPortfolioHistoryFlow(): Flow<List<PortfolioHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioHistory(history: List<PortfolioHistory>)

    @Query("DELETE FROM portfolio_history")
    suspend fun clearPortfolioHistory()

    // Watch List
    @Query("SELECT * FROM watch_list")
    fun getWatchListFlow(): Flow<List<WatchStock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchList(watchStocks: List<WatchStock>)

    @Query("DELETE FROM watch_list")
    suspend fun clearWatchList()

    // Transaction to replace cached data atomically
    @Transaction
    suspend fun refreshAll(
        holdings: List<CurrentHolding>,
        stocks: List<StockItem>,
        history: List<PortfolioHistory>,
        watchList: List<WatchStock>
    ) {
        clearCurrentHoldings()
        insertCurrentHoldings(holdings)

        clearStockList()
        insertStockList(stocks)

        clearPortfolioHistory()
        insertPortfolioHistory(history)

        clearWatchList()
        insertWatchList(watchList)
    }
}
