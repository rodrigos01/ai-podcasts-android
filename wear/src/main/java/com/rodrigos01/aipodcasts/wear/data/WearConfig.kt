package com.rodrigos01.aipodcasts.wear.data

/** Mirrors `ApiClient` in :app (the watch has no backend-URL override). */
object WearConfig {
    const val BASE_URL = "https://ai-podcast-api-883622140264.us-central1.run.app/"

    /** Name of the Firestore database the podcasts backend writes to. */
    const val FIRESTORE_DATABASE = "podcasts"

    /** Mime type of the backend's audio, so ExoPlayer doesn't have to sniff an extension-less URL. */
    const val AUDIO_MIME_TYPE = "audio/aac"

    /** `GET` here (authenticated) returns `{ "url": ... }`: the episode's public, credential-free audio URL. */
    fun audioUrlEndpoint(podcastId: String, episodeId: String): String =
        "${BASE_URL.trimEnd('/')}/podcasts/$podcastId/episodes/$episodeId/audio/url"

    /**
     * Sets (or, for null/non-positive, removes) the `t=<seconds>` start offset on [url], keeping
     * any other query parameters and the fragment. Mirrors `ApiClient.withStartTime` in :app.
     */
    fun withStartTime(url: String, timeSeconds: Double?): String {
        val fragmentAt = url.indexOf('#').let { if (it < 0) url.length else it }
        val fragment = url.substring(fragmentAt)
        val beforeFragment = url.substring(0, fragmentAt)
        val path = beforeFragment.substringBefore('?')
        val params = beforeFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() && it != "t" && !it.startsWith("t=") }
            .toMutableList()
        if (timeSeconds != null && timeSeconds > 0) {
            params.add("t=%.1f".format(java.util.Locale.US, timeSeconds))
        }
        return path + (if (params.isEmpty()) "" else "?" + params.joinToString("&")) + fragment
    }
}
