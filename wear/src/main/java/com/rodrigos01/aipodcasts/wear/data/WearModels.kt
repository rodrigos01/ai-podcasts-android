package com.rodrigos01.aipodcasts.wear.data

/** The slice of a podcast the watch shows. Mirrors the Firestore `podcasts/{id}` document. */
data class WearPodcast(
    val id: String,
    val title: String,
)

/** The slice of an episode the watch shows and plays. Mirrors `podcasts/{id}/episodes/{id}`. */
data class WearEpisode(
    val id: String,
    val podcastId: String,
    val title: String,
    /** The episode's description (called "topics" in the backend). */
    val description: String,
    val status: String,
    val generatedAudioSeconds: Double?,
    val audioComplete: Boolean,
    val audioDurationSeconds: Double?,
    val createdAtMillis: Long,
) {
    /** Audio can be streamed while it is still being generated ("streamable") or once "ready". */
    val canPlay: Boolean
        get() = status.equals(STATUS_STREAMABLE, ignoreCase = true) || status.equals(STATUS_READY, ignoreCase = true)

    companion object {
        const val STATUS_READY = "ready"
        const val STATUS_STREAMABLE = "streamable"
    }
}
