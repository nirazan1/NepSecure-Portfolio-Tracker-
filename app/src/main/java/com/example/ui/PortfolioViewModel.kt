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
import com.example.data.database.ChatMessageEntity
import com.example.data.repository.PortfolioRepository
import com.example.data.api.MarketCandle
import com.example.data.api.MarketDataClient
import com.example.data.api.MarketNewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

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

    private val _nepseStatus = MutableStateFlow<String?>(repository.getNepseStatus())
    val nepseStatus: StateFlow<String?> = _nepseStatus.asStateFlow()

    private val _nepseDateTime = MutableStateFlow<String?>(repository.getNepseDateTime())
    val nepseDateTime: StateFlow<String?> = _nepseDateTime.asStateFlow()

    // Auto-sync schedule settings
    private val _autoSyncDays = MutableStateFlow(repository.getAutoSyncDays())
    val autoSyncDays: StateFlow<Set<Int>> = _autoSyncDays.asStateFlow()

    private val _autoSyncStartMinutes = MutableStateFlow(repository.getAutoSyncStartMinutes())
    val autoSyncStartMinutes: StateFlow<Int> = _autoSyncStartMinutes.asStateFlow()

    private val _autoSyncEndMinutes = MutableStateFlow(repository.getAutoSyncEndMinutes())
    val autoSyncEndMinutes: StateFlow<Int> = _autoSyncEndMinutes.asStateFlow()

    fun updateGoogleAccount(email: String?) {
        _googleAccountEmail.value = email
        repository.saveGoogleAccountEmail(email)
    }

    fun signOutGoogle() {
        _googleAccountEmail.value = null
        repository.saveGoogleAccountEmail(null)
    }

    private val _nepseIndexValue = MutableStateFlow<Double?>(null)
    val nepseIndexValue: StateFlow<Double?> = _nepseIndexValue.asStateFlow()

    private val _nepseIndexChange = MutableStateFlow<Double?>(null)
    val nepseIndexChange: StateFlow<Double?> = _nepseIndexChange.asStateFlow()

    private val _nepseIndexPercent = MutableStateFlow<Double?>(null)
    val nepseIndexPercent: StateFlow<Double?> = _nepseIndexPercent.asStateFlow()

    private val _isNepseLoading = MutableStateFlow(false)
    val isNepseLoading: StateFlow<Boolean> = _isNepseLoading.asStateFlow()

    private val _newsList = MutableStateFlow<List<MarketNewsItem>>(emptyList())
    val newsList: StateFlow<List<MarketNewsItem>> = _newsList.asStateFlow()

    private val _isNewsLoading = MutableStateFlow(false)
    val isNewsLoading: StateFlow<Boolean> = _isNewsLoading.asStateFlow()

    private val _newsError = MutableStateFlow<String?>(null)
    val newsError: StateFlow<String?> = _newsError.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.isDatabaseEmpty()) {
                Log.d("ViewModel", "Database is empty, loading demo data")
                repository.loadDemoData()
                _lastSyncTime.value = repository.getLastSyncTime()
            }
            _e5Value.value = repository.getE5Value()
            _o5Value.value = repository.getO5Value()
            _nepseStatus.value = repository.getNepseStatus()
            _nepseDateTime.value = repository.getNepseDateTime()
            fetchNepseIndex()
            fetchNews()
        }
        startAutoSync()
    }

    // Nepal timezone UTC+5:45
    private val nepalTZ = TimeZone.getTimeZone("Asia/Kathmandu")

    private fun isWithinAutoSyncWindow(): Boolean {
        val cal = Calendar.getInstance(nepalTZ)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=Sun, 1=Mon, ..., 6=Sat
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val activeDays = _autoSyncDays.value
        val start = _autoSyncStartMinutes.value
        val end = _autoSyncEndMinutes.value
        return dayOfWeek in activeDays && minuteOfDay in start until end
    }

    private fun startAutoSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(60_000L) // check every minute
                if (isWithinAutoSyncWindow() && _spreadsheetId.value.isNotBlank()) {
                    try {
                        Log.d("ViewModel", "Auto-sync triggered within trading hours")
                        val success = repository.refreshFromGoogleSheets()
                        if (success) {
                            _lastSyncTime.value = repository.getLastSyncTime()
                            _e5Value.value = repository.getE5Value()
                            _o5Value.value = repository.getO5Value()
                            _nepseStatus.value = repository.getNepseStatus()
                            _nepseDateTime.value = repository.getNepseDateTime()
                        }
                    } catch (e: Exception) {
                        Log.e("ViewModel", "Auto-sync failed", e)
                    }
                }
            }
        }
    }

    fun updateAutoSyncDays(days: Set<Int>) {
        _autoSyncDays.value = days
        repository.saveAutoSyncDays(days)
    }

    fun updateAutoSyncStartMinutes(minutes: Int) {
        _autoSyncStartMinutes.value = minutes
        repository.saveAutoSyncStartMinutes(minutes)
    }

    fun updateAutoSyncEndMinutes(minutes: Int) {
        _autoSyncEndMinutes.value = minutes
        repository.saveAutoSyncEndMinutes(minutes)
    }

    fun fetchNepseIndex() {
        viewModelScope.launch {
            _isNepseLoading.value = true
            try {
                val data = withContext(Dispatchers.IO) {
                    MarketDataClient.fetchCandles("NEPSE")
                }
                if (data.size >= 2) {
                    val latest = data.last()
                    val prev = data[data.size - 2]
                    _nepseIndexValue.value = latest.close
                    val diff = latest.close - prev.close
                    _nepseIndexChange.value = diff
                    _nepseIndexPercent.value = (diff / prev.close) * 100.0
                } else if (data.isNotEmpty()) {
                    _nepseIndexValue.value = data.last().close
                    _nepseIndexChange.value = 0.0
                    _nepseIndexPercent.value = 0.0
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to fetch NEPSE index", e)
            } finally {
                _isNepseLoading.value = false
            }
        }
    }

    fun fetchNews() {
        viewModelScope.launch {
            _isNewsLoading.value = true
            _newsError.value = null
            try {
                val news = withContext(Dispatchers.IO) {
                    MarketDataClient.fetchNews(page = 1, size = 30)
                }
                _newsList.value = news
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to fetch news", e)
                _newsError.value = e.message ?: "Failed to fetch news."
            } finally {
                _isNewsLoading.value = false
            }
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
                    _nepseStatus.value = repository.getNepseStatus()
                    _nepseDateTime.value = repository.getNepseDateTime()
                    fetchNepseIndex()
                    fetchNews()
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
                fetchNepseIndex()
                fetchNews()
                _syncState.value = SyncState.Success
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Failed to load demo data: ${e.message}")
            }
        }
    }

    private val _selectedSymbol = MutableStateFlow<String?>(null)
    val selectedSymbol: StateFlow<String?> = _selectedSymbol.asStateFlow()

    private val _candleData = MutableStateFlow<List<MarketCandle>>(emptyList())
    val candleData: StateFlow<List<MarketCandle>> = _candleData.asStateFlow()

    private val _isCandleLoading = MutableStateFlow(false)
    val isCandleLoading: StateFlow<Boolean> = _isCandleLoading.asStateFlow()

    private val _candleError = MutableStateFlow<String?>(null)
    val candleError: StateFlow<String?> = _candleError.asStateFlow()

    fun selectSymbol(symbol: String?) {
        _selectedSymbol.value = symbol
        if (symbol == null) {
            _candleData.value = emptyList()
            _candleError.value = null
            return
        }

        viewModelScope.launch {
            _isCandleLoading.value = true
            _candleError.value = null
            try {
                val data = withContext(Dispatchers.IO) {
                    MarketDataClient.fetchCandles(symbol)
                }
                if (data.isNotEmpty()) {
                    _candleData.value = data
                } else {
                    _candleError.value = "No historical candle data found for $symbol."
                }
            } catch (e: Exception) {
                _candleError.value = "Failed to load data: ${e.message}"
            } finally {
                _isCandleLoading.value = false
            }
        }
    }

    // --- Gemini AI Assistant Integration ---

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _geminiApiKey = MutableStateFlow(repository.getGeminiApiKey() ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun updateGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        repository.saveGeminiApiKey(key)
    }

    fun getActiveGeminiApiKey(): String {
        val savedKey = repository.getGeminiApiKey()
        if (!savedKey.isNullOrBlank()) {
            return savedKey
        }
        return try {
            com.example.BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatMessages
        .map { entityList ->
            if (entityList.isEmpty()) {
                listOf(
                    ChatMessage(
                        text = "Namaste! 🙏 I am your **NepSecure AI Assistant**, powered by Gemini. I can help you analyze the NEPSE index, evaluate your stock portfolio, review your watchlist, or answer any stock market questions. Select one of the quick analysis options above or ask me anything!",
                        isUser = false
                    )
                )
            } else {
                entityList.map { ChatMessage(it.text, it.isUser, it.timestamp) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(
                ChatMessage(
                    text = "Namaste! 🙏 I am your **NepSecure AI Assistant**, powered by Gemini. I can help you analyze the NEPSE index, evaluate your stock portfolio, review your watchlist, or answer any stock market questions. Select one of the quick analysis options above or ask me anything!",
                    isUser = false
                )
            )
        )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatMessages()
            repository.insertChatMessage(
                ChatMessageEntity(
                    text = "Chat history cleared. How can I assist you today with your NEPSE investments?",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            repository.insertChatMessage(
                ChatMessageEntity(
                    text = text,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
            )
            _isGenerating.value = true
            try {
                val apiKey = getActiveGeminiApiKey()
                val response = com.example.data.api.GeminiClient.generateContent(
                    apiKey = apiKey,
                    systemInstruction = "You are a professional Nepalese stock market adviser and financial expert. Help the user with general questions, stock definitions, technical/fundamental analysis concepts, and Nepalese investment rules. Keep responses concise, clear, and professional. Use formatting/markdown for clean display.",
                    prompt = text
                )
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = response,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = "An error occurred: ${e.localizedMessage ?: "Unknown error"}",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun analyzeNepse() {
        viewModelScope.launch {
            val userText = "Please analyze the NEPSE market index and current trends using the Comprehensive NEPSE Market Analyzer blueprint."
            repository.insertChatMessage(
                ChatMessageEntity(
                    text = userText,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
            )
            _isGenerating.value = true
            try {
                val apiKey = getActiveGeminiApiKey()
                
                // Compile market context
                val indexVal = _nepseIndexValue.value ?: 0.0
                val indexChange = _nepseIndexChange.value ?: 0.0
                val indexPercent = _nepseIndexPercent.value ?: 0.0
                
                val stocksList = stockList.value
                val stockSummary = if (stocksList.isEmpty()) "No active stock listings in database." else {
                    stocksList.take(15).joinToString("\n") { 
                        "- ${it.ticker} (${it.name}): Price: NPR ${it.price}, Change: ${it.changePercent}% [Sector: ${it.sector}]" 
                    }
                }

                val prompt = """
                    **The Comprehensive NEPSE Market Analyzer**
                    
                    *Date Analyzed:* Today (July 18, 2026)
                    *Current NEPSE Index level:* $indexVal
                    *Current Change:* ${if (indexChange >= 0) "+" else ""}$indexChange (${if (indexPercent >= 0) "+" else ""}${String.format("%.2f%%", indexPercent)})
                    
                    *Sample of current NEPSE listings data:*
                    $stockSummary
                    
                    Goal: Provide a comprehensive, data-driven analysis of the NEPSE index as it stands today. Your final report must merge fundamental macroeconomic drivers, structural market analysis, and granular technical charting to deliver an actionable market intelligence report.
                    
                    Phase 1: Macro Context and Sectoral Dynamics
                    Establish environmental factors influencing the Nepalese capital market:
                    1. Macroeconomic Overview (Nepal Context):
                    Analyze the current impact of key economic indicators on the market.
                    Key Metrics: Update on current Bank Interest Rates, Inflation (CPI), Foreign Exchange Reserves, and Nepal Rastra Bank’s (NRB) current monetary stance.
                    2. Sectoral Heatmap & Performance:
                    Synthesize major sectoral movements (Banking, Hydropower, Insurance, Manufacturing, Microfinance, etc.).
                    Analyze which sectors are driving the market and which are lagging. Identify rotation patterns.
                    
                    Phase 2: Fundamental and Flow Analysis
                    Examine the valuation and liquidity driving the index:
                    3. Index Fundamentals:
                    Report the current estimated P/E Ratio and Dividend Yield of the aggregate NEPSE index.
                    Compare these figures against their 3-year and 5-year historical averages to determine if the market is overvalued, undervalued, or fairly valued.
                    4. Liquidity & Volume Profile:
                    Analyze the market's Daily Turnover (Value) trends and institutional interest levels.
                    
                    Phase 3: Structural Technical Analysis
                    Use a multi-timeframe approach to establish structural health:
                    5. Macro Market Structure (Weekly Chart Analysis):
                    Provide analysis of the Weekly NEPSE Index Chart. Identify Support/Demand Zones, Resistance/Supply Zones, and Elliott Wave counts or structural shifts.
                    
                    Phase 4: Tactical Technical Indicators
                    Focus on timing and short-term momentum:
                    6. Momentum and Volatility Analysis (Daily indicators):
                    Incorporate Moving Averages (50-day, 100-day, 200-day SMAs), RSI (14) momentum, MACD signals, and Bollinger Bands.
                    
                    Phase 5: Synthesis and Executive Outlook
                    7. Combined Summary & Future Scenarios:
                    Develop two potential forward-looking scenarios for the next 1–3 months (Scenario A: Bullish Continuation, Scenario B: Bearish Correction). Benchmark NEPSE index in NPR.
                """.trimIndent()

                val systemInstruction = "You are a senior financial analyst specializing in the Nepal Stock Exchange (NEPSE). You have access to real-time or historical data and provide comprehensive, data-driven analysis. Deliver a highly professional, well-formatted financial report with clear markdown headings, bold sections, and robust analysis."
                
                val response = com.example.data.api.GeminiClient.generateContent(
                    apiKey = apiKey,
                    systemInstruction = systemInstruction,
                    prompt = prompt
                )
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = response,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = "Failed to analyze NEPSE: ${e.localizedMessage}",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun analyzePortfolio() {
        viewModelScope.launch {
            val userText = "Please perform an Institutional-Grade Portfolio Audit on my holdings."
            repository.insertChatMessage(
                ChatMessageEntity(
                    text = userText,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
            )
            _isGenerating.value = true
            try {
                val apiKey = getActiveGeminiApiKey()
                
                val holdings = currentHoldings.value
                if (holdings.isEmpty()) {
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            text = "Your portfolio is currently empty. Please load demo data or sync from Sheets to analyze.",
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    return@launch
                }

                val totalCost = holdings.sumOf { it.shares * it.avgPrice }
                val totalValue = holdings.sumOf { it.marketValue }
                val overallGain = totalValue - totalCost
                val overallPercent = if (totalCost > 0) (overallGain / totalCost) * 100.0 else 0.0

                val holdingsSummary = holdings.joinToString("\n") {
                    "- ${it.ticker} (${it.name}): ${it.shares} Shares @ Avg Price NPR ${it.avgPrice}. Current Price: NPR ${it.currentPrice}. Market Value: NPR ${it.marketValue}. Gain/Loss: NPR ${it.gainLoss} (${String.format("%.2f%%", it.gainLossPercent)})"
                }

                val prompt = """
                    **The Institutional-Grade Portfolio Auditor**
                    
                    Goal: Conduct a rigorous, data-driven analysis of my current investment portfolio. Evaluate asset allocation efficiency, calculate risk metrics, pinpoint hidden vulnerabilities, and provide optimized rebalancing recommendations based on modern portfolio theory (MPT).
                    
                    1. Portfolio Inputs & Constraints
                    * Total Portfolio Value: NPR ${String.format("%.2f", totalValue)}
                    * Total Cost Basis: NPR ${String.format("%.2f", totalCost)}
                    * Overall Gain/Loss: NPR ${String.format("%.2f", overallGain)} (${String.format("%.2f%%", overallPercent)})
                    * Current Asset Breakdown:
                    $holdingsSummary
                    
                    * Investment Horizon: Medium-term to Long-term (Nepal Stock Market Context)
                    * Risk Tolerance: Moderate to Aggressive
                    * Primary Objective: Capital appreciation and strategic growth
                    
                    2. Required Analysis Phases
                    
                    Phase 1: Allocation & Sector Diversification Audit
                    - Group holdings into broad asset classes (Equities, Fixed Income, Cash) and calculate exact percentage exposure to each.
                    - Break down the equity portion into sectors (e.g., Hydropower, Commercial Banks, Development Banks, Finance, Microfinance) and market caps where applicable.
                    - Vulnerability Check: Explicitly flag any concentration risk—specifically if any single asset exceeds 10% or any single sector exceeds 25% of the total portfolio.
                    
                    Phase 2: Risk-Adjusted Performance & Correlation Analysis
                    - Estimate or fetch the approximate Beta of this portfolio relative to its primary benchmark index: NEPSE Index. Is my portfolio more or less volatile than the broader market?
                    - Identify highly correlated assets within holdings. Point out instances where different stocks move in lockstep, creating a false sense of diversification. Pay close attention to Hydropower and Commercial Banks which heavily dictate local market movements.
                    - Evaluate the portfolio's cash-flow health: Calculate the weighted dividend yield or interest generation of the aggregate portfolio.
                    
                    Phase 3: Stress Testing & Scenario Analysis
                    Simulate how this exact portfolio breakdown would likely perform under the following macro scenarios:
                    - Scenario 1: High-Inflation / Sticky Interest Rate Environment (Nepal Context): How do specific assets hold up?
                    - Scenario 2: Market-Wide Correction / Liquidity Crunch in NEPSE: Which assets will act as my shock absorbers, and which will experience the steepest drawdowns?
                    
                    Phase 4: Actionable Rebalancing & Optimization Strategy
                    - Provide a clear Before vs. After target allocation matrix.
                    - Give specific, step-by-step instructions on what to trim (overvalued or over-allocated sectors) and what to accumulate (undervalued or defensive assets) to align the portfolio with stated risk tolerance and time horizon.
                    - List 3 immediate, concrete risk-mitigation rules tailored to current setup.
                    
                    3. Formatting Output Requirements
                    - Present all allocation breakdowns and Before/After comparisons in clean, scannable Markdown Tables.
                    - Use bold headings and bullet points for all vulnerability alerts so they stand out immediately.
                    - Keep explanations highly practical, avoiding overly dense academic jargon.
                    - Benchmark the NEPSE Index and currency as NPR.
                """.trimIndent()

                val systemInstruction = "You are a senior portfolio manager, quantitative risk analyst, and asset allocation expert specializing in the Nepalese market. Provide highly institutional-grade, data-driven audits of portfolios with clean markdown tables and precise metrics."

                val response = com.example.data.api.GeminiClient.generateContent(
                    apiKey = apiKey,
                    systemInstruction = systemInstruction,
                    prompt = prompt
                )
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = response,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = "Failed to analyze portfolio: ${e.localizedMessage}",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun analyzeWatchlist() {
        viewModelScope.launch {
            val userText = "Analyze my watchlist and target achievements."
            repository.insertChatMessage(
                ChatMessageEntity(
                    text = userText,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
            )
            _isGenerating.value = true
            try {
                val apiKey = getActiveGeminiApiKey()
                
                val watchlist = watchList.value
                if (watchlist.isEmpty()) {
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            text = "Your watchlist is empty. Please add stocks to your watchlist to perform strategy analysis.",
                            isUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    return@launch
                }

                val watchlistSummary = watchlist.joinToString("\n") {
                    val gap = if (it.currentPrice > 0) ((it.targetPrice - it.currentPrice) / it.currentPrice * 100) else 0.0
                    val gapStr = if (gap >= 0) "At target/Reached (+${String.format("%.1f%%", gap)})" else "${String.format("%.1f%%", gap)} to target"
                    "- ${it.ticker} (${it.name}): Current Price: Rs ${it.currentPrice}, Target Price: Rs ${it.targetPrice} ($gapStr). Notes: ${it.notes}"
                }

                val prompt = """
                    Here is my watchlist with current market prices and target buy/sell prices:
                    $watchlistSummary
                    
                    Please provide:
                    1. **Trigger Alert & Opportunity Analysis**: Which targets are closest or already reached?
                    2. **Strategic Watchlist Recommendation**: How to trade these tickers, potential entry windows, and risk setups.
                    3. **Psychology of Targets**: Advise on setting realistic targets and sticking to trading plans.
                """.trimIndent()

                val systemInstruction = "You are a technical analyst and trading coach. Guide the user through watchlist opportunities and tactical triggers with high-energy, helpful advice."

                val response = com.example.data.api.GeminiClient.generateContent(
                    apiKey = apiKey,
                    systemInstruction = systemInstruction,
                    prompt = prompt
                )
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = response,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                repository.insertChatMessage(
                    ChatMessageEntity(
                        text = "Failed to analyze watchlist: ${e.localizedMessage}",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }
}
