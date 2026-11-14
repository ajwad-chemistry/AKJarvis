package com.akjarvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.akjarvis.app.data.prefs.AppSettings
import com.akjarvis.app.di.ServiceLocator
import com.akjarvis.app.ui.chat.ChatScreen
import com.akjarvis.app.ui.chat.ChatViewModel
import com.akjarvis.app.ui.permissions.PermissionsScreen
import com.akjarvis.app.ui.settings.SettingsScreen
import com.akjarvis.app.ui.settings.SettingsViewModel
import com.akjarvis.app.ui.tasks.TasksScreen
import com.akjarvis.app.ui.tasks.TasksViewModel
import com.akjarvis.app.ui.theme.AKJarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by ServiceLocator.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            AKJarvisTheme(themeMode = settings.theme) {
                AppRoot()
            }
        }
    }
}

private data class BottomDestination(val route: String, val label: String)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val destinations = listOf(
        BottomDestination("chat", "Chat"),
        BottomDestination("tasks", "Tasks"),
        BottomDestination("settings", "Settings"),
    )
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val showBottomBar = currentRoute in destinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    when (destination.route) {
                                        "chat" -> Icons.Filled.Chat
                                        "tasks" -> Icons.Filled.Checklist
                                        else -> Icons.Filled.Settings
                                    },
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("chat") {
                val vm: ChatViewModel = viewModel()
                ChatScreen(viewModel = vm, onOpenSettings = { navController.navigate("settings") })
            }
            composable("tasks") {
                val vm: TasksViewModel = viewModel()
                TasksScreen(viewModel = vm)
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate("permissions") },
                )
            }
            composable("permissions") {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
