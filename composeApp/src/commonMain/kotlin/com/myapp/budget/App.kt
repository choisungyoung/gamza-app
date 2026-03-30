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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.budget.platform.BackPressExitHandler
import com.myapp.budget.platform.OnBackPressed
import com.myapp.budget.ui.addedit.AddEditScreen
import com.myapp.budget.ui.asset.AssetScreen
import com.myapp.budget.ui.category.CategoryManagementScreen
import com.myapp.budget.ui.components.PotatoCharacter
import com.myapp.budget.ui.datamanagement.DataManagementScreen
import com.myapp.budget.ui.dbviewer.DbViewerScreen
import com.myapp.budget.ui.fixedexpense.FixedExpenseScreen
import com.myapp.budget.ui.splash.SplashScreen
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
    data class CategoryManagement(val previousScreen: Screen = Home) : Screen()
    data class FixedExpenses(val previousScreen: Screen = Home) : Screen()
    data object Search : Screen()
    data class DataManagement(val previousScreen: Screen = Home) : Screen()
    data class DbViewer(val previousScreen: Screen = Home) : Screen()
    data class AddEdit(val transactionId: Long? = null, val previousScreen: Screen = Home) : Screen()
}

@Composable
fun App() {
    BudgetTheme {
        var showSplash by remember { mutableStateOf(true) }

        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
            return@BudgetTheme
        }

        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var drawerOpen by remember { mutableStateOf(false) }
        var isAdminMode by remember { mutableStateOf(false) }
        var potatoClickCount by remember { mutableIntStateOf(0) }
        var toastMessage by remember { mutableStateOf("") }

        // Stable lambda references — prevent screens from recomposing when drawerOpen changes
        val onNavigate: (Screen) -> Unit = remember { { screen -> currentScreen = screen } }
        val onMenuClick: () -> Unit = remember { { drawerOpen = true } }

        val isMainTab = currentScreen is Screen.Home
                || currentScreen is Screen.Transactions
                || currentScreen is Screen.Statistics
                || currentScreen is Screen.Assets
        BackPressExitHandler(enabled = isMainTab && !drawerOpen)
        OnBackPressed(enabled = drawerOpen) { drawerOpen = false }

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
                    isAdminMode = isAdminMode,
                    onPotatoClick = {
                        val newCount = potatoClickCount + 1
                        potatoClickCount = newCount
                        if (!isAdminMode) {
                            when (newCount) {
                                7 -> toastMessage = "관리자 모드까지 3회 남았습니다"
                                8 -> toastMessage = "관리자 모드까지 2회 남았습니다"
                                9 -> toastMessage = "관리자 모드까지 1회 남았습니다"
                            }
                            if (newCount >= 10) {
                                isAdminMode = true
                                toastMessage = "관리자 모드가 활성화되었습니다"
                            }
                        }
                    },
                    onCategoryManagementClick = {
                        drawerOpen = false
                        currentScreen = Screen.CategoryManagement(currentScreen)
                    },
                    onDataManagementClick = {
                        drawerOpen = false
                        currentScreen = Screen.DataManagement(currentScreen)
                    },
                    onDbViewerClick = {
                        drawerOpen = false
                        currentScreen = Screen.DbViewer(currentScreen)
                    }
                )
            }

            // 토스트 메시지
            if (toastMessage.isNotEmpty()) {
                LaunchedEffect(toastMessage) {
                    delay(2000)
                    toastMessage = ""
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = toastMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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
            && currentScreen !is Screen.DataManagement
            && currentScreen !is Screen.DbViewer

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
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
                if (targetState is Screen.AddEdit || targetState is Screen.CategoryManagement || targetState is Screen.FixedExpenses || targetState is Screen.Search || targetState is Screen.DataManagement || targetState is Screen.DbViewer) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else if (initialState is Screen.AddEdit || initialState is Screen.CategoryManagement || initialState is Screen.FixedExpenses || initialState is Screen.Search || initialState is Screen.DataManagement || initialState is Screen.DbViewer) {
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
                    onAddClick = { onNavigate(Screen.AddEdit(previousScreen = Screen.Transactions)) },
                    onTransactionClick = { id -> onNavigate(Screen.AddEdit(id, Screen.Transactions)) },
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
                    onBack = { onNavigate(screen.previousScreen) }
                )
                is Screen.FixedExpenses -> FixedExpenseScreen(
                    onBack = { onNavigate(screen.previousScreen) }
                )
                is Screen.Search -> SearchScreen(
                    onBack = { onNavigate(Screen.Transactions) },
                    onTransactionClick = { id -> onNavigate(Screen.AddEdit(id)) }
                )
                is Screen.DataManagement -> DataManagementScreen(
                    onBack = { onNavigate(screen.previousScreen) }
                )
                is Screen.DbViewer -> DbViewerScreen(
                    onBack = { onNavigate(screen.previousScreen) }
                )
                is Screen.AddEdit -> AddEditScreen(
                    transactionId = screen.transactionId,
                    onBack = { onNavigate(screen.previousScreen) }
                )
            }
        }
    }
}

@Composable
private fun AppDrawer(
    isAdminMode: Boolean,
    onPotatoClick: () -> Unit,
    onCategoryManagementClick: () -> Unit,
    onDataManagementClick: () -> Unit,
    onDbViewerClick: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(PotatoBrown, Color(0xFFFFBD5E), PotatoDark)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.75f)
            .background(PotatoCream)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* 내부 빈 공간 클릭 흡수 — 스크림으로 전파 방지 */ }
    ) {
        // 그라데이션 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                // 감자 캐릭터 — 10회 클릭 시 관리자 모드 활성화
                Box {
                    PotatoCharacter(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onPotatoClick
                            )
                    )
                    // 관리자 모드 활성화: 잠금 해제 표시
                    if (isAdminMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFD700)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔓", fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "감자 가계부",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = if (isAdminMode) "🔒 관리자 모드 활성화됨" else "내 돈을 알뜰하게 관리해요",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAdminMode) Color(0xFFFFD700) else Color.White.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        DrawerMenuItem(
            icon = Icons.Default.Category,
            label = "카테고리 관리",
            onClick = onCategoryManagementClick
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )

        DrawerMenuItem(
            icon = Icons.Default.Storage,
            label = "데이터 관리",
            onClick = onDataManagementClick
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )

        // 관리자 전용 메뉴
        if (isAdminMode) {
            DrawerMenuItem(
                icon = Icons.Default.TableChart,
                label = "DB 데이터 조회",
                onClick = onDbViewerClick,
                tint = Color(0xFF7B1FA2)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = PotatoBrown
) {
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
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint
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
