package com.rodrigos01.aipodcasts.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.rodrigos01.aipodcasts.wear.WearApplication
import com.rodrigos01.aipodcasts.wear.auth.WearAuthUiState
import com.rodrigos01.aipodcasts.wear.auth.WearAuthViewModel
import com.rodrigos01.aipodcasts.wear.ui.screens.AuthGateScreen
import com.rodrigos01.aipodcasts.wear.ui.screens.WearEpisodeScreen
import com.rodrigos01.aipodcasts.wear.ui.screens.WearEpisodesScreen
import com.rodrigos01.aipodcasts.wear.ui.screens.WearPlayerScreen
import com.rodrigos01.aipodcasts.wear.ui.screens.WearPodcastsScreen

@UnstableApi
@Composable
fun WearApp() {
    val app = WearApplication.instance
    val context = LocalContext.current.applicationContext
    val authViewModel: WearAuthViewModel = viewModel(factory = WearAuthViewModel.factory(context))
    val navController = rememberSwipeDismissableNavController()
    val authState by authViewModel.uiState.collectAsState()

    // Once pairing succeeds, replace the auth gate with the podcast list.
    LaunchedEffect(authState) {
        if (authState is WearAuthUiState.Authenticated) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current == null || current == WearDestinations.AUTH_GATE) {
                navController.navigate(WearDestinations.PODCASTS) {
                    popUpTo(WearDestinations.AUTH_GATE) { inclusive = true }
                }
            }
        }
    }

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = if (authViewModel.isAuthenticated) WearDestinations.PODCASTS else WearDestinations.AUTH_GATE
        ) {
            composable(WearDestinations.AUTH_GATE) {
                AuthGateScreen(authViewModel = authViewModel)
            }

            composable(WearDestinations.PODCASTS) {
                WearPodcastsScreen(
                    repository = app.podcastRepository,
                    onPodcastClick = { podcast ->
                        navController.navigate(WearDestinations.episodes(podcast.id, podcast.title))
                    }
                )
            }

            composable(
                route = WearDestinations.EPISODES_TEMPLATE,
                arguments = listOf(
                    navArgument(WearDestinations.ARG_PODCAST_ID) {},
                    navArgument(WearDestinations.ARG_PODCAST_TITLE) {}
                )
            ) { entry ->
                val podcastId = entry.arguments?.getString(WearDestinations.ARG_PODCAST_ID).orEmpty()
                val podcastTitle = entry.arguments?.getString(WearDestinations.ARG_PODCAST_TITLE).orEmpty()
                WearEpisodesScreen(
                    podcastId = podcastId,
                    podcastTitle = podcastTitle,
                    repository = app.podcastRepository,
                    onEpisodeClick = { episode ->
                        navController.navigate(WearDestinations.episode(podcastId, podcastTitle, episode.id))
                    }
                )
            }

            composable(
                route = WearDestinations.EPISODE_TEMPLATE,
                arguments = listOf(
                    navArgument(WearDestinations.ARG_PODCAST_ID) {},
                    navArgument(WearDestinations.ARG_PODCAST_TITLE) {},
                    navArgument(WearDestinations.ARG_EPISODE_ID) {}
                )
            ) { entry ->
                val podcastId = entry.arguments?.getString(WearDestinations.ARG_PODCAST_ID).orEmpty()
                val podcastTitle = entry.arguments?.getString(WearDestinations.ARG_PODCAST_TITLE).orEmpty()
                val episodeId = entry.arguments?.getString(WearDestinations.ARG_EPISODE_ID).orEmpty()
                WearEpisodeScreen(
                    podcastId = podcastId,
                    episodeId = episodeId,
                    repository = app.podcastRepository,
                    positions = app.playbackPositions,
                    audioController = app.audioController,
                    podcastTitle = podcastTitle,
                    onStartPlayback = { navController.navigate(WearDestinations.PLAYER) }
                )
            }

            composable(WearDestinations.PLAYER) {
                WearPlayerScreen(audioController = app.audioController)
            }
        }
    }
}
