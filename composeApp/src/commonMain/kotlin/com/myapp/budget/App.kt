package com.myapp.budget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.ui.addedit.AddEditScreen
import com.myapp.budget.ui.asset.AssetScreen
import com.myapp.budget.ui.category.CategoryManagementScreen
import com.myapp.budget.ui.components.EmojiText
import com.myapp.budget.ui.fixedexpense.FixedExpenseScreen
import com.myapp.budget.ui.home.HomeScreen
import com.myapp.budget.ui.search.SearchScreen
import com.myapp.budget.ui.statistics.StatisticsScreen
import com.myapp.budget.ui.theme.BudgetTheme
import com.myapp.budget.ui.theme.PotatoBrown
import com.myapp.budget.ui.theme.PotatoCream
import com.myapp.budget.ui.theme.PotatoDark
import com.myapp.budget.ui.theme.PotatoDeep
import com.myapp.budget.ui.theme.PotatoLight
import com.myapp.budget.ui.transactions.TransactionListScreen

@Stable
sealed class Screen {
    data object Home : Screen()
    data object Transactions : Screen()
    data object Statistics : Screen()
    data object Assets : Screen()
    data object CategoryManagement : Screen()
    data object FixedExpenses : Screen()
    data object Search : Screen()
    data class AddEdit(val transactionId: Long? = null) : Screen()
}

@Composable
fun App() {
    BudgetTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var drawerOpen by remember { mutableStateOf(false) }

        // Stable lambda references — prevent screens from recomposing when drawerOpen changes
        val onNavigate: (Screen) -> Unit = remember { { screen -> currentScreen = screen } }
        val onMenuClick: () -> Unit = remember { { drawerOpen = true } }

        Box(modifier = Modifier.fillMaxSize()) {
            AppContent(
                currentScreen = currentScreen,
                onNavigate = onNavigate,
                onMenuClick = onMenuClick
            )

            // 스크림
            AnimatedVisibility(visible = drawerOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { drawerOpen = false }
                )
            }

            // 우측 사이드바
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                AppDrawer(
                    onCategoryManagementClick = {
                        drawerOpen = false
                        currentScreen = Screen.CategoryManagement
                    },
                    onFixedExpensesClick = {
                        drawerOpen = false
                        currentScreen = Screen.FixedExpenses
                    }
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onMenuClick: () -> Unit
) {
    val showBottomBar = currentScreen !is Screen.AddEdit
            && currentScreen !is Screen.CategoryManagement
            && currentScreen !is Screen.FixedExpenses
            && currentScreen !is Screen.Search

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text("홈", style = MaterialTheme.typography.labelSmall) },
                        selected = currentScreen is Screen.Home,
                        onClick = { onNavigate(Screen.Home) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PotatoBrown,
                            selectedTextColor = PotatoBrown,
                            indicatorColor = PotatoLight.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text("내역", style = MaterialTheme.typography.labelSmall) },
                        selected = currentScreen is Screen.Transactions,
                        onClick = { onNavigate(Screen.Transactions) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PotatoBrown,
                            selectedTextColor = PotatoBrown,
                            indicatorColor = PotatoLight.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text("통계", style = MaterialTheme.typography.labelSmall) },
                        selected = currentScreen is Screen.Statistics,
                        onClick = { onNavigate(Screen.Statistics) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PotatoBrown,
                            selectedTextColor = PotatoBrown,
                            indicatorColor = PotatoLight.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text("자산", style = MaterialTheme.typography.labelSmall) },
                        selected = currentScreen is Screen.Assets,
                        onClick = { onNavigate(Screen.Assets) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PotatoBrown,
                            selectedTextColor = PotatoBrown,
                            indicatorColor = PotatoLight.copy(alpha = 0.6f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState is Screen.AddEdit || targetState is Screen.CategoryManagement || targetState is Screen.FixedExpenses || targetState is Screen.Search) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else if (initialState is Screen.AddEdit || initialState is Screen.CategoryManagement || initialState is Screen.FixedExpenses || initialState is Screen.Search) {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            modifier = Modifier.padding(innerPadding),
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is Screen.Home -> HomeScreen(
                    onAddClick = { onNavigate(Screen.AddEdit()) },
                    onTransactionClick = { id -> onNavigate(Screen.AddEdit(id)) },
                    onMenuClick = onMenuClick
                )
                is Screen.Transactions -> TransactionListScreen(
                    onAddClick = { onNavigate(Screen.AddEdit()) },
                    onTransactionClick = { id -> onNavigate(Screen.AddEdit(id)) },
                    onSearchClick = { onNavigate(Screen.Search) },
                    onMenuClick = onMenuClick
                )
                is Screen.Statistics -> StatisticsScreen(
                    onMenuClick = onMenuClick
                )
                is Screen.Assets -> AssetScreen(
                    onMenuClick = onMenuClick
                )
                is Screen.CategoryManagement -> CategoryManagementScreen(
                    onBack = { onNavigate(Screen.Home) }
                )
                is Screen.FixedExpenses -> FixedExpenseScreen(
                    onBack = { onNavigate(Screen.Home) }
                )
                is Screen.Search -> SearchScreen(
                    onBack = { onNavigate(Screen.Transactions) },
                    onTransactionClick = { id -> onNavigate(Screen.AddEdit(id)) }
                )
                is Screen.AddEdit -> AddEditScreen(
                    transactionId = screen.transactionId,
                    onBack = { onNavigate(Screen.Home) }
                )
            }
        }
    }
}

@Composable
private fun AppDrawer(
    onCategoryManagementClick: () -> Unit,
    onFixedExpensesClick: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(PotatoBrown, Color(0xFFFFBD5E), PotatoDark)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.75f)
            .background(PotatoCream)
    ) {
        // 그라데이션 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                EmojiText("🥔", fontSize = 40.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "감자 가계부",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "내 돈을 알뜰하게 관리해요",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        DrawerMenuItem(
            icon = Icons.Default.Category,
            label = "카테고리 관리",
            onClick = onCategoryManagementClick
        )
        DrawerMenuItem(
            icon = Icons.Default.Repeat,
            label = "고정 지출 관리",
            onClick = onFixedExpensesClick
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DrawerMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PotatoBrown.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = PotatoBrown
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = PotatoDeep,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
