package com.local.moneynote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.moneynote.ui.screens.AddTransactionScreen
import com.local.moneynote.ui.screens.BudgetScreen
import com.local.moneynote.ui.screens.RecurringScreen
import com.local.moneynote.ui.screens.SearchScreen
import com.local.moneynote.ui.screens.SettingsScreen
import com.local.moneynote.ui.screens.StatsScreen
import com.local.moneynote.ui.screens.TransactionListScreen
import com.local.moneynote.ui.theme.MoneyNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoneyNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "main") {

        composable("main") {
            MainScaffold(
                vm = vm,
                onAdd = { nav.navigate("edit") },
                onEdit = { id -> nav.navigate("edit?txId=$id") },
                onSearch = { nav.navigate("search") },
                onOpenRecurring = { nav.navigate("recurring") }
            )
        }

        composable(
            route = "edit?txId={txId}",
            arguments = listOf(
                navArgument("txId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { entry ->
            AddTransactionScreen(
                vm = vm,
                txId = entry.arguments?.getLong("txId") ?: -1L,
                onDone = { nav.popBackStack() }
            )
        }

        composable("search") {
            SearchScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate("edit?txId=$id") }
            )
        }

        composable("recurring") {
            RecurringScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenRecurring: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    label = { Text("明细") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.PieChart, contentDescription = null) },
                    label = { Text("图表") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Savings, contentDescription = null) },
                    label = { Text("预算") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("我的") }
                )
            }
        },
        floatingActionButton = {
            // FAB 是悬浮的，会永久盖住它下方的内容（内容不足一屏时无法靠滚动避让），
            // 所以只在「明细」页出现；图表/预算/我的页没有随手记一笔的即时需求。
            if (tab == 0) {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                0 -> TransactionListScreen(vm = vm, onEdit = onEdit, onSearch = onSearch)
                1 -> StatsScreen(vm = vm)
                2 -> BudgetScreen(vm = vm, onOpenRecurring = onOpenRecurring)
                else -> SettingsScreen(vm = vm, onOpenRecurring = onOpenRecurring)
            }
        }
    }
}
