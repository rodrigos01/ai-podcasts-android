package com.rodrigos01.aipodcasts.wear.ui

import android.net.Uri

/**
 * String-route destinations (the watch's SwipeDismissableNavHost uses classic string routes
 * rather than the phone's type-safe ones). Titles travel as encoded path segments.
 */
object WearDestinations {
    const val ARG_PODCAST_ID = "podcastId"
    const val ARG_PODCAST_TITLE = "podcastTitle"
    const val ARG_EPISODE_ID = "episodeId"

    const val AUTH_GATE = "auth_gate"
    const val PODCASTS = "podcasts"
    const val EPISODES_TEMPLATE = "episodes/{$ARG_PODCAST_ID}/{$ARG_PODCAST_TITLE}"
    const val EPISODE_TEMPLATE = "episode/{$ARG_PODCAST_ID}/{$ARG_PODCAST_TITLE}/{$ARG_EPISODE_ID}"
    const val PLAYER = "player"

    fun episodes(podcastId: String, podcastTitle: String) = "episodes/$podcastId/${Uri.encode(podcastTitle)}"
    fun episode(podcastId: String, podcastTitle: String, episodeId: String) =
        "episode/$podcastId/${Uri.encode(podcastTitle)}/$episodeId"
}
