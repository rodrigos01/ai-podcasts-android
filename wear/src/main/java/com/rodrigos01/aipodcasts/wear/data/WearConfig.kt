package com.rodrigos01.aipodcasts.wear.data

/** Mirrors `ApiClient.DEFAULT_BASE_URL` in :app (the watch has no backend-URL override). */
object WearConfig {
    const val BASE_URL = "https://ai-podcast-api-883622140264.us-central1.run.app/"

    /** Name of the Firestore database the podcasts backend writes to. */
    const val FIRESTORE_DATABASE = "podcasts"

    /**
     * Builds the direct streaming URL handed to ExoPlayer (auth is injected per request by
     * [com.rodrigos01.aipodcasts.wear.player.AudioStreamTokenResolver]). [timeSeconds] makes the
     * backend start the stream at that offset.
     */
    fun buildAudioStreamUrl(podcastId: String, episodeId: String, timeSeconds: Double? = null): String {
        var url = "${BASE_URL.trimEnd('/')}/podcasts/$podcastId/episodes/$episodeId/audio/stream"
        if (timeSeconds != null && timeSeconds > 0) {
            url += "?t=%.1f".format(java.util.Locale.US, timeSeconds)
        }
        return url
    }
}
