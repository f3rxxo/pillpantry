package com.yourname.pillpantry

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourname.pillpantry.data.FirebaseRepository
import com.yourname.pillpantry.data.OpenFoodFactsRepository
import com.yourname.pillpantry.notifications.NotificationHelper
import com.yourname.pillpantry.ui.pantry.PantryScreen
import com.yourname.pillpantry.ui.scanner.ScannerScreen
import com.yourname.pillpantry.ui.shoppinglist.ShoppingListScreen
import com.yourname.pillpantry.ui.theme.PillPantryTheme
import kotlinx.coroutines.launch

private sealed class Tab(val route: String, val label: String) {
    data object Scanner : Tab("scanner", "Scanner")
    data object Pantry : Tab("pantry", "Pantry")
    data object ShoppingList : Tab("shopping_list", "Shopping List")
}

class MainActivity : ComponentActivity() {

    private val repository = FirebaseRepository()
    private val offRepository = OpenFoodFactsRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        com.yourname.pillpantry.work.PortionDecayWorker.schedule(this)

        setContent {
            PillPantryTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* no-op: refill alerts simply won't show if denied */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var userId by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    scope.launch {
                        val uid = repository.ensureSignedIn()
                        // Catch up any missed daily portion decrements (see
                        // FirebaseRepository.applyMissedPortionDecrements for
                        // why this runs here instead of relying solely on a
                        // background job to fire at exactly 7am).
                        repository.applyMissedPortionDecrements(uid)
                        userId = uid
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val currentUserId = userId
                    if (currentUserId == null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        PillPantryApp(currentUserId, repository, offRepository)
                    }
                }
            }
        }
    }
}

@Composable
private fun PillPantryApp(
    userId: String,
    repository: FirebaseRepository,
    offRepository: OpenFoodFactsRepository
) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Scanner, Tab.Pantry, Tab.ShoppingList)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (tab) {
                                Tab.Scanner -> Icons.Default.CameraAlt
                                Tab.Pantry -> Icons.Default.Kitchen
                                Tab.ShoppingList -> Icons.Default.ShoppingCart
                            }
                            Icon(icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Scanner.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(Tab.Scanner.route) {
                ScannerScreen(userId = userId, repository = repository, offRepository = offRepository)
            }
            composable(Tab.Pantry.route) {
                PantryScreen(userId = userId, repository = repository)
            }
            composable(Tab.ShoppingList.route) {
                ShoppingListScreen(userId = userId, repository = repository)
            }
        }
    }
}
