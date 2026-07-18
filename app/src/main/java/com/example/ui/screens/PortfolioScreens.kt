package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import android.util.Log
import com.example.data.database.CurrentHolding
import com.example.data.database.PortfolioHistory
import com.example.data.database.StockItem
import com.example.data.database.WatchStock
import com.example.ui.PortfolioViewModel
import com.example.ui.SyncState
import com.example.ui.theme.*
import java.text.DecimalFormat

private val moneyFormat = DecimalFormat("Rs #,##0.00")
private val changeFormat = DecimalFormat("+0.00;-0.00")
private val percentFormat = DecimalFormat("+0.00%;-0.00%")

@Composable
fun DashboardScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val holdings by viewModel.currentHoldings.collectAsState()
    val stocks by viewModel.stockList.collectAsState()
    val history by viewModel.portfolioHistory.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()

    var holdingsSearchQuery by remember { mutableStateOf("") }
    var holdingsSortBy by remember { mutableStateOf("Value") }
    var holdingsSortOrderDesc by remember { mutableStateOf(true) }
    var holdingsFilterPerformance by remember { mutableStateOf("All") }
    var holdingsFilterExpanded by remember { mutableStateOf(false) }

    val filteredHoldings = remember(holdings, holdingsSearchQuery, holdingsSortBy, holdingsSortOrderDesc, holdingsFilterPerformance) {
        var result = holdings.filter {
            it.ticker.contains(holdingsSearchQuery, ignoreCase = true) ||
                    it.name.contains(holdingsSearchQuery, ignoreCase = true)
        }

        result = when (holdingsFilterPerformance) {
            "Profitable" -> result.filter { it.gainLoss > 0 }
            "In the Red" -> result.filter { it.gainLoss < 0 }
            else -> result
        }

        when (holdingsSortBy) {
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Balance Card (Portfolio Value)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
                        Text(
                            text = "PORTFOLIO VALUE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 1.5.sp
                        )
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = moneyFormat.format(totalValue),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SlateTextPrimary
                    )
                }
            }
        }

        // Metrics Grid Card (Current Investment, Today's Difference, Current Profit/Loss)
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                                text = moneyFormat.format(totalCost),
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
                                .padding(horizontal = 8.dp)
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
                                text = "${if (isGain) "+" else ""}${moneyFormat.format(totalGainLoss)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Text(
                                text = "${if (isGain) "+" else ""}${String.format("%.2f%%", totalGainPercent * 100)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                    }

                    HorizontalDivider(color = SlateBorder, thickness = 0.8.dp)

                    // Today's Difference Row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "TODAY'S DIFFERENCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val isTodayGain = todayDiff >= 0
                        val todayColor = if (isTodayGain) GainGreen else LossRed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isTodayGain) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = todayColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "${if (isTodayGain) "+" else ""}${moneyFormat.format(todayDiff)} (${if (isTodayGain) "+" else ""}${String.format("%.2f%%", todayPercentChange)})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = todayColor
                            )
                        }
                    }
                }
            }
        }

        // Portfolio History Chart Card
        if (history.isNotEmpty()) {
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
                            text = "PORTFOLIO HISTORIC TREND",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            HistoryLineChart(points = history)
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
                                text = "Performance Trend",
                                fontSize = 10.sp,
                                color = SlateTextSecondary,
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
                                tint = if (holdingsFilterExpanded || holdingsFilterPerformance != "All" || holdingsSortBy != "Value" || holdingsSearchQuery.isNotEmpty()) GrowBlue else SlateTextSecondary
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
                                val sortOptions = listOf("Value", "Gain/Loss", "Ticker", "Shares")
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
                HoldingRowItem(holding = holding, stockItem = matchingStock)
            }
        }
    }
}

@Composable
fun HoldingRowItem(holding: CurrentHolding, stockItem: StockItem?) {
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GrowBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = holding.ticker,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrowBlue
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
                    Text(
                        text = "${holding.shares} Shares @ ${moneyFormat.format(holding.avgPrice)}",
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

                    val isGain = holding.gainLoss >= 0
                    val color = if (isGain) GainGreen else LossRed
                    Text(
                        text = "${if (isGain) "+" else ""}${moneyFormat.format(holding.gainLoss)} (${String.format("%.2f%%", holding.gainLossPercent)})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )

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
                        text = "TODAY'S PERFORMANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (stockItem != null) {
                        val todayChangeVal = stockItem.change * holding.shares
                        val todayIsGain = todayChangeVal >= 0
                        val todayColor = if (todayIsGain) GainGreen else LossRed

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Holding Value Change",
                                    fontSize = 12.sp,
                                    color = SlateTextSecondary
                                )
                                Text(
                                    text = "${if (todayIsGain) "+" else ""}${moneyFormat.format(todayChangeVal)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = todayColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Stock Price Change",
                                    fontSize = 12.sp,
                                    color = SlateTextSecondary
                                )
                                Text(
                                    text = "${if (stockItem.change >= 0) "+" else ""}${moneyFormat.format(stockItem.change)} (${if (stockItem.changePercent >= 0) "+" else ""}${String.format("%.2f%%", stockItem.changePercent)})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = todayColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GridItem(label = "Open", value = moneyFormat.format(stockItem.open))
                                GridItem(label = "High", value = moneyFormat.format(stockItem.high))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GridItem(label = "Low", value = moneyFormat.format(stockItem.low))
                                GridItem(label = "Closing Value (LTP)", value = moneyFormat.format(stockItem.price))
                            }
                        }
                    } else {
                        Text(
                            text = "Sync required or stock data unavailable to view live metrics.",
                            fontSize = 12.sp,
                            color = SlateTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun GridItem(label: String, value: String) {
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
                color = SlateTextPrimary,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Market Directory",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
            Text(
                text = "${filteredStocks.size} stock(s)",
                fontSize = 12.sp,
                color = SlateTextSecondary
            )
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
                    StockRowItem(stock = stock)
                }
            }
        }
    }
}

@Composable
fun StockRowItem(stock: StockItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SlateBorder)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stock.ticker,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
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
    }
}

@Composable
fun WatchListScreen(
    viewModel: PortfolioViewModel,
    modifier: Modifier = Modifier
) {
    val watchlist by viewModel.watchList.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Ticker") }
    var sortOrderDesc by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("All") }
    var filterExpanded by remember { mutableStateOf(false) }

    val filteredWatchlist = remember(watchlist, searchQuery, sortBy, sortOrderDesc, statusFilter) {
        var result = watchlist.filter {
            it.ticker.contains(searchQuery, ignoreCase = true) ||
                    it.name.contains(searchQuery, ignoreCase = true)
        }

        result = when (statusFilter) {
            "Below Target" -> result.filter { it.currentPrice <= it.targetPrice }
            "Above Target" -> result.filter { it.currentPrice > it.targetPrice }
            "Near Target (<= 5%)" -> result.filter {
                if (it.currentPrice > 0.0) {
                    val gap = (it.targetPrice - it.currentPrice) / it.currentPrice
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
                    if (it.currentPrice > 0.0) {
                        (it.targetPrice - it.currentPrice) / it.currentPrice
                    } else 0.0
                }
                if (sortOrderDesc) result.sortedByDescending(selector)
                else result.sortedBy(selector)
            }
            else -> result
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Stock Watchlist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
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
                    WatchRowItem(watch = item)
                }
            }
        }
    }
}

@Composable
fun WatchRowItem(watch: WatchStock) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GrowBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = watch.ticker,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GrowBlue
                            )
                        }
                        Text(
                            text = watch.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateTextPrimary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = moneyFormat.format(watch.currentPrice),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "Current",
                        fontSize = 10.sp,
                        color = SlateTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SlateBorder, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = moneyFormat.format(watch.targetPrice),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "Target Price",
                        fontSize = 10.sp,
                        color = SlateTextSecondary
                    )
                }

                // Gap calculation
                val gapPercent = if (watch.currentPrice > 0) {
                    (watch.targetPrice - watch.currentPrice) / watch.currentPrice * 100
                } else 0.0

                val badgeColor = if (gapPercent >= 0) GainGreen else LossRed
                val badgeText = if (gapPercent >= 0) {
                    "At target (+${String.format("%.1f%%", gapPercent)})"
                } else {
                    "${String.format("%.1f%%", gapPercent)} to target"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            if (watch.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateBg)
                        .padding(10.dp)
                ) {
                    Text(
                        text = watch.notes,
                        fontSize = 12.sp,
                        color = SlateTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
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

    LazyColumn(
        modifier = modifier
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
                            text = "HISTORIC VALUATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextSecondary,
                            letterSpacing = 1.2.sp
                        )

                        val currentVal = history.lastOrNull()?.value ?: 0.0
                        Text(
                            text = moneyFormat.format(currentVal),
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
                            HistoryLineChart(points = history)
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
                                text = "Timeline Performance",
                                fontSize = 10.sp,
                                color = SlateTextSecondary,
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
fun HistoryLineChart(points: List<PortfolioHistory>) {
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

        // 3. Draw dots on nodes
        coordinates.forEachIndexed { idx, point ->
            // Draw outer neon halo for final point
            if (idx == coordinates.lastIndex) {
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
                    color = GrowBlue,
                    radius = 3.dp.toPx(),
                    center = point
                )
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
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets.readonly"))
            .build()
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Status Code ${lastErrorCode ?: 10} (DEVELOPER_ERROR) indicates your Google/Firebase Console project does not recognize the SHA-1 signature of this built application.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateTextPrimary
                    )
                    Text(
                        text = "To resolve this, please register the app details under your project settings in the Firebase Console / Google Cloud Console:",
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
                        text = "2. Build SHA-1 Fingerprint:",
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
                        text = "💡 Steps to Fix:\n1. Copy the SHA-1 signature.\n2. Open Firebase Console -> Project Settings.\n3. Add Android App with Package Name and the SHA-1.\n4. Download the new google-services.json file.\n5. Upload it to AI Studio, then click compile to build the final APK.",
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
        item {
            Text(
                text = "Sheet Integration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

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
                        text = "2. Create exactly these 4 tab sheets with identical names and header columns:",
                        fontSize = 12.sp,
                        color = SlateTextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    GuideTabItem("Current", "Ticker, Name, Shares, Avg Price, Current Price")
                    GuideTabItem("Stocks", "Ticker, Name, Price, Change, Change %, Sector, Volume")
                    GuideTabItem("PortfolioHistory", "Date, Value")
                    GuideTabItem("Watch List", "Ticker, Name, Target Price, Current Price, Notes")

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
