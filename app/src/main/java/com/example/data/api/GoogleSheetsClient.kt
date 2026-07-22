package com.example.data.api

import android.util.Log
import com.example.data.database.CurrentHolding
import com.example.data.database.PortfolioHistory
import com.example.data.database.StockItem
import com.example.data.database.WatchStock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object GoogleSheetsClient {
    private val client = OkHttpClient()

    var e5Value: Double? = null
    var o5Value: Double? = null
    var nepseStatus: String? = null
    var nepseDateTime: String? = null

    private fun parseCsvRow(row: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun fetchCsv(spreadsheetId: String, sheetName: String, accessToken: String? = null): List<List<String>> {
        val encodedSheetName = java.net.URLEncoder.encode(sheetName, "UTF-8")
        
        if (!accessToken.isNullOrBlank()) {
            try {
                // Use the official Google Sheets API v4 (JSON values endpoint) which supports standard OAuth 2.0 Bearer tokens
                val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedSheetName"
                Log.d("SheetsClient", "Fetching from Sheets API: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    Log.d("SheetsClient", "Sheets API response code: ${response.code}, body: $bodyString")
                    
                    if (response.code == 401) {
                        throw IOException("HTTP 401 Unauthorized. Your login session might have expired. Please disconnect and reconnect your Google Account in Settings.")
                    }
                    if (response.code == 403) {
                        val detail = if (bodyString.contains("disabled", ignoreCase = true) || bodyString.contains("not been used", ignoreCase = true)) {
                            "Google Sheets API is disabled in the Google Cloud project. Please enable the Google Sheets API in your Google Cloud Console."
                        } else if (bodyString.contains("permission", ignoreCase = true)) {
                            "Your connected Google Account does not have permission to access this spreadsheet. Please make sure the sheet is shared with your account."
                        } else {
                            "Details: " + (if (bodyString.length > 200) bodyString.take(200) + "..." else bodyString)
                        }
                        throw IOException("HTTP 403 Forbidden. $detail")
                    }
                    if (response.code == 404) {
                        throw IOException("HTTP 404 Spreadsheet Not Found. Verify that your Spreadsheet ID is correct and the sheet tab '$sheetName' exists.")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("HTTP error ${response.code} during Sheets API fetch. Details: ${bodyString.take(200)}")
                    }
                    
                    try {
                        val jsonObject = org.json.JSONObject(bodyString)
                        val valuesArray = jsonObject.optJSONArray("values") ?: return emptyList()
                        val result = mutableListOf<List<String>>()
                        for (i in 0 until valuesArray.length()) {
                            val rowArray = valuesArray.optJSONArray(i)
                            val rowList = mutableListOf<String>()
                            if (rowArray != null) {
                                        for (j in 0 until rowArray.length()) {
                                            rowList.add(rowArray.optString(j, ""))
                                        }
                            }
                            result.add(rowList)
                        }
                        return result
                    } catch (e: Exception) {
                        Log.e("SheetsClient", "Failed to parse Sheets API JSON response", e)
                        throw IOException("Failed to parse Sheets API response: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.w("SheetsClient", "Authenticated Sheets API fetch failed: ${e.message}. Attempting authenticated web CSV export fallback...", e)
                try {
                    return fetchAuthenticatedCsv(spreadsheetId, encodedSheetName, sheetName, accessToken)
                } catch (fallbackEx: Exception) {
                    Log.e("SheetsClient", "Authenticated web CSV export fallback also failed", fallbackEx)
                    // If fallback also fails, throw the original authenticated exception to help the user debug their private sheet permissions/APIs
                    throw e
                }
            }
        } else {
            return fetchPublicCsv(spreadsheetId, encodedSheetName, sheetName)
        }
    }

    private fun fetchAuthenticatedCsv(spreadsheetId: String, encodedSheetName: String, originalSheetName: String, accessToken: String): List<List<String>> {
        // Try docs.google.com export endpoint with Bearer token
        val exportUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&sheet=$encodedSheetName"
        Log.d("SheetsClient", "Fetching from authenticated export endpoint: $exportUrl")
        
        try {
            val request = Request.Builder()
                .url(exportUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (!bodyString.contains("<!doctype html>", ignoreCase = true) && !bodyString.contains("<html", ignoreCase = true)) {
                        val lines = bodyString.split(Regex("\\r?\\n"))
                        return lines.filter { it.isNotBlank() }.map { parseCsvRow(it) }
                    } else {
                        Log.w("SheetsClient", "Authenticated export returned HTML instead of CSV.")
                    }
                } else {
                    Log.w("SheetsClient", "Authenticated export failed with HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (ex: Exception) {
            Log.w("SheetsClient", "Authenticated export request exception: ${ex.message}", ex)
        }
        
        // Fallback: Try gviz/tq endpoint with Bearer token
        val gvizUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=$encodedSheetName"
        Log.d("SheetsClient", "Fetching from authenticated gviz endpoint: $gvizUrl")
        
        try {
            val gvizRequest = Request.Builder()
                .url(gvizUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
                
            client.newCall(gvizRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (!bodyString.contains("<!doctype html>", ignoreCase = true) && !bodyString.contains("<html", ignoreCase = true)) {
                        val lines = bodyString.split(Regex("\\r?\\n"))
                        return lines.filter { it.isNotBlank() }.map { parseCsvRow(it) }
                    } else {
                        Log.w("SheetsClient", "Authenticated gviz returned HTML instead of CSV.")
                    }
                } else {
                    Log.w("SheetsClient", "Authenticated gviz failed with HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (ex: Exception) {
            Log.w("SheetsClient", "Authenticated gviz request exception: ${ex.message}", ex)
        }
        
        throw IOException("Authenticated web CSV export failed. Please verify that your Google Account has permissions to access this sheet.")
    }

    private fun fetchPublicCsv(spreadsheetId: String, encodedSheetName: String, originalSheetName: String): List<List<String>> {
        // Fallback: Use gviz endpoint for public sheets
        val url = "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=$encodedSheetName"
        Log.d("SheetsClient", "Fetching from gviz endpoint (public mode): $url")
        
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw IOException("HTTP 401 Unauthorized. This spreadsheet is private. Please sign in with Google in Settings to access it.")
            }
            if (response.code == 404) {
                throw IOException("HTTP 404 Spreadsheet Not Found. Verify that your Spreadsheet ID is correct and the sheet tab '$originalSheetName' exists.")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code} during sheet fetch.")
            }
            val bodyString = response.body?.string() ?: return emptyList()
            
            // Check if Google redirected to a login/unauthorized HTML page instead of returning CSV
            if (bodyString.contains("<!doctype html>", ignoreCase = true) || bodyString.contains("<html", ignoreCase = true)) {
                Log.e("SheetsClient", "HTML received instead of CSV: ${bodyString.take(300)}")
                throw IOException("Authentication required. This spreadsheet is private or restricted. Please connect your authorized Google Account in Settings.")
            }

            val lines = bodyString.split(Regex("\\r?\\n"))
            return lines.filter { it.isNotBlank() }.map { parseCsvRow(it) }
        }
    }

    fun cleanDouble(valueStr: String?): Double? {
        if (valueStr == null) return null
        val cleaned = valueStr.trim()
            .replace("Rs", "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("NPR", "", ignoreCase = true)
            .replace("₹", "")
            .replace("$", "")
            .replace(",", "")
            .replace("%", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun getSectorFromTicker(ticker: String): String {
        val upper = ticker.uppercase().trim()
        return when {
            // Commercial Banks
            upper in listOf("GBIME", "KBL", "HBL", "Nabil", "NABIL", "NICA", "SCB", "EBL", "MBL", "SBL", "PRVU", "ADBL", "BOKL", "CCBL", "CZBIL", "LBL", "NMB", "PCBL", "SANIMA", "SBI") -> "Commercial Banks"
            
            // Microfinance
            upper.endsWith("BSL") || upper.endsWith("LBS") || upper.endsWith("BS") || upper.endsWith("MF") || 
            upper in listOf("CBBL", "DDBL", "ALBSL", "ANLB", "GBLBS", "JSLBB", "MSLB", "FMDBL", "SKBBL", "NUBL", "NICLBSL", "FMDDBL", "LLBS", "MLBBL", "MMFDB") -> "Microfinance"
            
            // Hydropower
            upper in listOf("CHCL", "UPPER", "SMHL", "HPPL", "MKHC", "SHPC", "BPCL", "AKPL", "API", "BARUN", "GLH", "HDHPC", "HPWR", "HURJA", "KPCL", "LEC", "MEN", "NGPL", "NHDL", "PMHPL", "RADHI", "RHPL", "RRHP", "SHEL", "SPDL", "SSHL", "UMHL", "USHEC") -> "Hydropower"
            
            // Development Banks
            upper in listOf("EDBL", "FMDBL", "GRDBL", "GBBL", "JBBL", "KSBBL", "MDB", "SADBL", "SHINE", "SINHE") -> "Development Banks"
            
            // Life Insurance
            upper.contains("LI") || upper in listOf("LICN", "NLIC", "ALICL", "PLI", "ELI", "JLI", "SLI", "SULI") -> "Life Insurance"
            
            // Non-Life Insurance
            upper in listOf("NLG", "NIL", "NICL", "PRIN", "SICL", "LGIL", "SGI", "IGI", "GICL", "UIC", "Neco", "NECO") -> "Non-Life Insurance"
            
            // Finance
            upper.endsWith("F") || upper in listOf("CIT", "CFCL", "GFCL", "ICFC", "MFIL", "RLFL", "SFCL", "GMFIL") -> "Finance"
            
            // Investment
            upper in listOf("HATHY", "NIFRA", "ENL", "HIDCL", "CGH") -> "Investment"
            
            // Manufacturing & Processing
            upper in listOf("HDL", "UNL", "BNT", "SHIVM", "SARBTM", "GCIL") -> "Manufacturing & Processing"
            
            // Hotels & Tourism
            upper in listOf("SHL", "OHL", "TRH", "CGH") -> "Hotels & Tourism"
            
            // Others
            else -> "Others"
        }
    }

    fun fetchCurrentHoldings(spreadsheetId: String, accessToken: String? = null): List<CurrentHolding> {
        val rows = fetchCsv(spreadsheetId, "Current", accessToken)
        if (rows.isEmpty()) return emptyList()

        // Extract E5 (row index 4, col index 4) and O5 (row index 4, col index 14)
        // Extract A1 (NEPSE status) and A2 (date/time) from the sheet
        val a1Raw = rows.getOrNull(0)?.getOrNull(0)?.replace("\"", "")?.trim()
        val a2Raw = rows.getOrNull(1)?.getOrNull(0)?.replace("\"", "")?.trim()
        nepseStatus = if (!a1Raw.isNullOrBlank()) a1Raw else null
        nepseDateTime = if (!a2Raw.isNullOrBlank()) a2Raw else null
        Log.d("SheetsClient", "Extracted A1 (status): $nepseStatus, A2 (datetime): $nepseDateTime")

        val row5 = rows.getOrNull(4)
        val e5Raw = row5?.getOrNull(4)
        val o5Raw = row5?.getOrNull(14)
        
        e5Value = cleanDouble(e5Raw)
        o5Value = cleanDouble(o5Raw)
        Log.d("SheetsClient", "Extracted E5: $e5Value (raw: $e5Raw), O5: $o5Value (raw: $o5Raw)")

        var tickerIdx = 0
        var sharesIdx = 1
        var avgPriceIdx = 5
        var currentPriceIdx = 6
        var marketValueIdx = 7
        var gainLossIdx = 11
        var gainLossPercentIdx = 12

        // Scan all rows to find header positions dynamically
        for (row in rows) {
            val lowercaseRow = row.map { it.lowercase().replace(" ", "").replace("_", "").replace("\"", "") }
            lowercaseRow.forEachIndexed { index, cell ->
                if (cell == "symbol" || cell == "ticker") tickerIdx = index
                if (cell == "quantity" || cell == "qty" || cell == "shares") sharesIdx = index
                if (cell == "wacc" || cell == "avgprice" || cell == "averageprice" || cell == "costprice") avgPriceIdx = index
                if (cell == "marketprice" || cell == "currentprice" || cell == "ltp") currentPriceIdx = index
                if (cell == "portfoliovalue" || cell == "marketvalue") marketValueIdx = index
                if (cell == "currentprofit/loss" || (cell == "profit/loss" && gainLossIdx == 11)) gainLossIdx = index
                if (cell == "profit/loss%" || cell == "gainloss%") gainLossPercentIdx = index
            }
        }

        Log.d("SheetsClient", "Dynamic Current Mapping -> ticker: $tickerIdx, shares: $sharesIdx, avg: $avgPriceIdx, price: $currentPriceIdx, mv: $marketValueIdx, gainLoss: $gainLossIdx, pct: $gainLossPercentIdx")

        val list = mutableListOf<CurrentHolding>()
        for (row in rows) {
            if (row.size <= tickerIdx) continue
            val ticker = row[tickerIdx].replace("\"", "").trim()
            if (ticker.isBlank()) continue

            val upperTicker = ticker.uppercase()
            // Robust filter for Nepalese index/metadata/header rows
            if (upperTicker == "SYMBOL" || upperTicker == "TICKER" || upperTicker == "TOTAL" || 
                upperTicker.startsWith("NEPSE") || upperTicker.contains("INDEX") || upperTicker.contains("MARKET") ||
                upperTicker.contains("CLOSED") || upperTicker.contains("OPEN") || upperTicker.contains("DATE") ||
                upperTicker.contains("TIME") || upperTicker.contains("PORTFOLIO") || upperTicker.contains("FOUND") ||
                upperTicker.contains("|") || upperTicker.contains("NEP") || upperTicker.contains("BANKING") || 
                upperTicker.contains("DEVELOP") || upperTicker.contains("FINANCE") || upperTicker.contains("INSUR") ||
                upperTicker.contains("MICRO") || upperTicker.contains("MUTUAL") || upperTicker.contains("HOTEL") ||
                upperTicker.contains("HYDRO") || upperTicker.contains("MANU") || upperTicker.contains("NONLIFE") ||
                upperTicker.contains("OTHER") || upperTicker.contains("SYMBOL NOT FOUND") || ticker.matches(Regex(".*\\d{4}-\\d{2}-\\d{2}.*"))
            ) {
                continue
            }

            try {
                val shares = cleanDouble(row.getOrNull(sharesIdx)) ?: 0.0
                if (shares <= 0.0) continue

                val avgPrice = cleanDouble(row.getOrNull(avgPriceIdx)) ?: 0.0
                val currentPrice = cleanDouble(row.getOrNull(currentPriceIdx)) ?: 0.0
                
                val marketValue = cleanDouble(row.getOrNull(marketValueIdx)) ?: (shares * currentPrice)
                val gainLoss = cleanDouble(row.getOrNull(gainLossIdx)) ?: (marketValue - (shares * avgPrice))
                val gainLossPercent = cleanDouble(row.getOrNull(gainLossPercentIdx)) ?: (if (avgPrice > 0.0) (currentPrice - avgPrice) / avgPrice * 100.0 else 0.0)

                list.add(
                    CurrentHolding(
                        ticker = upperTicker,
                        name = upperTicker,
                        shares = shares,
                        avgPrice = avgPrice,
                        currentPrice = currentPrice,
                        marketValue = marketValue,
                        gainLoss = gainLoss,
                        gainLossPercent = gainLossPercent
                    )
                )
            } catch (e: Exception) {
                Log.e("SheetsClient", "Error parsing current holdings row: $row", e)
            }
        }
        return list
    }

    fun fetchStockList(spreadsheetId: String, accessToken: String? = null): List<StockItem> {
        val rows = fetchCsv(spreadsheetId, "Stocks", accessToken)
        if (rows.isEmpty()) return emptyList()

        val headers = rows[0].map { it.lowercase().replace(" ", "").replace("_", "").replace("\"", "") }
        
        var tickerIdx = 0
        var priceIdx = 1
        var changeIdx = 3
        var changePercentIdx = 4
        var volumeIdx = 8
        var sectorIdx = -1
        var openIdx = -1
        var highIdx = -1
        var lowIdx = -1
        var prevCloseIdx = -1

        headers.forEachIndexed { index, cell ->
            if (cell == "symbol" || cell == "ticker") tickerIdx = index
            if (cell.contains("ltp") || cell == "price" || cell == "currentprice") priceIdx = index
            if (cell.contains("pointchange") || (cell.contains("change") && !cell.contains("percent") && !cell.contains("%"))) changeIdx = index
            if (cell.contains("percentagechange") || cell.contains("changepercent") || cell.contains("percent") || cell.contains("%")) changePercentIdx = index
            if (cell == "volume" || cell == "#volume" || cell.contains("totalvolume")) volumeIdx = index
            if (cell.contains("sector") || cell.contains("industry")) sectorIdx = index
            if (cell == "open" || cell.contains("openprice") || cell.contains("opening")) openIdx = index
            if (cell == "high" || cell.contains("highprice") || cell.contains("max") || cell == "maxprice") highIdx = index
            if (cell == "low" || cell.contains("lowprice") || cell.contains("min") || cell == "minprice") lowIdx = index
            if (cell.contains("prev") || cell.contains("yesterday") || cell == "previousclose") prevCloseIdx = index
        }

        Log.d("SheetsClient", "Dynamic StockList Mapping -> ticker: $tickerIdx, price: $priceIdx, change: $changeIdx, pct: $changePercentIdx, vol: $volumeIdx, sector: $sectorIdx, open: $openIdx, high: $highIdx, low: $lowIdx, prevClose: $prevCloseIdx")

        val list = mutableListOf<StockItem>()
        val dataRows = rows.drop(1)
        for (row in dataRows) {
            if (row.size <= tickerIdx) continue
            val ticker = row[tickerIdx].replace("\"", "").trim()
            if (ticker.isBlank()) continue

            val upperTicker = ticker.uppercase()
            if (upperTicker == "SYMBOL" || upperTicker == "TICKER" || upperTicker == "TOTAL" || upperTicker.contains("|")) {
                continue
            }

            try {
                val price = cleanDouble(row.getOrNull(priceIdx)) ?: 0.0
                val change = cleanDouble(row.getOrNull(changeIdx)) ?: 0.0
                val changePercent = cleanDouble(row.getOrNull(changePercentIdx)) ?: 0.0
                val volume = cleanDouble(row.getOrNull(volumeIdx))?.toLong() ?: 0L
                val sector = if (sectorIdx != -1) {
                    row.getOrNull(sectorIdx)?.replace("\"", "") ?: "Other"
                } else {
                    getSectorFromTicker(upperTicker)
                }

                val parsedOpen = if (openIdx != -1) cleanDouble(row.getOrNull(openIdx)) else null
                val parsedHigh = if (highIdx != -1) cleanDouble(row.getOrNull(highIdx)) else null
                val parsedLow = if (lowIdx != -1) cleanDouble(row.getOrNull(lowIdx)) else null
                val parsedPrevClose = if (prevCloseIdx != -1) cleanDouble(row.getOrNull(prevCloseIdx)) else null

                val prevCloseVal = parsedPrevClose ?: (price - change)
                val openVal = parsedOpen ?: prevCloseVal
                val highVal = parsedHigh ?: maxOf(price, openVal)
                val lowVal = parsedLow ?: minOf(price, openVal)

                list.add(
                    StockItem(
                        ticker = upperTicker,
                        name = upperTicker,
                        price = price,
                        change = change,
                        changePercent = changePercent,
                        sector = sector,
                        volume = volume,
                        open = openVal,
                        high = highVal,
                        low = lowVal,
                        prevClose = prevCloseVal
                    )
                )
            } catch (e: Exception) {
                Log.e("SheetsClient", "Error parsing stock list row: $row", e)
            }
        }
        return list
    }

    fun fetchPortfolioHistory(spreadsheetId: String, accessToken: String? = null): List<PortfolioHistory> {
        val rows = fetchCsv(spreadsheetId, "PortfolioHistory", accessToken)
        if (rows.isEmpty()) return emptyList()

        val headers = rows[0].map { it.lowercase().replace(" ", "").replace("_", "").replace("\"", "") }
        val dataRows = rows.drop(1)

        val dateIdx = headers.indexOfFirst { it.contains("date") || it.contains("time") }.coerceAtLeast(0)
        val valueIdx = headers.indexOfFirst { it.contains("value") || it.contains("balance") || it.contains("total") }.coerceAtLeast(1)

        return dataRows.mapNotNull { row ->
            try {
                if (row.size <= dateIdx || row[dateIdx].isBlank()) return@mapNotNull null
                val date = row[dateIdx].replace("\"", "")
                val value = cleanDouble(row.getOrNull(valueIdx)) ?: 0.0

                PortfolioHistory(
                    date = date,
                    value = value
                )
            } catch (e: Exception) {
                Log.e("SheetsClient", "Error parsing history row: $row", e)
                null
            }
        }
    }

    fun fetchWatchList(spreadsheetId: String, accessToken: String? = null): List<WatchStock> {
        val rows = fetchCsv(spreadsheetId, "Watch List", accessToken)
        if (rows.isEmpty()) return emptyList()

        val headers = rows[0].map { it.lowercase().replace(" ", "").replace("_", "").replace("\"", "") }
        val dataRows = rows.drop(1)

        var tickerIdx = 0
        var currentPriceIdx = 1
        var changePercentIdx = 4
        var targetPriceIdx = -1
        var notesIdx = -1

        headers.forEachIndexed { index, cell ->
            if (cell == "symbol" || cell == "ticker") tickerIdx = index
            if (cell.contains("ltp") || cell == "price" || cell == "currentprice") currentPriceIdx = index
            if (cell.contains("percentagechange") || cell.contains("changepercent") || cell.contains("percent") || cell.contains("%")) changePercentIdx = index
            if (cell.contains("target")) targetPriceIdx = index
            if (cell.contains("notes") || cell.contains("comment")) notesIdx = index
        }

        return dataRows.mapNotNull { row ->
            try {
                if (row.size <= tickerIdx || row[tickerIdx].isBlank()) return@mapNotNull null
                val ticker = row[tickerIdx].replace("\"", "").trim()
                val upperTicker = ticker.uppercase()
                if (upperTicker == "SYMBOL" || upperTicker == "TICKER" || upperTicker.contains("|")) return@mapNotNull null

                val currentPrice = cleanDouble(row.getOrNull(currentPriceIdx)) ?: 0.0
                val changePercent = cleanDouble(row.getOrNull(changePercentIdx)) ?: 0.0
                val targetPrice = if (targetPriceIdx != -1) cleanDouble(row.getOrNull(targetPriceIdx)) ?: currentPrice else currentPrice * 0.95
                
                val notes = if (notesIdx != -1) {
                    row.getOrNull(notesIdx)?.replace("\"", "") ?: ""
                } else {
                    val formattedChange = String.format("%.2f%%", changePercent)
                    "Daily change: $formattedChange"
                }

                WatchStock(
                    ticker = upperTicker,
                    name = upperTicker,
                    targetPrice = targetPrice,
                    currentPrice = currentPrice,
                    notes = notes
                )
            } catch (e: Exception) {
                Log.e("SheetsClient", "Error parsing watch list row: $row", e)
                null
            }
        }
    }
}
