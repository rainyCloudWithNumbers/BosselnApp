package com.example.bosseln.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bosseln.ui.history.HistoryScreen
import com.example.bosseln.ui.map.MapScreen
import com.example.bosseln.ui.play.PlayScreen
import com.example.bosseln.ui.settings.SettingsScreen
import com.example.bosseln.ui.setup.MatchSetupScreen
import com.example.bosseln.ui.team.TeamManagerScreen
import com.example.bosseln.vm.GameViewModel
import kotlinx.coroutines.launch

object Routes {
    const val TeamManager = "teams"
    const val Setup = "setup"
    const val Play = "play"
    const val Map = "map"
    const val Settings = "settings"
    const val History = "history"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(vm: GameViewModel) {
    val nav = rememberNavController()
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val route = navBackStackEntry?.destination?.route

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ui by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bosseln") },
                actions = {
                    // Teams bearbeiten – immer erreichbar
                    IconButton(onClick = { nav.navigate(Routes.TeamManager) }) {
                        Icon(Icons.Filled.Group, contentDescription = "Teams")
                    }
                    // Match starten – nur wenn kein Match läuft
                    IconButton(onClick = {
                        if (ui.currentMatch == null) {
                            nav.navigate(Routes.Setup)
                        } else {
                            scope.launch {
                                snackbar.showSnackbar(
                                    message = "Es läuft bereits ein Match. Bitte zuerst beenden.",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Flag, contentDescription = "Match starten")
                    }
                    // Restliche Shortcuts
                    IconButton(onClick = { nav.navigate(Routes.Play) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                    IconButton(onClick = { nav.navigate(Routes.Map) }) {
                        Icon(Icons.Filled.Map, contentDescription = "Map")
                    }
                    IconButton(onClick = { nav.navigate(Routes.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { nav.navigate(Routes.History) }) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.TeamManager,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.TeamManager) { TeamManagerScreen(vm) { nav.navigate(Routes.Setup) } }
            composable(Routes.Setup) { MatchSetupScreen(vm) { nav.navigate(Routes.Play) } }
            composable(Routes.Play) { PlayScreen(vm) }
            composable(Routes.Map) { MapScreen(vm) }
            composable(Routes.Settings) { SettingsScreen(vm) }
            composable(Routes.History) { HistoryScreen(vm) }
        }
    }
}
