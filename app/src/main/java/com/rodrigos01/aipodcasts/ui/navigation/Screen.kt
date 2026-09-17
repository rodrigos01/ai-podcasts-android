package com.rodrigos01.aipodcasts.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object PodcastWizard : Screen("podcast_wizard")
    object PodcastDetail : Screen("podcast_detail/{podcastId}") {
        fun createRoute(podcastId: String) = "podcast_detail/$podcastId"
    }
    object Sources : Screen("sources/{podcastId}") {
        fun createRoute(podcastId: String) = "sources/$podcastId"
    }
    object EpisodeWizard : Screen("episode_wizard/{podcastId}") {
        fun createRoute(podcastId: String) = "episode_wizard/$podcastId"
    }
    object EpisodeDetail : Screen("episode_detail/{podcastId}/{episodeId}") {
        fun createRoute(podcastId: String, episodeId: String) = "episode_detail/$podcastId/$episodeId"
    }
    object Settings : Screen("settings")
}
