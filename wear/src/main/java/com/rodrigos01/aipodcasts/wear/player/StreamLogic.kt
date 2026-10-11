package com.rodrigos01.aipodcasts.wear.player

/**
 * Pure stream/position rules, mirroring `PodcastAudioController`'s companion in :app. The audio
 * is streamed from a `?t=<seconds>` offset, so ExoPlayer's own position is relative to where the
 * current stream started and has to be offset to get episode time.
 */
object StreamLogic {
    const val MAX_RESUME_ATTEMPTS = 6
    const val RESUME_DELAY_MS = 2_000L

    /** Playback this far past where an early-end resume began counts as "it worked". */
    const val RESUME_PROGRESS_RESET_MS = 5_000L

    /** How close to the end a stopped stream must be to count as a genuine finish. */
    const val END_TOLERANCE_MS = 3_000L

    /**
     * Whether a stream that just ended means the episode really finished: only when the backend
     * says the audio is complete AND playback reached its end. A response that merely closed
     * (live edge, proxy timeout, dropped connection) is not a finish.
     */
    fun isGenuineEnd(positionMs: Long, audioComplete: Boolean, audioDurationMs: Long): Boolean =
        audioComplete && audioDurationMs > 0 && positionMs >= audioDurationMs - END_TOLERANCE_MS

    /**
     * Whether [targetMs] (episode time) can be reached by seeking the player's current stream: the
     * stream starts at [streamStartOffsetMs] and, if seekable at all, is [playerDurationMs] long.
     * Anything outside that window needs a new `?t=` stream.
     */
    fun canSeekInPlayer(
        targetMs: Long,
        streamStartOffsetMs: Long,
        playerDurationMs: Long,
        isSeekable: Boolean
    ): Boolean {
        if (!isSeekable || playerDurationMs <= 0) return false
        val relativeMs = targetMs - streamStartOffsetMs
        return relativeMs >= 0 && relativeMs <= playerDurationMs - END_TOLERANCE_MS
    }
}
