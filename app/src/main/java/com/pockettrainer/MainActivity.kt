package com.pockettrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.pockettrainer.ui.screens.*
import com.pockettrainer.ui.LoraManagerScreen
import com.pockettrainer.ui.theme.PocketTrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PocketTrainerTheme { MainApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomNavItem("home", "首页", Icons.Default.Home),
        BottomNavItem("models", "模型", Icons.Default.SmartToy),
        BottomNavItem("train", "训练", Icons.Default.PlayArrow),
        BottomNavItem("dataset", "数据集", Icons.Default.Storage),
        BottomNavItem("lora", "LoRA", Icons.Default.Memory),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomItems.forEach { item -&gt;
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding -&gt;
        NavHost(navController, "home", Modifier.padding(padding)) {
            composable("home") { HomeScreen(navController) }
            composable("models") { ModelScreen(navController) }
            composable("train") { TrainScreen(navController) }
            composable("dataset") { DatasetScreen(navController) }
            composable("lora") { LoraManagerScreen() }
            composable("settings") { SettingsScreen(navController) }
        }
    }
}

data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
