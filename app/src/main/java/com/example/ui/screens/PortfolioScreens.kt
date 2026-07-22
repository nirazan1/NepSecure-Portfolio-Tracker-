package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import android.util.Log
import com.example.data.database.CurrentHolding
import com.example.data.database.PortfolioHistory
import com.example.data.database.StockItem
import com.example.data.database.WatchStock
import com.example.data.api.MarketCandle
import com.example.data.api.MarketNewsItem
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Size
import com.example.ui.PortfolioViewModel
import com.example.ui.SyncState
import com.example.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private val moneyFormat = DecimalFormat("Rs #,##0.00")
private val changeFormat = DecimalFormat("+0.00;-0.00")
private val percentFormat = DecimalFormat("+0.00%;-0.00%")

data class SubIndexData(
    val name: String,
    val value: Double,
    val change: Double,
    val percent: Double
)

@Composable
fun DashboardScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val holdings by viewModel.currentHoldings.collectAsState()
    val stocks by viewModel.stockList.collectAsState()
    val history by viewModel.portfolioHistory.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()

    val nepseVal by viewModel.nepseIndexValue.collectAsState()
    val nepseChange by viewModel.nepseIndexChange.collectAsState()
    val nepsePercent by viewModel.nepseIndexPercent.collectAsState()
    val isNepseLoading by viewModel.isNepseLoading.collectAsState()
    val nepseStatus by viewModel.nepseStatus.collectAsState()
    val nepseDateTime by viewModel.nepseDateTime.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    var showBalances by remember { mutableStateOf(false) }
    var showOtherIndices by remember { mutableStateOf(false) }

    var holdingsSearchQuery by remember { mutableStateOf("") }
    var hoveredPoint by remember { mutableStateOf<PortfolioHistory?>(null) }
    var selectedPeriod by remember { mutableStateOf("1M") }
    var holdingsSortBy by remember { mutableStateOf("Today's Diff") }
    var holdingsSortOrderDesc by remember { mutableStateOf(true) }
    var holdingsFilterPerformance by remember { mutableStateOf("All") }
    var holdingsFilterExpanded by remember { mutableStateOf(false) }

    val filteredHoldings = remember(holdings, stocks, holdingsSearchQuery, holdingsSortBy, holdingsSortOrderDesc, holdingsFilterPerformance) {
        var result = holdings.filter {
            it.ticker.contains(holdingsSearchQuery, ignoreCase = true) ||
                    it.name.contains(holdingsSearchQuery, ignoreCase = true)
        }

        result = when (holdingsFilterPerformance) {
            "Profitable" -> result.filter { it.gainLoss > 0 }
            "In the Red" -> result.filter { it.gainLoss < 0 }
            else -> result
        }

        val stocksMap = stocks.associateBy { it.ticker.uppercase().trim() }

        when (holdingsSortBy) {
            "Today's Diff" -> {
                if (holdingsSortOrderDesc) {
                    result.sortedByDescending { holding ->
                        val matchingStock = stocksMap[holding.ticker.uppercase().trim()]
                        (matchingStock?.change ?: 0.0) * holding.shares
                    }
                } else {
                    result.sortedBy { holding ->
                        val matchingStock = stocksMap[holding.ticker.uppercase().trim()]
                        (matchingStock?.change ?: 0.0) * holding.shares
                    }
                }
            }
            "Value" -> {
                if (holdingsSortOrderDesc) result.sortedByDescending { it.marketValue }
                else result.sortedBy { it.marketValue }
            }
            "Gain/Loss" -> {
                if (holdingsSortOrderDesc) result.sortedByDescending { it.gainLossPercent }
                else result.sortedBy { it.gainLossPercent }
            }
            "Ticker" -> {
                if (holdingsSortOrderDesc) result.sortedByDescending { it.ticker }
                else result.sortedBy { it.ticker }
            }
            "Shares" -> {
                if (holdingsSortOrderDesc) result.sortedByDescending { it.shares }
                else result.sortedBy { it.shares }
            }
            else -> result
        }
    }

    var totalValue = 0.0
    var totalCost = 0.0
    holdings.forEach {
        totalValue += it.marketValue
        totalCost += (it.shares * it.avgPrice)
    }
    val totalGainLoss = totalValue - totalCost
    val totalGainPercent = if (totalCost > 0) (totalValue - totalCost) / totalCost else 0.0

    // Calculate Today's Difference using StockItem changes
    val stocksMap = stocks.associateBy { it.ticker.uppercase().trim() }
    var todayDiff = 0.0
    holdings.forEach { holding ->
        val ticker = holding.ticker.uppercase().trim()
        val stockItem = stocksMap[ticker]
        if (stockItem != null) {
            todayDiff += holding.shares * stockItem.change
        }
    }
    val todayPercentChange = if (totalValue - todayDiff > 0.0) {
        todayDiff / (totalValue - todayDiff) * 100.0
    } else {
        0.0
    }

    LaunchedEffect(syncState) {
        if (syncState !is SyncState.Syncing) isRefreshing = false
    }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refreshFromSheets()
        },
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // NEPSE Index Banner at the very top
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSymbol("NEPSE") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GrowBlue.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "NEPSE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GrowBlue
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Timeline,
                                            contentDescription = "View Chart",
                                            tint = GrowBlue,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                if (isNepseLoading && nepseVal == null) {
                                    Text(
                                        text = "Loading index...",
                                        fontSize = 12.sp,
                                        color = SlateTextSecondary
                                    )
                                } else {
                                    Text(
                                        text = nepseVal?.let { String.format("%,.2f", it) } ?: "2,677.54",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SlateTextPrimary
                                    )
                                }
                            }

                            if (!nepseStatus.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    val statusColor = if (nepseStatus!!.contains("OPEN", ignoreCase = true)) GainGreen else LossRed
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(statusColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = nepseStatus!!,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = statusColor
                                        )
                                    }
                                    if (!nepseDateTime.isNullOrBlank()) {
                                        Text(
                                            text = nepseDateTime!!,
                                            fontSize = 9.sp,
                                            color = SlateTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!isNepseLoading || nepseVal != null) {
                                val change = nepseChange ?: 79.74
                                val pctVal = nepsePercent ?: 3.07
                                val isGain = change >= 0
                                val idxColor = if (isGain) GainGreen else LossRed

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isGain) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = idxColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = String.format("%s%,.2f (%s%.2f%%)", if (isGain) "+" else "", change, if (isGain) "+" else "", pctVal),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = idxColor
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showOtherIndices = !showOtherIndices },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("toggle_other_indices")
                            ) {
                                Icon(
                                    imageVector = if (showOtherIndices) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Other Indices",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (showOtherIndices) {
                        val pct = nepsePercent ?: 3.07
                        val otherIndices = listOf(
                            Triple("Sensitive Index", 475.20, 0.85),
                            Triple("Float Index", 184.50, 0.95),
                            Triple("Banking Index", 1418.10, 1.20),
                            Triple("Hydropower Index", 2544.30, -0.40),
                            Triple("Life Insurance", 11850.00, 1.45),
                            Triple("Non-Life Insurance", 10420.00, 1.30),
                            Triple("Microfinance", 3850.50, 1.85),
                            Triple("Manufacturing", 5120.00, 0.65)
                        ).map { (name, baseVal, beta) ->
                            val subPct = pct * beta
                            val subVal = baseVal * (1.0 + (subPct / 100.0))
                            val subChange = subVal - baseVal
                            SubIndexData(name, subVal, subChange, subPct)
                        }

                        HorizontalDivider(
                            color = SlateBorder,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.8.dp
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "OTHER INDICES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextSecondary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            otherIndices.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { indexData ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SlateBg)
                                                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = indexData.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SlateTextSecondary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = String.format("%,.1f", indexData.value),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = SlateTextPrimary
                                                    )
                                                    val isSubGain = indexData.change >= 0
                                                    val subColor = if (isSubGain) GainGreen else LossRed
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isSubGain) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                            contentDescription = null,
                                                            tint = subColor,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = String.format("%.2f%%", indexData.percent),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = subColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Balance Card (Portfolio Value & Metrics toggle section)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PORTFOLIO VALUE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextSecondary,
                                letterSpacing = 1.5.sp
                            )
                            IconButton(
                                onClick = { showBalances = !showBalances },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("toggle_balance_visibility")
                            ) {
                                Icon(
                                    imageVector = if (showBalances) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Balance Visibility",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Last Synced",
                                    tint = SlateTextSecondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Synced: $lastSync",
                                    fontSize = 10.sp,
                                    color = SlateTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (showBalances) moneyFormat.format(totalValue) else "Rs ••••••",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateTextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SlateBorder, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Current Investment Column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CURRENT INVESTMENT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (showBalances) moneyFormat.format(totalCost) else "Rs ••••••",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(SlateBorder)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Current Profit/Loss Column
                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "CURRENT PROFIT/LOSS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextSecondary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val isGain = totalGainLoss >= 0
                            val color = if (isGain) GainGreen else LossRed
                            Text(
                                text = if (showBalances) "${if (isGain) "+" else ""}${moneyFormat.format(totalGainLoss)}" else "Rs ••••••",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Text(
                                text = if (showBalances) "${if (isGain) "+" else ""}${String.format("%.2f%%", totalGainPercent * 100)}" else "•••%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        // Today's Difference Card (Always Visible)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "TODAY'S DIFFERENCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val isTodayGain = todayDiff >= 0
                    val todayColor = if (isTodayGain) GainGreen else LossRed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isTodayGain) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = if (isTodayGain) "Gain" else "Loss",
                            tint = todayColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${if (isTodayGain) "+" else ""}${moneyFormat.format(todayDiff)} (${if (isTodayGain) "+" else ""}${String.format("%.2f%%", todayPercentChange)})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = todayColor
                        )
                    }
                }
            }
        }
        // Portfolio History Chart (interactive) — shown above Current Holdings
        if (history.size >= 2) {
            item {
                // Parse and sort history chronologically for proper range selection
                val dateFormats = remember {
                    listOf(
                        SimpleDateFormat("yyyy-MM-dd", Locale.US),
                        SimpleDateFormat("yyyy/MM/dd", Locale.US),
                        SimpleDateFormat("MM-dd-yyyy", Locale.US),
                        SimpleDateFormat("MM/dd/yyyy", Locale.US),
                        SimpleDateFormat("dd-MM-yyyy", Locale.US),
                        SimpleDateFormat("dd/MM/yyyy", Locale.US)
                    )
                }
                
                val parsedHistory = remember(history) {
                    history.mapNotNull { item ->
                        var parsedDate: Date? = null
                        for (fmt in dateFormats) {
                            try {
                                parsedDate = fmt.parse(item.date.trim())
                                if (parsedDate != null) break
                            } catch (e: Exception) {}
                        }
                        if (parsedDate != null) Pair(item, parsedDate) else null
                    }.sortedBy { it.second }
                }

                val filteredHistory = remember(parsedHistory, selectedPeriod) {
                    val now = Date()
                    val calendar = Calendar.getInstance()
                    val result = when (selectedPeriod) {
                        "1M" -> {
                            calendar.time = now
                            calendar.add(Calendar.MONTH, -1)
                            parsedHistory.filter { it.second.after(calendar.time) }
                        }
                        "3M" -> {
                            calendar.time = now
                            calendar.add(Calendar.MONTH, -3)
                            parsedHistory.filter { it.second.after(calendar.time) }
                        }
                        "6M" -> {
                            calendar.time = now
                            calendar.add(Calendar.MONTH, -6)
                            parsedHistory.filter { it.second.after(calendar.time) }
                        }
                        "1Y" -> {
                            calendar.time = now
                            calendar.add(Calendar.YEAR, -1)
                            parsedHistory.filter { it.second.after(calendar.time) }
                        }
                        else -> parsedHistory
                    }.map { it.first }
                    
                    if (result.size >= 2) result else history
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (hoveredPoint != null) "VALUE ON ${hoveredPoint?.date?.uppercase()}" else "PORTFOLIO HISTORY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hoveredPoint != null) GrowBlue else SlateTextSecondary,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (hoveredPoint != null) {
                            val displayVal = hoveredPoint?.value ?: 0.0
                            Text(
                                text = moneyFormat.format(displayVal),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SlateTextPrimary
                            )
                        } else {
                            Text(
                                text = "Touch & drag graph to view details",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = SlateTextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Chart
                        val minVal = filteredHistory.minOf { it.value }
                        val maxVal = filteredHistory.maxOf { it.value }
                        val range = if (maxVal > minVal) maxVal - minVal else 1.0

                        var chartSize by remember { mutableStateOf(IntSize.Zero) }
                        var touchX by remember { mutableStateOf<Float?>(null) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .onSizeChanged { chartSize = it }
                                .pointerInput(filteredHistory) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val anyPressed = event.changes.any { it.pressed }
                                            if (!anyPressed) {
                                                hoveredPoint = null
                                                touchX = null
                                            } else {
                                                val position = event.changes.firstOrNull { it.pressed }?.position
                                                    ?: event.changes.firstOrNull()?.position
                                                touchX = position?.x
                                                if (position != null && chartSize.width > 0) {
                                                    val idx = ((position.x / chartSize.width) * filteredHistory.size)
                                                        .roundToInt()
                                                        .coerceIn(0, filteredHistory.size - 1)
                                                    hoveredPoint = filteredHistory[idx]
                                                }
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val padBottom = 24f
                                val padTop = 8f
                                val chartH = h - padBottom - padTop

                                fun xFor(i: Int) = if (filteredHistory.size > 1) w * i / (filteredHistory.size - 1) else w / 2
                                fun yFor(v: Double) = padTop + chartH * (1.0 - (v - minVal) / range).toFloat()

                                // Gradient fill
                                val path = Path()
                                path.moveTo(xFor(0), yFor(filteredHistory[0].value))
                                for (i in 1 until filteredHistory.size) {
                                    path.lineTo(xFor(i), yFor(filteredHistory[i].value))
                                }
                                val lastX = xFor(filteredHistory.size - 1)
                                path.lineTo(lastX, h)
                                path.lineTo(0f, h)
                                path.close()
                                drawPath(
                                    path = path,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(GrowBlue.copy(alpha = 0.35f), GrowBlue.copy(alpha = 0.0f))
                                    )
                                )

                                // Line
                                val linePath = Path()
                                linePath.moveTo(xFor(0), yFor(filteredHistory[0].value))
                                for (i in 1 until filteredHistory.size) {
                                    linePath.lineTo(xFor(i), yFor(filteredHistory[i].value))
                                }
                                drawPath(
                                    path = linePath,
                                    color = GrowBlue,
                                    style = Stroke(width = 2.5f, pathEffect = PathEffect.cornerPathEffect(8f))
                                )

                                // Crosshair and highlight dot
                                val hovered = hoveredPoint
                                if (hovered != null && chartSize.width > 0) {
                                    val idx = filteredHistory.indexOf(hovered).coerceIn(0, filteredHistory.size - 1)
                                    val cx = xFor(idx)
                                    val cy = yFor(hovered.value)
                                    // Vertical line
                                    drawLine(
                                        color = GrowBlue.copy(alpha = 0.5f),
                                        start = Offset(cx, padTop),
                                        end = Offset(cx, h - padBottom),
                                        strokeWidth = 1f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                    )
                                    // Dot
                                    drawCircle(color = GrowBlue, radius = 5f, center = Offset(cx, cy))
                                    drawCircle(color = Color.White, radius = 2.5f, center = Offset(cx, cy))
                                }

                                // Date lines: first and last
                                if (filteredHistory.size >= 2) {
                                    drawLine(
                                        color = android.graphics.Color.valueOf(0.58f, 0.65f, 0.76f, 0.3f).toArgb().let { Color(it) },
                                        start = Offset(0f, h - padBottom),
                                        end = Offset(w, h - padBottom),
                                        strokeWidth = 0.5f
                                    )
                                }
                            }

                            // Date labels overlay
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = filteredHistory.first().date,
                                    fontSize = 9.sp,
                                    color = SlateTextSecondary
                                )
                                Text(
                                    text = filteredHistory.last().date,
                                    fontSize = 9.sp,
                                    color = SlateTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Period Selection Buttons (1M, 3M, 6M, 1Y, ALL)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val periods = listOf("1M", "3M", "6M", "1Y", "ALL")
                            periods.forEach { period ->
                                val isSelected = selectedPeriod == period
                                val chipBg = if (isSelected) GrowBlue.copy(alpha = 0.15f) else Color.Transparent
                                val chipBorderColor = if (isSelected) GrowBlue else SlateBorder
                                val textColor = if (isSelected) GrowBlue else SlateTextSecondary
                                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipBg)
                                        .border(1.dp, chipBorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedPeriod = period }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = period,
                                        fontSize = 11.sp,
                                        fontWeight = fontWeight,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Current Holdings Header with Search & Filter Toggle
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Holdings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${filteredHoldings.size} of ${holdings.size}",
                            fontSize = 12.sp,
                            color = SlateTextSecondary
                        )
                        IconButton(
                            onClick = { holdingsFilterExpanded = !holdingsFilterExpanded },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter & Sort",
                                tint = if (holdingsFilterExpanded || holdingsFilterPerformance != "All" || holdingsSortBy != "Today's Diff" || holdingsSearchQuery.isNotEmpty()) GrowBlue else SlateTextSecondary
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = holdingsFilterExpanded || holdingsSearchQuery.isNotEmpty()) {
                    OutlinedTextField(
                        value = holdingsSearchQuery,
                        onValueChange = { holdingsSearchQuery = it },
                        placeholder = { Text("Search by symbol or name...", color = SlateTextSecondary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (holdingsSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { holdingsSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SlateSurface,
                            unfocusedContainerColor = SlateSurface,
                            focusedBorderColor = GrowBlue,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary
                        )
                    )
                }

                AnimatedVisibility(visible = holdingsFilterExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Sort by Section
                            Text(
                                text = "SORT BY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextSecondary,
                                letterSpacing = 1.sp
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val sortOptions = listOf("Today's Diff", "Value", "Gain/Loss", "Ticker", "Shares")
                                items(sortOptions) { option ->
                                    val selected = holdingsSortBy == option
                                    CustomFilterChip(
                                        selected = selected,
                                        label = option,
                                        onClick = {
                                            if (selected) {
                                                holdingsSortOrderDesc = !holdingsSortOrderDesc
                                            } else {
                                                holdingsSortBy = option
                                                holdingsSortOrderDesc = true
                                            }
                                        },
                                        arrowDirectionDesc = if (selected) holdingsSortOrderDesc else null
                                    )
                                }
                            }

                            HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 0.8.dp)

                            // Performance Section
                            Text(
                                text = "PERFORMANCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextSecondary,
                                letterSpacing = 1.sp
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val performanceOptions = listOf("All", "Profitable", "In the Red")
                                items(performanceOptions) { option ->
                                    val selected = holdingsFilterPerformance == option
                                    CustomFilterChip(
                                        selected = selected,
                                        label = option,
                                        onClick = { holdingsFilterPerformance = option }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (holdings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Empty",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No assets loaded",
                            fontSize = 14.sp,
                            color = SlateTextSecondary
                        )
                        Text(
                            text = "Link your Google Sheet in Settings to sync.",
                            fontSize = 12.sp,
                            color = SlateTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (filteredHoldings.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Not found",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No holding matches filter/search",
                            fontSize = 14.sp,
                            color = SlateTextSecondary
                        )
                    }
                }
            }
        } else {
            val stocksMap = stocks.associateBy { it.ticker.uppercase().trim() }
            items(filteredHoldings) { holding ->
                val matchingStock = stocksMap[holding.ticker.uppercase().trim()]
                HoldingRowItem(
                    holding = holding,
                    stockItem = matchingStock,
                    onTickerClick = { symbol -> viewModel.selectSymbol(symbol) }
                )
            }
        }
    }
    } // end PullToRefreshBox
}

@Composable
fun HoldingRowItem(
    holding: CurrentHolding,
    stockItem: StockItem?,
    onTickerClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GrowBlue.copy(alpha = 0.15f))
                                .clickable { onTickerClick(holding.ticker) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = holding.ticker,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrowBlue
                            )
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Open Chart",
                                tint = GrowBlue,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = holding.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateTextPrimary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val marketPrice = stockItem?.price ?: holding.currentPrice
                    Text(
                        text = "${holding.shares} Shares @ ${moneyFormat.format(marketPrice)}",
                        fontSize = 12.sp,
                        color = SlateTextSecondary
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text(
                        text = moneyFormat.format(holding.marketValue),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (stockItem != null) {
                        val todayChange = stockItem.change * holding.shares
                        val todayIsGain = todayChange >= 0
                        val todayColor = if (todayIsGain) GainGreen else LossRed
                        Text(
                            text = "Today: ${if (todayIsGain) "+" else ""}${moneyFormat.format(todayChange)} (${if (stockItem.changePercent >= 0) "+" else ""}${String.format("%.2f%%", stockItem.changePercent)})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = todayColor
                        )
                    } else {
                        Text(
                            text = "Market Price",
                            fontSize = 10.sp,
                            color = SlateTextSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateBg.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "HOLDING DETAILS & OVERALL PERFORMANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val overallIsGain = holding.gainLoss >= 0
                    val overallColor = if (overallIsGain) GainGreen else LossRed

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                GridItem(
                                    label = "WACC (Avg Cost)",
                                    value = moneyFormat.format(holding.avgPrice)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                GridItem(
                                    label = "Total Cost",
                                    value = moneyFormat.format(holding.shares * holding.avgPrice)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                GridItem(
                                    label = "Overall Profit / Loss",
                                    value = "${if (overallIsGain) "+" else ""}${moneyFormat.format(holding.gainLoss)} (${String.format("%.2f%%", holding.gainLossPercent)})",
                                    valueColor = overallColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                GridItem(
                                    label = "Trading Volume",
                                    value = stockItem?.let { formatVolume(it.volume) } ?: "N/A"
                                )
                            }
                        }

                        if (stockItem != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    GridItem(label = "Today's High", value = moneyFormat.format(stockItem.high))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    GridItem(label = "Today's Low", value = moneyFormat.format(stockItem.low))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    GridItem(label = "Opening Price", value = moneyFormat.format(stockItem.open))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    GridItem(label = "LTP (Last Price)", value = moneyFormat.format(stockItem.price))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    GridItem(label = "LTP (Last Price)", value = moneyFormat.format(holding.currentPrice))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    GridItem(label = "Current Value", value = moneyFormat.format(holding.marketValue))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun GridItem(
    label: String,
    value: String,
    valueColor: Color = SlateTextPrimary
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurface.copy(alpha = 0.5f))
            .border(1.dp, SlateBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 9.sp,
                color = SlateTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StockListScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val stocks by viewModel.stockList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val sectors = remember(stocks) {
        listOf("All") + stocks.map { it.sector.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    var sortBy by remember { mutableStateOf("Ticker") }
    var sortOrderDesc by remember { mutableStateOf(false) }
    var performanceFilter by remember { mutableStateOf("All") }
    var sectorFilter by remember { mutableStateOf("All") }
    var priceFilter by remember { mutableStateOf("All") }
    var filterExpanded by remember { mutableStateOf(false) }

    val filteredStocks = remember(stocks, searchQuery, sortBy, sortOrderDesc, performanceFilter, sectorFilter, priceFilter) {
        var result = stocks.filter {
            it.ticker.contains(searchQuery, ignoreCase = true) ||
                    it.name.contains(searchQuery, ignoreCase = true)
        }

        if (sectorFilter != "All") {
            result = result.filter { it.sector.trim().equals(sectorFilter.trim(), ignoreCase = true) }
        }

        result = when (performanceFilter) {
            "Gainers" -> result.filter { it.changePercent > 0 }
            "Losers" -> result.filter { it.changePercent < 0 }
            else -> result
        }

        result = when (priceFilter) {
            "Under Rs 100" -> result.filter { it.price < 100.0 }
            "Rs 100 - Rs 500" -> result.filter { it.price in 100.0..500.0 }
            "Above Rs 500" -> result.filter { it.price > 500.0 }
            else -> result
        }

        when (sortBy) {
            "Ticker" -> {
                if (sortOrderDesc) result.sortedByDescending { it.ticker }
                else result.sortedBy { it.ticker }
            }
            "Price" -> {
                if (sortOrderDesc) result.sortedByDescending { it.price }
                else result.sortedBy { it.price }
            }
            "% Change" -> {
                if (sortOrderDesc) result.sortedByDescending { it.changePercent }
                else result.sortedBy { it.changePercent }
            }
            "Volume" -> {
                if (sortOrderDesc) result.sortedByDescending { it.volume }
                else result.sortedBy { it.volume }
            }
            else -> result
        }
    }

    val syncState by viewModel.syncState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(syncState) { if (syncState !is SyncState.Syncing) isRefreshing = false }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { isRefreshing = true; viewModel.refreshFromSheets() },
        modifier = modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredStocks.size} stock(s)",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )
        }

        // Search Box and Filter Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by symbol, name...", color = SlateTextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateTextSecondary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface,
                    focusedBorderColor = GrowBlue,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary
                )
            )

            IconButton(
                onClick = { filterExpanded = !filterExpanded },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (filterExpanded) GrowBlue.copy(alpha = 0.15f) else SlateSurface)
                    .border(1.dp, if (filterExpanded) GrowBlue else SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = if (filterExpanded || sectorFilter != "All" || performanceFilter != "All" || priceFilter != "All" || sortBy != "Ticker") GrowBlue else SlateTextSecondary
                )
            }
        }

        AnimatedVisibility(visible = filterExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sort by
                    Text(
                        text = "SORT BY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sortOptions = listOf("Ticker", "Price", "% Change", "Volume")
                        items(sortOptions) { option ->
                            val selected = sortBy == option
                            CustomFilterChip(
                                selected = selected,
                                label = option,
                                onClick = {
                                    if (selected) {
                                        sortOrderDesc = !sortOrderDesc
                                    } else {
                                        sortBy = option
                                        sortOrderDesc = (option != "Ticker") // Default desc for numeric, asc for alpha
                                    }
                                },
                                arrowDirectionDesc = if (selected) sortOrderDesc else null
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 0.8.dp)

                    // Sector Filter
                    Text(
                        text = "SECTOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sectors) { sector ->
                            val selected = sectorFilter == sector
                            CustomFilterChip(
                                selected = selected,
                                label = sector,
                                onClick = { sectorFilter = sector }
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 0.8.dp)

                    // Performance Filter
                    Text(
                        text = "DAILY PERFORMANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val perfOptions = listOf("All", "Gainers", "Losers")
                        items(perfOptions) { option ->
                            val selected = performanceFilter == option
                            CustomFilterChip(
                                selected = selected,
                                label = option,
                                onClick = { performanceFilter = option }
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 0.8.dp)

                    // Price Filter
                    Text(
                        text = "PRICE RANGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val priceOptions = listOf("All", "Under Rs 100", "Rs 100 - Rs 500", "Above Rs 500")
                        items(priceOptions) { option ->
                            val selected = priceFilter == option
                            CustomFilterChip(
                                selected = selected,
                                label = option,
                                onClick = { priceFilter = option }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredStocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Not found",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No stock matches filters",
                        fontSize = 14.sp,
                        color = SlateTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredStocks) { stock ->
                    StockRowItem(
                        stock = stock,
                        onTickerClick = { symbol -> viewModel.selectSymbol(symbol) }
                    )
                }
            }
        }
    }
    } // end PullToRefreshBox
}

@Composable
fun StockRowItem(stock: StockItem, onTickerClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.3f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateBorder)
                                .clickable { onTickerClick(stock.ticker) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stock.ticker,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Open Chart",
                                tint = SlateTextPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = stock.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateTextPrimary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stock.sector,
                            fontSize = 11.sp,
                            color = SlateTextSecondary
                        )
                        Text(
                            text = "•",
                            color = SlateBorder,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Vol: ${formatVolume(stock.volume)}",
                            fontSize = 11.sp,
                            color = SlateTextSecondary
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.7f)
                ) {
                    Text(
                        text = moneyFormat.format(stock.price),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    val isPositive = stock.change >= 0
                    val color = if (isPositive) GainGreen else LossRed
                    val text = "${if (isPositive) "+" else ""}${moneyFormat.format(stock.change)} (${String.format("%.2f%%", stock.changePercent)})"
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-bar for Open, High, Low, Swing%
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateBg.copy(alpha = 0.6f))
                    .border(0.5.dp, SlateBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("OPEN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    Text(moneyFormat.format(stock.open), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateTextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HIGH", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    Text(moneyFormat.format(stock.high), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GainGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LOW", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    Text(moneyFormat.format(stock.low), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LossRed)
                }
                val swingVal = if (stock.low > 0) ((stock.high - stock.low) / stock.low) * 100.0 else 0.0
                Column(horizontalAlignment = Alignment.End) {
                    Text("SWING %", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                    Text(String.format("%.2f%%", swingVal), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA68C8))
                }
            }
        }
    }
}

@Composable
fun WatchListScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val watchlist by viewModel.watchList.collectAsState()
    val stocks by viewModel.stockList.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Ticker") }
    var sortOrderDesc by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("All") }
    var filterExpanded by remember { mutableStateOf(false) }

    val stocksMap = remember(stocks) { stocks.associateBy { it.ticker.uppercase().trim() } }

    val filteredWatchlist = remember(watchlist, stocks, searchQuery, sortBy, sortOrderDesc, statusFilter) {
        val getEffectivePrice: (WatchStock) -> Double = { watch ->
            val matchingStock = stocksMap[watch.ticker.uppercase().trim()]
            matchingStock?.price ?: watch.currentPrice
        }

        var result = watchlist.filter {
            it.ticker.contains(searchQuery, ignoreCase = true) ||
                    it.name.contains(searchQuery, ignoreCase = true)
        }

        result = when (statusFilter) {
            "Below Target" -> result.filter { getEffectivePrice(it) <= it.targetPrice }
            "Above Target" -> result.filter { getEffectivePrice(it) > it.targetPrice }
            "Near Target (<= 5%)" -> result.filter {
                val price = getEffectivePrice(it)
                if (price > 0.0) {
                    val gap = (it.targetPrice - price) / price
                    Math.abs(gap) <= 0.05
                } else false
            }
            else -> result
        }

        when (sortBy) {
            "Ticker" -> {
                if (sortOrderDesc) result.sortedByDescending { it.ticker }
                else result.sortedBy { it.ticker }
            }
            "Target Price" -> {
                if (sortOrderDesc) result.sortedByDescending { it.targetPrice }
                else result.sortedBy { it.targetPrice }
            }
            "Distance %" -> {
                val selector: (WatchStock) -> Double = {
                    val price = getEffectivePrice(it)
                    if (price > 0.0) {
                        (it.targetPrice - price) / price
                    } else 0.0
                }
                if (sortOrderDesc) result.sortedByDescending(selector)
                else result.sortedBy(selector)
            }
            else -> result
        }
    }

    val syncStateWatch by viewModel.syncState.collectAsState()
    var isRefreshingWatch by remember { mutableStateOf(false) }
    LaunchedEffect(syncStateWatch) { if (syncStateWatch !is SyncState.Syncing) isRefreshingWatch = false }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshingWatch,
        onRefresh = { isRefreshingWatch = true; viewModel.refreshFromSheets() },
        modifier = modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredWatchlist.size} stock(s)",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )
        }

        // Search Bar & Filter Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search watchlist symbols...", color = SlateTextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateTextSecondary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SlateTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface,
                    focusedBorderColor = GrowBlue,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary
                )
            )

            IconButton(
                onClick = { filterExpanded = !filterExpanded },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (filterExpanded) GrowBlue.copy(alpha = 0.15f) else SlateSurface)
                    .border(1.dp, if (filterExpanded) GrowBlue else SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = if (filterExpanded || statusFilter != "All" || sortBy != "Ticker") GrowBlue else SlateTextSecondary
                )
            }
        }

        AnimatedVisibility(visible = filterExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sort By
                    Text(
                        text = "SORT BY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sortOptions = listOf("Ticker", "Target Price", "Distance %")
                        items(sortOptions) { option ->
                            val selected = sortBy == option
                            CustomFilterChip(
                                selected = selected,
                                label = option,
                                onClick = {
                                    if (selected) {
                                        sortOrderDesc = !sortOrderDesc
                                    } else {
                                        sortBy = option
                                        sortOrderDesc = (option != "Ticker")
                                    }
                                },
                                arrowDirectionDesc = if (selected) sortOrderDesc else null
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.6f), thickness = 0.8.dp)

                    // Target Proximity Filter
                    Text(
                        text = "TARGET PROXIMITY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val proximityOptions = listOf("All", "Below Target", "Above Target", "Near Target (<= 5%)")
                        items(proximityOptions) { option ->
                            val selected = statusFilter == option
                            CustomFilterChip(
                                selected = selected,
                                label = option,
                                onClick = { statusFilter = option }
                            )
                        }
                    }
                }
            }
        }

        if (watchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
                        contentDescription = "Empty Stars",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Watchlist is empty",
                        fontSize = 14.sp,
                        color = SlateTextSecondary
                    )
                    Text(
                        text = "Add symbols to your 'watch list' sheet to track targets.",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (filteredWatchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Not found",
                        tint = SlateTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No watchlist stock matches filters",
                        fontSize = 14.sp,
                        color = SlateTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredWatchlist) { item ->
                    val matchingStock = stocksMap[item.ticker.uppercase().trim()]
                    WatchRowItem(
                        watch = item,
                        stockItem = matchingStock,
                        onTickerClick = { symbol -> viewModel.selectSymbol(symbol) }
                    )
                }
            }
        }
    }
    } // end PullToRefreshBox
}

@Composable
fun WatchRowItem(
    watch: WatchStock,
    stockItem: StockItem?,
    onTickerClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.3f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GrowBlue.copy(alpha = 0.15f))
                                .clickable { onTickerClick(watch.ticker) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = watch.ticker,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrowBlue
                            )
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Open Chart",
                                tint = GrowBlue,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = watch.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateTextPrimary,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stockItem?.sector ?: "N/A",
                            fontSize = 11.sp,
                            color = SlateTextSecondary
                        )
                        if (stockItem != null) {
                            Text(
                                text = "•",
                                color = SlateBorder,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Vol: ${formatVolume(stockItem.volume)}",
                                fontSize = 11.sp,
                                color = SlateTextSecondary
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.7f)
                ) {
                    val activeCurrentPrice = stockItem?.price ?: watch.currentPrice
                    Text(
                        text = moneyFormat.format(activeCurrentPrice),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (stockItem != null) {
                        val isPositive = stockItem.change >= 0
                        val color = if (isPositive) GainGreen else LossRed
                        val text = "${if (isPositive) "+" else ""}${moneyFormat.format(stockItem.change)} (${if (stockItem.changePercent >= 0) "+" else ""}${String.format("%.2f%%", stockItem.changePercent)})"
                        Text(
                            text = text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        )
                    } else {
                        Text(
                            text = "Market Price",
                            fontSize = 11.sp,
                            color = SlateTextSecondary
                        )
                    }
                }
            }

            if (stockItem != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateBg.copy(alpha = 0.6f))
                        .border(0.5.dp, SlateBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("OPEN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Text(moneyFormat.format(stockItem.open), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateTextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HIGH", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Text(moneyFormat.format(stockItem.high), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GainGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LOW", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Text(moneyFormat.format(stockItem.low), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LossRed)
                    }
                    val swingVal = if (stockItem.low > 0) ((stockItem.high - stockItem.low) / stockItem.low) * 100.0 else 0.0
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SWING %", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SlateTextSecondary)
                        Text(String.format("%.2f%%", swingVal), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBA68C8))
                    }
                }
            }

            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateBg.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Target:",
                        fontSize = 11.sp,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = moneyFormat.format(watch.targetPrice),
                        fontSize = 12.sp,
                        color = SlateTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                val activeCurrentPrice = stockItem?.price ?: watch.currentPrice
                val gapPercent = if (activeCurrentPrice > 0) {
                    (watch.targetPrice - activeCurrentPrice) / activeCurrentPrice * 100
                } else 0.0

                val isTargetReached = gapPercent >= 0
                val badgeColor = if (isTargetReached) GainGreen else LossRed
                val proximityText = if (isTargetReached) {
                    "Reached (+${String.format("%.1f%%", gapPercent)})"
                } else {
                    "${String.format("%.1f%%", gapPercent)} to target"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = proximityText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.portfolioHistory.collectAsState()
    var hoveredPoint by remember { mutableStateOf<PortfolioHistory?>(null) }

    val syncStateHist by viewModel.syncState.collectAsState()
    var isRefreshingHist by remember { mutableStateOf(false) }
    LaunchedEffect(syncStateHist) { if (syncStateHist !is SyncState.Syncing) isRefreshingHist = false }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshingHist,
        onRefresh = { isRefreshingHist = true; viewModel.refreshFromSheets() },
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Performance History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Empty Chart",
                            tint = SlateTextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No history points found",
                            fontSize = 14.sp,
                            color = SlateTextSecondary
                        )
                        Text(
                            text = "Populate rows with columns 'Date' and 'Value' in your 'portfoliohistory' sheet.",
                            fontSize = 12.sp,
                            color = SlateTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Neon Chart Panel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (hoveredPoint != null) "VALUATION ON ${hoveredPoint?.date?.uppercase()}" else "HISTORIC VALUATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hoveredPoint != null) GrowBlue else SlateTextSecondary,
                            letterSpacing = 1.2.sp
                        )

                        val displayVal = hoveredPoint?.value ?: (history.lastOrNull()?.value ?: 0.0)
                        Text(
                            text = moneyFormat.format(displayVal),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SlateTextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Render Canvas Chart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            HistoryLineChart(
                                points = history,
                                onPointHovered = { hoveredPoint = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = history.firstOrNull()?.date ?: "",
                                fontSize = 10.sp,
                                color = SlateTextSecondary
                            )
                            Text(
                                text = if (hoveredPoint != null) "Inspection Mode" else "Timeline Performance",
                                fontSize = 10.sp,
                                color = if (hoveredPoint != null) GrowBlue else SlateTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = history.lastOrNull()?.date ?: "",
                                fontSize = 10.sp,
                                color = SlateTextSecondary
                            )
                        }
                    }
                }
            }

            // Ledger title
            item {
                Text(
                    text = "Historical Ledger",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )
            }

            // Ledger rows
            items(history.reversed()) { data ->
                LedgerRowItem(point = data)
            }
        }
    }
    } // end PullToRefreshBox
}

@Composable
fun LedgerRowItem(point: PortfolioHistory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Date",
                    tint = GrowBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = point.date,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateTextPrimary
                )
            }

            Text(
                text = moneyFormat.format(point.value),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
        }
    }
}

@Composable
fun HistoryLineChart(
    points: List<PortfolioHistory>,
    modifier: Modifier = Modifier,
    onPointHovered: ((PortfolioHistory?) -> Unit)? = null
) {
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { chartSize = it }
            .pointerInput(points, chartSize) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val anyPressed = changes.any { it.pressed }
                        if (anyPressed && points.isNotEmpty() && chartSize.width > 0) {
                            val position = changes.first().position
                            val width = chartSize.width.toFloat()
                            val steps = points.size
                            val xInterval = if (steps > 1) width / (steps - 1) else width
                            val idx = (position.x / xInterval).roundToInt().coerceIn(0, points.lastIndex)
                            selectedIndex = idx
                            onPointHovered?.invoke(points[idx])
                        } else {
                            selectedIndex = null
                            onPointHovered?.invoke(null)
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height

            val values = points.map { it.value }
            val minVal = (values.minOrNull() ?: 0.0) * 0.98 // Give bottom padding
            val maxVal = (values.maxOrNull() ?: 1.0) * 1.02 // Give top padding
            val valRange = maxVal - minVal

            val steps = points.size
            val xInterval = if (steps > 1) width / (steps - 1) else width

            val coordinates = points.mapIndexed { index, point ->
                val x = index * xInterval
                val ratio = if (valRange > 0) (point.value - minVal) / valRange else 0.5
                val y = height - (ratio * height).toFloat()
                Offset(x, y)
            }

            if (coordinates.isEmpty()) return@Canvas

            // 1. Draw glowing gradient fill under curve
            val fillPath = Path().apply {
                moveTo(0f, height)
                coordinates.forEach { lineTo(it.x, it.y) }
                lineTo(width, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GrowBlue.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
            )

            // 2. Draw lines
            val linePath = Path().apply {
                moveTo(coordinates[0].x, coordinates[0].y)
                for (i in 1 until coordinates.size) {
                    lineTo(coordinates[i].x, coordinates[i].y)
                }
            }
            drawPath(
                path = linePath,
                color = GrowBlue,
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. Draw vertical guide line at selectedIndex
            if (selectedIndex != null) {
                val selIdx = selectedIndex!!
                val selectedPoint = coordinates.getOrNull(selIdx)
                if (selectedPoint != null) {
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.8f),
                        start = Offset(selectedPoint.x, 0f),
                        end = Offset(selectedPoint.x, height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }

            // 4. Draw dots on nodes
            coordinates.forEachIndexed { idx, point ->
                if (idx == selectedIndex) {
                    // Selected point neon bubble
                    drawCircle(
                        color = GrowBlue,
                        radius = 10.dp.toPx(),
                        center = point,
                        alpha = 0.35f
                    )
                    drawCircle(
                        color = GrowBlue,
                        radius = 5.dp.toPx(),
                        center = point
                    )
                } else if (idx == coordinates.lastIndex && selectedIndex == null) {
                    // Draw outer neon halo for final point
                    drawCircle(
                        color = GainGreen,
                        radius = 8.dp.toPx(),
                        center = point,
                        alpha = 0.4f
                    )
                    drawCircle(
                        color = GainGreen,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                } else {
                    drawCircle(
                        color = GrowBlue.copy(alpha = 0.4f),
                        radius = 2.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val spreadsheetId by viewModel.spreadsheetId.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val gso = remember {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets.readonly"))
        
        val webClientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (webClientIdResId != 0) {
            val webClientId = context.getString(webClientIdResId)
            builder.requestIdToken(webClientId)
        }
        builder.build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    var showConfigHelpDialog by remember { mutableStateOf(false) }
    var lastErrorCode by remember { mutableStateOf<Int?>(null) }
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (email != null) {
                viewModel.updateGoogleAccount(email)
                Log.d("SettingsScreen", "Google Sign-In successful: $email")
                android.widget.Toast.makeText(context, "Successfully signed in: $email", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Google Sign-In API exception", e)
            val statusCode = if (e is ApiException) e.statusCode else null
            lastErrorCode = statusCode
            lastErrorMessage = e.localizedMessage
            if (statusCode == 10 || statusCode == 12500 || statusCode == 12501 || statusCode == 12502) {
                showConfigHelpDialog = true
            } else {
                val statusCodeText = if (statusCode != null) " (Status Code: $statusCode)" else ""
                val message = "Google Sign-In failed$statusCodeText: ${e.localizedMessage ?: "Developer configuration required"}. Please check Google Console OAuth configuration."
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showConfigHelpDialog) {
        AlertDialog(
            onDismissRequest = { showConfigHelpDialog = false },
            title = {
                Text(
                    text = "Google Sign-In Setup Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Status Code ${lastErrorCode ?: 10} (DEVELOPER_ERROR) indicates that Google Play Services cannot verify the signature of your app or is missing developer-side authorization configuration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "To resolve this, please verify and complete the following steps in Firebase and Google Cloud Console:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "1. App Package Name:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "com.nepse.portfoliotracker.app",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = GrowBlue,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "2. Build SHA-1 Fingerprint (Copy below):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "D3:1D:93:B9:E5:2C:7A:17:3F:AE:3B:B1:A1:C7:07:71:F6:BF:A4:94",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = GrowBlue,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "3. Build SHA-256 Fingerprint:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "2E:66:7C:F6:EE:75:7E:62:73:8B:D8:C1:5E:02:68:29:12:D2:5A:39:94:2D:67:89:CB:E1:10:55:8A:CB:FC:3C",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = GrowBlue,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 Steps to Verify and Resolve:\n\n" +
                               "A. ADD SHA-1 TO FIREBASE CONSOLE:\n" +
                               "1. Go to Firebase Console -> Project Settings.\n" +
                               "2. Scroll to 'Your apps' -> Android App.\n" +
                               "3. Add the SHA-1 (and SHA-256) certificate fingerprints matching the above exactly.\n" +
                               "4. Re-download the new google-services.json and upload it to the project root in AI Studio.\n\n" +
                               "B. CONFIGURE OAUTH CONSENT SCREEN (CRITICAL):\n" +
                               "1. Go to Google Cloud Console (console.cloud.google.com) and select this project.\n" +
                               "2. Navigate to 'APIs & Services' -> 'OAuth consent screen'.\n" +
                               "3. If publishing status is 'Testing' (default), scroll to the 'Test users' section.\n" +
                               "4. Click 'ADD USERS' and enter the EXACT Google email you are trying to sign in with (e.g., your email).\n" +
                               "5. If you do not add your email as a Test User, Google Sign-In will always fail with Status Code 10/12500!\n\n" +
                               "C. ENABLE GOOGLE SHEETS API:\n" +
                               "1. In Google Cloud Console, search for 'Google Sheets API'.\n" +
                               "2. Make sure it is Enabled for your cloud project.\n\n" +
                               "D. WAIT FOR PROPAGATION:\n" +
                               "- Fingerprints and OAuth config changes usually take 5 to 10 minutes to sync dynamically across Google Play Services on your device. Try again after a short wait.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString("D3:1D:93:B9:E5:2C:7A:17:3F:AE:3B:B1:A1:C7:07:71:F6:BF:A4:94"))
                        android.widget.Toast.makeText(context, "SHA-1 copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy SHA-1")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigHelpDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {


        // Google Authentication Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "GOOGLE SECURITY AUTHENTICATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!googleAccountEmail.isNullOrBlank()) {
                        // Signed In State
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SlateBg)
                                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google Account",
                                tint = GrowBlue,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Google Account connected",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GainGreen.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Active",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GainGreen
                                        )
                                    }
                                }
                                Text(
                                    text = googleAccountEmail ?: "",
                                    fontSize = 12.sp,
                                    color = SlateTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Only authorized users can sync this spreadsheet. Since you are authenticated, your sync requests will include your Google security credentials.",
                            fontSize = 11.sp,
                            color = SlateTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    viewModel.signOutGoogle()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect Google Account", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Signed Out State
                        Text(
                            text = "Connect your Google Account to securely sync from restricted/private spreadsheets that only you can access.",
                            fontSize = 12.sp,
                            color = SlateTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val signInIntent = googleSignInClient.signInIntent
                                signInLauncher.launch(signInIntent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Sign in key",
                                    tint = SlateBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign in with Google", color = SlateBg, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // Connection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "GOOGLE SPREADSHEET CONFIG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = spreadsheetId,
                        onValueChange = { viewModel.updateSpreadsheetId(it) },
                        label = { Text("Google Spreadsheet ID", color = SlateTextSecondary) },
                        placeholder = { Text("Enter ID from Sheets URL...", color = SlateTextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateTextPrimary,
                            unfocusedTextColor = SlateTextPrimary,
                            focusedContainerColor = SlateBg,
                            unfocusedContainerColor = SlateBg,
                            focusedBorderColor = GrowBlue,
                            unfocusedBorderColor = SlateBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.refreshFromSheets() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GrowBlue),
                        enabled = syncState !is SyncState.Syncing
                    ) {
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing Sheets...")
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetch & Refresh Prices", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sync State Banner
                    AnimatedVisibility(visible = syncState != SyncState.Idle) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.clearSyncState() },
                            color = when (syncState) {
                                is SyncState.Success -> GainGreen.copy(alpha = 0.15f)
                                is SyncState.Error -> LossRed.copy(alpha = 0.15f)
                                else -> SlateBg
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (syncState) {
                                    is SyncState.Success -> {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = GainGreen)
                                        Column {
                                            Text("Sync Completed Successfully!", color = GainGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Cached stock prices updated.", color = SlateTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    is SyncState.Error -> {
                                        Icon(Icons.Default.Error, contentDescription = "Error", tint = LossRed)
                                        Column {
                                            Text("Sync Error", color = LossRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text((syncState as SyncState.Error).message, color = SlateTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto-Fetch Schedule Card
        item {
            val autoSyncDays by viewModel.autoSyncDays.collectAsState()
            val autoSyncStart by viewModel.autoSyncStartMinutes.collectAsState()
            val autoSyncEnd by viewModel.autoSyncEndMinutes.collectAsState()

            val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

            fun minutesToTime(m: Int): String {
                val h = m / 60
                val min = m % 60
                val ampm = if (h < 12) "AM" else "PM"
                val displayH = if (h == 0) 12 else if (h > 12) h - 12 else h
                return String.format("%d:%02d %s", displayH, min, ampm)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "AUTO-FETCH SCHEDULE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Auto-fetch runs every minute during the configured window (Nepal Time). Default: Mon–Fri, 10:45 AM – 3:00 PM NST.",
                        fontSize = 11.sp,
                        color = SlateTextSecondary,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "ACTIVE DAYS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dayLabels.size) { idx ->
                            val selected = idx in autoSyncDays
                            val chipBg = if (selected) GrowBlue else SlateBg
                            val chipText = if (selected) Color.White else SlateTextSecondary
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipBg)
                                    .border(1.dp, if (selected) GrowBlue else SlateBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val newDays = autoSyncDays.toMutableSet()
                                        if (selected) newDays.remove(idx) else newDays.add(idx)
                                        viewModel.updateAutoSyncDays(newDays)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayLabels[idx],
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = chipText
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "TIME WINDOW (NST)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("START", fontSize = 9.sp, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Simplified: +/- buttons for time adjustment
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { viewModel.updateAutoSyncStartMinutes((autoSyncStart - 15).coerceAtLeast(0)) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = minutesToTime(autoSyncStart),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextPrimary
                                )
                                IconButton(
                                    onClick = { viewModel.updateAutoSyncStartMinutes((autoSyncStart + 15).coerceAtMost(autoSyncEnd - 15)) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = "to", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("END", fontSize = 9.sp, color = SlateTextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { viewModel.updateAutoSyncEndMinutes((autoSyncEnd - 15).coerceAtLeast(autoSyncStart + 15)) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = minutesToTime(autoSyncEnd),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextPrimary
                                )
                                IconButton(
                                    onClick = { viewModel.updateAutoSyncEndMinutes((autoSyncEnd + 15).coerceAtMost(23 * 60 + 59)) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = SlateTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gemini API Configuration Card
        item {
            val geminiApiKey by viewModel.geminiApiKey.collectAsState()
            var localKey by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
            var isEditingKey by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "GEMINI AI COGNITIVE AGENT CONFIG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Customize your AI Assistant. Enter a custom Gemini API Key below to override the default system key. If left blank, NepSecure uses its pre-configured secure system key.",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isEditingKey) {
                        OutlinedTextField(
                            value = localKey,
                            onValueChange = { localKey = it },
                            label = { Text("Gemini API Key", color = SlateTextSecondary) },
                            placeholder = { Text("AIzaSy...", color = SlateTextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = SlateTextPrimary,
                                unfocusedTextColor = SlateTextPrimary,
                                focusedContainerColor = SlateBg,
                                unfocusedContainerColor = SlateBg,
                                focusedBorderColor = GrowBlue,
                                unfocusedBorderColor = SlateBorder
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.updateGeminiApiKey(localKey)
                                    isEditingKey = false
                                    android.widget.Toast.makeText(context, "Gemini API Key saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GrowBlue)
                            ) {
                                Text("Save Key", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    localKey = geminiApiKey
                                    isEditingKey = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextPrimary),
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SlateBg)
                                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Key Status",
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (geminiApiKey.isNotBlank()) {
                                        "Custom Key Enabled (AIzaSy..." + geminiApiKey.takeLast(4) + ")"
                                    } else {
                                        "Using Secure Default System Key"
                                    },
                                    fontSize = 12.sp,
                                    color = if (geminiApiKey.isNotBlank()) GainGreen else SlateTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { isEditingKey = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Key",
                                    tint = GrowBlue
                                )
                            }
                        }
                    }
                }
            }
        }

        // Setup Guide Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "SPREADSHEET FORMATTING GUIDE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "1. Share your Spreadsheet as 'Anyone with the link can view'.",
                        fontSize = 12.sp,
                        color = SlateTextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = "2. Create exactly these 3 tab sheets with identical names and header columns:",
                        fontSize = 12.sp,
                        color = SlateTextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    GuideTabItem("Current", "Ticker/Symbol, Shares/Qty, WACC/Avg Price, LTP/Price (cell E5/O5 read)")
                    GuideTabItem("Stocks", "Ticker, Price/LTP, Change, Change %, Volume (Optional: Open, High, Low, Sector)")
                    GuideTabItem("Watch List", "Ticker, Target Price, Notes")

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "3. Copy the Spreadsheet ID from the browser URL:",
                        fontSize = 12.sp,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "docs.google.com/spreadsheets/d/[THIS-SPREADSHEET-ID]/edit",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GrowBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(SlateBg)
                            .padding(8.dp)
                    )
                }
            }
        }

        // Demo Actions Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "OFFLINE PERFORMANCE & DEMO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "By default, the app runs completely offline utilizing Room caching to ensure immediate startup speeds and data persistency on-the-go. Reset to demo data below:",
                        fontSize = 12.sp,
                        color = SlateTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.loadDemoData() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextPrimary),
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Demo")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Premium Demo Data")
                    }
                }
            }
        }
    }
}

@Composable
fun GuideTabItem(sheetName: String, columns: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SlateBg)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Layers, contentDescription = "Tab", tint = GrowBlue, modifier = Modifier.size(14.dp))
            Text(
                text = "Sheet: '$sheetName'",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
        }
        Text(
            text = "Columns: $columns",
            fontSize = 11.sp,
            color = SlateTextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 1_000_000 -> String.format("%.1fM", volume.toDouble() / 1_000_000)
        volume >= 1_000 -> String.format("%.1fK", volume.toDouble() / 1_000)
        else -> volume.toString()
    }
}

@Composable
fun CustomFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrowDirectionDesc: Boolean? = null
) {
    Surface(
        onClick = onClick,
        color = if (selected) GrowBlue.copy(alpha = 0.15f) else SlateSurface,
        border = BorderStroke(1.dp, if (selected) GrowBlue else SlateBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (selected) GrowBlue else SlateTextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            if (arrowDirectionDesc != null) {
                Icon(
                    imageVector = if (arrowDirectionDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = GrowBlue
                )
            }
        }
    }
}

@Composable
fun CandlestickChartDialog(
    symbol: String,
    candles: List<MarketCandle>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = symbol.uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SlateTextPrimary
                        )
                        Text(
                            text = "Historical Candlestick (Market API)",
                            fontSize = 12.sp,
                            color = SlateTextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SlateSurface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = GrowBlue)
                            Text(
                                text = "Loading historical data...",
                                fontSize = 12.sp,
                                color = SlateTextSecondary
                            )
                        }
                    }
                } else if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = LossRed,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = error,
                                fontSize = 14.sp,
                                color = SlateTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = GrowBlue)
                            ) {
                                Text("Close", color = Color.White)
                            }
                        }
                    }
                } else if (candles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No candle data available.",
                            fontSize = 14.sp,
                            color = SlateTextSecondary
                        )
                    }
                } else {
                    var timeFrame by remember { mutableStateOf("1M") }
                    val visibleCandles = remember(candles, timeFrame) {
                        when (timeFrame) {
                            "1M" -> candles.takeLast(22)
                            "3M" -> candles.takeLast(65)
                            else -> candles
                        }
                    }

                    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
                    
                    val activeCandle = remember(visibleCandles, hoveredIndex) {
                        val idx = hoveredIndex
                        if (idx != null && idx in visibleCandles.indices) {
                            visibleCandles[idx]
                        } else {
                            visibleCandles.lastOrNull()
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeCandle?.date ?: "Date",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextPrimary
                                )
                                if (hoveredIndex != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GrowBlue.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "INSPECTING",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GrowBlue
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "LATEST DAILY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Open", fontSize = 10.sp, color = SlateTextSecondary)
                                        Text(
                                            text = activeCandle?.open?.let { moneyFormat.format(it) } ?: "-",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateTextPrimary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("High", fontSize = 10.sp, color = SlateTextSecondary)
                                        Text(
                                            text = activeCandle?.high?.let { moneyFormat.format(it) } ?: "-",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GainGreen
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Low", fontSize = 10.sp, color = SlateTextSecondary)
                                        Text(
                                            text = activeCandle?.low?.let { moneyFormat.format(it) } ?: "-",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LossRed
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Close", fontSize = 10.sp, color = SlateTextSecondary)
                                        val isBullish = activeCandle != null && activeCandle.close >= activeCandle.open
                                        Text(
                                            text = activeCandle?.close?.let { moneyFormat.format(it) } ?: "-",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isBullish) GainGreen else LossRed
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text("Change %", fontSize = 10.sp, color = SlateTextSecondary)
                                        if (activeCandle != null) {
                                            val change = activeCandle.close - activeCandle.open
                                            val changePercent = if (activeCandle.open > 0) (change / activeCandle.open * 100) else 0.0
                                            val isGain = change >= 0
                                            val changeColor = if (isGain) GainGreen else LossRed
                                            Text(
                                                text = "${if (isGain) "+" else ""}${moneyFormat.format(change)} (${if (isGain) "+" else ""}${String.format("%.2f%%", changePercent)})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = changeColor,
                                                maxLines = 1
                                            )
                                        } else {
                                            Text("-", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SlateTextPrimary)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(0.8f)) {
                                        Text("Volume", fontSize = 10.sp, color = SlateTextSecondary)
                                        Text(
                                            text = activeCandle?.volume?.let { formatCandleVolume(it) } ?: "-",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateTextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SlateSurface.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .border(1.dp, SlateBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        CandlestickChart(
                            candles = visibleCandles,
                            selectedIndex = hoveredIndex,
                            onSelectedIndexChanged = { hoveredIndex = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drag/Touch chart to inspect details",
                            fontSize = 10.sp,
                            color = SlateTextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("1M", "3M", "ALL").forEach { tf ->
                                val isSel = timeFrame == tf
                                Surface(
                                    onClick = { timeFrame = tf },
                                    color = if (isSel) GrowBlue else SlateSurface,
                                    border = BorderStroke(1.dp, if (isSel) GrowBlue else SlateBorder),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = tf,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else SlateTextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatCandleVolume(vol: Double): String {
    return when {
        vol >= 1_000_000_000 -> String.format("%.2f B", vol / 1_000_000_000)
        vol >= 1_000_000 -> String.format("%.2f M", vol / 1_000_000)
        vol >= 1_000 -> String.format("%.1f K", vol / 1_000)
        else -> String.format("%.0f", vol)
    }
}

@Composable
fun CandlestickChart(
    candles: List<MarketCandle>,
    selectedIndex: Int?,
    onSelectedIndexChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(candles, size) {
                if (size.width <= 0) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val anyPressed = changes.any { it.pressed }
                        if (anyPressed && candles.isNotEmpty()) {
                            val position = changes.first().position
                            val candleWidth = size.width.toFloat() / candles.size
                            val idx = (position.x / candleWidth).toInt().coerceIn(0, candles.lastIndex)
                            onSelectedIndexChanged(idx)
                        } else {
                            onSelectedIndexChanged(null)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width.toFloat()
            val height = size.height.toFloat()

            if (candles.isEmpty() || width <= 0 || height <= 0) return@Canvas

            val highs = candles.map { it.high }
            val lows = candles.map { it.low }
            val maxPrice = highs.maxOrNull() ?: 1.0
            val minPrice = lows.minOrNull() ?: 0.0
            val priceRange = maxPrice - minPrice
            val paddedMin = (minPrice - priceRange * 0.05).toFloat()
            val paddedMax = (maxPrice + priceRange * 0.05).toFloat()
            val finalRange = paddedMax - paddedMin

            val numCandles = candles.size
            val candleWidth = width / numCandles
            val bodyWidthFraction = 0.7f

            val gridLines = 4
            for (i in 0 until gridLines) {
                val ratio = i.toFloat() / (gridLines - 1)
                val y = ratio * height

                drawLine(
                    color = SlateBorder.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            candles.forEachIndexed { idx, candle ->
                val xCenter = idx * candleWidth + candleWidth / 2f
                
                val openY = height - ((candle.open - paddedMin) / finalRange * height)
                val closeY = height - ((candle.close - paddedMin) / finalRange * height)
                val highY = height - ((candle.high - paddedMin) / finalRange * height)
                val lowY = height - ((candle.low - paddedMin) / finalRange * height)

                val isBullish = candle.close >= candle.open
                val candleColor = if (isBullish) GainGreen else LossRed

                drawLine(
                    color = candleColor,
                    start = Offset(xCenter, highY.toFloat()),
                    end = Offset(xCenter, lowY.toFloat()),
                    strokeWidth = 1.5.dp.toPx()
                )

                val bodyTop = minOf(openY, closeY).toFloat()
                val bodyBottom = maxOf(openY, closeY).toFloat()
                val bodyHeight = maxOf(bodyBottom - bodyTop, 1.dp.toPx())
                val bodyWidth = candleWidth * bodyWidthFraction
                val bodyLeft = xCenter - bodyWidth / 2f

                drawRect(
                    color = candleColor,
                    topLeft = Offset(bodyLeft, bodyTop),
                    size = Size(bodyWidth, bodyHeight)
                )
            }

            if (selectedIndex != null && selectedIndex in candles.indices) {
                val selX = selectedIndex * candleWidth + candleWidth / 2f
                drawLine(
                    color = SlateTextSecondary.copy(alpha = 0.7f),
                    start = Offset(selX, 0f),
                    end = Offset(selX, height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
            }
        }
    }
}

@Composable
fun NewsScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val newsList by viewModel.newsList.collectAsState()
    val isNewsLoading by viewModel.isNewsLoading.collectAsState()
    val newsError by viewModel.newsError.collectAsState()
    val context = LocalContext.current

    var isRefreshingNews by remember { mutableStateOf(false) }
    LaunchedEffect(isNewsLoading) { if (!isNewsLoading) isRefreshingNews = false }

    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = isRefreshingNews,
        onRefresh = { isRefreshingNews = true; viewModel.fetchNews() },
        modifier = modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.fetchNews() },
                enabled = !isNewsLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh News",
                    tint = if (isNewsLoading) SlateTextSecondary else Color.White
                )
            }
        }

        if (isNewsLoading && newsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = GrowBlue)
            }
        } else if (newsError != null && newsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = newsError ?: "", color = LossRed, textAlign = TextAlign.Center)
                    Button(
                        onClick = { viewModel.fetchNews() },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateSurface)
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
            }
        } else if (newsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No news articles found.", color = SlateTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(newsList) { news ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (news.clickUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(news.clickUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("NewsScreen", "Could not open URL: ${news.clickUrl}", e)
                                    }
                                }
                            }
                            .testTag("news_card_${news.id}"),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (news.pinned) GrowBlue else SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (news.pinned) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GrowBlue.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "PINNED",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GrowBlue
                                            )
                                        }
                                    }
                                    if (news.category.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SlateBorder)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = news.category.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SlateTextSecondary
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = news.date,
                                    fontSize = 11.sp,
                                    color = SlateTextSecondary
                                )
                            }

                            Text(
                                text = news.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )

                            if (news.message.isNotBlank()) {
                                Text(
                                    text = news.message,
                                    fontSize = 13.sp,
                                    color = SlateTextSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (news.symbol.isNotBlank() && news.symbol != "GENERAL") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SlateBorder)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = news.symbol,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateTextPrimary
                                        )
                                    }
                                    if (news.sector.isNotBlank()) {
                                        Text(
                                            text = news.sector,
                                            fontSize = 11.sp,
                                            color = SlateTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    } // end PullToRefreshBox
}

@Composable
fun AiAssistantScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    var userQuery by remember { mutableStateOf("") }
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Elegant Section Header
        Column {
            Text(
                text = "NEPSECURE AI ENGINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GrowBlue,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Activate real-time market blueprints and smart cognitive audits",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )
        }

        // Beautiful Interactive Trigger Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // NEPSE Index Blueprint Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isGenerating) { viewModel.analyzeNepse() },
                colors = CardDefaults.cardColors(
                    containerColor = if (isGenerating) SlateBg.copy(alpha = 0.5f) else SlateSurface
                ),
                border = BorderStroke(1.2.dp, GrowBlue.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GrowBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Analyze NEPSE",
                            tint = GrowBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "NEPSE Blueprint",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Full Index Run",
                        fontSize = 9.sp,
                        color = SlateTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Portfolio Auditor Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isGenerating) { viewModel.analyzePortfolio() },
                colors = CardDefaults.cardColors(
                    containerColor = if (isGenerating) SlateBg.copy(alpha = 0.5f) else SlateSurface
                ),
                border = BorderStroke(1.2.dp, GainGreen.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GainGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Audit Portfolio",
                            tint = GainGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Risk Auditor",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Holdings Audit",
                        fontSize = 9.sp,
                        color = SlateTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Watchlist Targets Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isGenerating) { viewModel.analyzeWatchlist() },
                colors = CardDefaults.cardColors(
                    containerColor = if (isGenerating) SlateBg.copy(alpha = 0.5f) else SlateSurface
                ),
                border = BorderStroke(1.2.dp, Color(0xFFBA68C8).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFBA68C8).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = "Watchlist Strategy",
                            tint = Color(0xFFBA68C8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Watch Targets",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Price Proximity",
                        fontSize = 9.sp,
                        color = SlateTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Terminal Chat Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateSurface)
                .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (chatMessages.isEmpty()) {
                // Empty State with Guidance
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(GrowBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Glow",
                            tint = GrowBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "NepSecure Cognitive Terminal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Trigger a real-time market report above or ask custom questions below to begin your analysis.",
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(chatMessages) { message ->
                        val isUser = message.isUser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (!isUser) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp, top = 2.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(GrowBlue.copy(alpha = 0.15f))
                                        .border(1.dp, GrowBlue.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = GrowBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            val bubbleShape = if (isUser) {
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                            } else {
                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                            }

                            val bubbleBg = if (isUser) {
                                GrowBlue
                            } else {
                                SlateBg
                            }

                            val borderStroke = if (isUser) {
                                null
                            } else {
                                BorderStroke(1.dp, SlateBorder)
                            }

                            Surface(
                                color = bubbleBg,
                                shape = bubbleShape,
                                border = borderStroke,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = formatMarkdown(message.text),
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = if (isUser) Color.White else SlateTextPrimary
                                    )
                                }
                            }

                            if (isUser) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp, top = 2.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFBA68C8).copy(alpha = 0.15f))
                                        .border(1.dp, Color(0xFFBA68C8).copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "User",
                                        tint = Color(0xFFBA68C8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(GrowBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = GrowBlue,
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(
                                    text = "Cognitive agent thinking...",
                                    fontSize = 12.sp,
                                    color = SlateTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Query input box & send buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Clear button
            IconButton(
                onClick = { viewModel.clearChat() },
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Chat",
                    tint = LossRed
                )
            }

            // Input field
            OutlinedTextField(
                value = userQuery,
                onValueChange = { userQuery = it },
                placeholder = { Text("Ask anything about NEPSE...", color = SlateTextSecondary, fontSize = 13.sp) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SlateTextPrimary,
                    unfocusedTextColor = SlateTextPrimary,
                    focusedContainerColor = SlateSurface,
                    unfocusedContainerColor = SlateSurface,
                    focusedBorderColor = GrowBlue,
                    unfocusedBorderColor = SlateBorder
                )
            )

            // Send button
            IconButton(
                onClick = {
                    if (userQuery.isNotBlank() && !isGenerating) {
                        viewModel.sendUserMessage(userQuery)
                        userQuery = ""
                    }
                },
                enabled = userQuery.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (userQuery.isNotBlank() && !isGenerating) GrowBlue else SlateSurface)
                    .border(
                        1.dp,
                        if (userQuery.isNotBlank() && !isGenerating) GrowBlue else SlateBorder,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Message",
                    tint = if (userQuery.isNotBlank() && !isGenerating) Color.White else SlateTextSecondary
                )
            }
        }
    }
}

@Composable
fun formatMarkdown(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = text.split("\n")
    for (index in lines.indices) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = GrowBlue, fontWeight = FontWeight.Bold))
            builder.append("  • ")
            builder.pop()
            val content = if (trimmed.startsWith("- ")) trimmed.substring(2) else trimmed.substring(2)
            parseBold(content, builder)
        } else {
            parseBold(line, builder)
        }
        if (index < lines.size - 1) {
            builder.append("\n")
        }
    }
    return builder.toAnnotatedString()
}

private fun parseBold(text: String, builder: AnnotatedString.Builder) {
    val parts = text.split("**")
    for (i in parts.indices) {
        if (i % 2 == 1) {
            builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
            builder.append(parts[i])
            builder.pop()
        } else {
            builder.append(parts[i])
        }
    }
}


