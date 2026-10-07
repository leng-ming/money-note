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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.moneynote.ui.screens.AccountDetailScreen
import com.local.moneynote.ui.screens.AccountEditorDialog
import com.local.moneynote.ui.screens.AddTransactionScreen
import com.local.moneynote.ui.screens.BudgetScreen
import com.local.moneynote.ui.screens.RecurringScreen
import com.local.moneynote.ui.screens.SearchScreen
import com.local.moneynote.ui.screens.SettingsScreen
import com.local.moneynote.ui.screens.StatsScreen
import com.local.moneynote.ui.screens.TransactionListScreen
import com.local.moneynote.ui.theme.MoneyNoteTheme
import kotlinx.coroutines.launch

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
                onOpenRecurring = { nav.navigate("recurring") },
                onOpenAccount = { id -> nav.navigate("account/$id") }
            )
        }

        composable(
            route = "account/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { entry ->
            AccountDetailScreen(
                vm = vm,
                accountId = entry.arguments?.getLong("accountId") ?: -1L,
                onBack = { nav.popBackStack() }
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
    onOpenRecurring: () -> Unit,
    onOpenAccount: (Long) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    // 明细页滑到「总资产」那张卡时，右下角 FAB 要变成「添加账户」
    var assetsPageActive by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                if (assetsPageActive) {
                    // 站在资产卡这一页，右下角就该是「加账户」，而不是「再记一笔」
                    ExtendedFloatingActionButton(
                        onClick = { showAddAccount = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        text = { Text("添加账户") }
                    )
                } else {
                    FloatingActionButton(
                        onClick = onAdd,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "记一笔")
                    }
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
                0 -> TransactionListScreen(
                    vm = vm,
                    onEdit = onEdit,
                    onSearch = onSearch,
                    onOpenAccount = onOpenAccount,
                    onAssetsPageChanged = { assetsPageActive = it }
                )
                1 -> StatsScreen(vm = vm)
                2 -> BudgetScreen(vm = vm, onOpenRecurring = onOpenRecurring)
                else -> SettingsScreen(vm = vm, onOpenRecurring = onOpenRecurring)
            }
        }
    }

    // 资产页右下角「添加账户」
    if (showAddAccount) {
        AccountEditorDialog(
            initial = null,
            onDismiss = { showAddAccount = false },
            onSave = { name, type, initialCents ->
                scope.launch { vm.repo.addAccount(name, type, initialCents) }
                showAddAccount = false
            },
            onDelete = { showAddAccount = false }
        )
    }
}
