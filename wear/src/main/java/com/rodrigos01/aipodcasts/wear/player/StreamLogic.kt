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

    /** How long after the last skip tap the seek is actually performed. */
    const val SEEK_DEBOUNCE_MS = 1_000L

    /**
     * The position a skip lands on: taps accumulate on top of [baseMs] (where the first tap was
     * made, or the previous pending target), clamped to what exists, [upperBoundMs] > 0.
     */
    fun skipTarget(baseMs: Long, offsetSeconds: Int, upperBoundMs: Long): Long =
        (baseMs + offsetSeconds * 1000L).coerceIn(0L, upperBoundMs)

    /** How close to the end a stopped stream must be to count as a genuine finish. */
    const val END_TOLERANCE_MS = 3_000L

    /**
     * How to open the audio at [positionMs]. The two kinds of episode are resumed differently:
     *
     * - **Fully generated** (`audioComplete`): a normal static file served with `Range` support,
     *   so open the plain URL and let the player start at the position (it issues the `Range`
     *   request). The public URL is signed, so no query parameter may be appended to it. The
     *   stream then starts at 0, so there is no offset between player and episode time.
     * - **Still generating**: the stream is live/chunked and can't be ranged into, so ask the
     *   backend to start it at `t=<seconds>`; the player's position is then offset by [positionMs].
     */
    data class StreamStart(val timeSeconds: Double?, val playerStartMs: Long, val streamOffsetMs: Long)

    fun streamStart(positionMs: Long, audioComplete: Boolean): StreamStart {
        val start = positionMs.coerceAtLeast(0L)
        return if (audioComplete) {
            StreamStart(timeSeconds = null, playerStartMs = start, streamOffsetMs = 0L)
        } else {
            StreamStart(timeSeconds = if (start > 0) start / 1000.0 else null, playerStartMs = 0L, streamOffsetMs = start)
        }
    }

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
