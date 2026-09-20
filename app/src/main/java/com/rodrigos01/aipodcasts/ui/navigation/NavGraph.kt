package com.rodrigos01.aipodcasts.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.ui.components.MiniPlayerBar
import com.rodrigos01.aipodcasts.ui.screens.auth.AuthScreen
import com.rodrigos01.aipodcasts.ui.screens.detail.PodcastDetailScreen
import com.rodrigos01.aipodcasts.ui.screens.episode.EpisodeDetailScreen
import com.rodrigos01.aipodcasts.ui.screens.episode.EpisodeWizardScreen
import com.rodrigos01.aipodcasts.ui.screens.home.HomeScreen
import com.rodrigos01.aipodcasts.ui.screens.player.PlayerSheet
import com.rodrigos01.aipodcasts.ui.screens.settings.SettingsScreen
import com.rodrigos01.aipodcasts.ui.screens.wizard.PodcastWizardScreen

@UnstableApi
@Composable
fun PodcastNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    val audioController = AIPodcastsApplication.instance.audioController
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showPlayerSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Auth.route) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    MiniPlayerBar(
                        audioController = audioController,
                        onOpenFullPlayer = { showPlayerSheet = true }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(
                    onAuthenticated = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToWizard = {
                        navController.navigate(Screen.PodcastWizard.route)
                    },
                    onNavigateToDetail = { podcastId ->
                        navController.navigate(Screen.PodcastDetail.createRoute(podcastId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.PodcastWizard.route) {
                PodcastWizardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPodcastCreated = { podcastId ->
                        navController.navigate(Screen.PodcastDetail.createRoute(podcastId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.PodcastDetail.route,
                arguments = listOf(navArgument("podcastId") { type = NavType.StringType })
            ) { backStackEntry ->
                val podcastId = backStackEntry.arguments?.getString("podcastId") ?: ""
                PodcastDetailScreen(
                    podcastId = podcastId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEpisodeWizard = { id ->
                        navController.navigate(Screen.EpisodeWizard.createRoute(id))
                    },
                    onNavigateToEpisodeDetail = { pId, eId ->
                        navController.navigate(Screen.EpisodeDetail.createRoute(pId, eId))
                    }
                )
            }

            composable(
                route = Screen.EpisodeWizard.route,
                arguments = listOf(navArgument("podcastId") { type = NavType.StringType })
            ) { backStackEntry ->
                val podcastId = backStackEntry.arguments?.getString("podcastId") ?: ""
                EpisodeWizardScreen(
                    podcastId = podcastId,
                    onNavigateBack = { navController.popBackStack() },
                    onEpisodeConfirmed = { pId, eId ->
                        navController.navigate(Screen.EpisodeDetail.createRoute(pId, eId)) {
                            popUpTo(Screen.PodcastDetail.createRoute(pId))
                        }
                    }
                )
            }

            composable(
                route = Screen.EpisodeDetail.route,
                arguments = listOf(
                    navArgument("podcastId") { type = NavType.StringType },
                    navArgument("episodeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val podcastId = backStackEntry.arguments?.getString("podcastId") ?: ""
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
                EpisodeDetailScreen(
                    podcastId = podcastId,
                    episodeId = episodeId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSignedOut = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        if (showPlayerSheet) {
            PlayerSheet(
                audioController = audioController,
                onDismiss = { showPlayerSheet = false }
            )
        }
    }
}
