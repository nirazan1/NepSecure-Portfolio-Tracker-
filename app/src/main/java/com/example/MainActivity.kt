package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.PortfolioViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.CandlestickChartDialog
import androidx.compose.runtime.collectAsState
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StockListScreen
import com.example.ui.screens.WatchListScreen
import com.example.ui.screens.NewsScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome

enum class PortfolioTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Portfolio", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "tab_dashboard"),
    STOCK_LIST("Stock List", Icons.Filled.List, Icons.Outlined.List, "tab_stocks"),
    WATCHLIST("Watchlist", Icons.Filled.Star, Icons.Outlined.Star, "tab_watch"),
    AI_CHAT("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_ai"),
    NEWS("News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper, "tab_news"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainLayout()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout() {
    val viewModel: PortfolioViewModel = viewModel()
    var currentTab by remember { mutableStateOf(PortfolioTab.DASHBOARD) }

    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val candleData by viewModel.candleData.collectAsState()
    val isCandleLoading by viewModel.isCandleLoading.collectAsState()
    val candleError by viewModel.candleError.collectAsState()

    if (selectedSymbol != null) {
        CandlestickChartDialog(
            symbol = selectedSymbol!!,
            candles = candleData,
            isLoading = isCandleLoading,
            error = candleError,
            onDismiss = { viewModel.selectSymbol(null) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurface,
                tonalElevation = 8.8.dp, // Or 8.dp
                modifier = Modifier.testTag("app_bottom_nav")
            ) {
                PortfolioTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = SlateTextSecondary,
                            selectedTextColor = Color.White,
                            unselectedTextColor = SlateTextSecondary,
                            indicatorColor = SlateBorder
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateBg)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                PortfolioTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                PortfolioTab.STOCK_LIST -> StockListScreen(viewModel = viewModel)
                PortfolioTab.WATCHLIST -> WatchListScreen(viewModel = viewModel)
                PortfolioTab.AI_CHAT -> AiAssistantScreen(viewModel = viewModel)
                PortfolioTab.NEWS -> NewsScreen(viewModel = viewModel)
                PortfolioTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
