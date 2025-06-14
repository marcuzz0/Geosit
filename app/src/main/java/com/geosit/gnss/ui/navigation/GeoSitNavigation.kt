package com.geosit.gnss.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.geosit.gnss.ui.screens.*

sealed class GeoSitScreen(
    val route: String,
    val icon: ImageVector
) {
    object Connection : GeoSitScreen("connection", Icons.Default.BluetoothSearching)
    object Recording : GeoSitScreen("recording", Icons.Default.FiberManualRecord)
    object Map : GeoSitScreen("map", Icons.Default.Map)
    object Data : GeoSitScreen("data", Icons.Default.Storage)
    object Settings : GeoSitScreen("settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoSitNavigation() {
    val navController = rememberNavController()
    val screens = listOf(
        GeoSitScreen.Connection,
        GeoSitScreen.Recording,
        GeoSitScreen.Map,
        GeoSitScreen.Data,
        GeoSitScreen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp) // Icone più grandi
                            )
                        },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = GeoSitScreen.Connection.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(GeoSitScreen.Connection.route) { ConnectionScreen() }
            composable(GeoSitScreen.Recording.route) { RecordingScreen() }
            composable(GeoSitScreen.Map.route) { MapScreen() }
            composable(GeoSitScreen.Data.route) { DataScreen() }
            composable(GeoSitScreen.Settings.route) { SettingsScreen() }
        }
    }
}