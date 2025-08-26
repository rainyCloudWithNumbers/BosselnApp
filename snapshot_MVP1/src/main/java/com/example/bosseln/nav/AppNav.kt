package com.example.bosseln.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bosseln.ui.play.PlayScreen
import com.example.bosseln.ui.setup.MatchSetupScreen
import com.example.bosseln.ui.team.TeamManagerScreen
import com.example.bosseln.vm.GameViewModel

object Routes { const val TeamManager = "teams"; const val Setup = "setup"; const val Play = "play" }

@Composable
fun AppNav(vm: GameViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TeamManager) {
        composable(Routes.TeamManager) {
            TeamManagerScreen(vm) { nav.navigate(Routes.Setup) }
        }
        composable(Routes.Setup) {
            MatchSetupScreen(vm) { nav.navigate(Routes.Play) }
        }
        composable(Routes.Play) { PlayScreen(vm) }
    }
}
