package com.rodrigos01.aipodcasts.wear.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Read-only Firestore access to the user's podcasts and episodes (all writes stay on the phone). */
class WearPodcastRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    fun podcastsFlow(): Flow<List<WearPodcast>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("podcasts")
            .whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.map { it.toWearPodcast() }.sortedBy { it.title.lowercase() })
                }
            }
        awaitClose { registration.remove() }
    }

    fun episodesFlow(podcastId: String): Flow<List<WearEpisode>> = callbackFlow {
        val registration = firestore.collection("podcasts").document(podcastId)
            .collection("episodes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.documents
                            .map { it.toWearEpisode(podcastId) }
                            .sortedWith(compareByDescending<WearEpisode> { it.createdAtMillis }.thenByDescending { it.id })
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    fun episodeFlow(podcastId: String, episodeId: String): Flow<WearEpisode?> = callbackFlow {
        val registration = firestore.collection("podcasts").document(podcastId)
            .collection("episodes").document(episodeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(if (snapshot.exists()) snapshot.toWearEpisode(podcastId) else null)
                }
            }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toWearPodcast(): WearPodcast =
    WearPodcast(id = id, title = getString("title").orEmpty())

private fun DocumentSnapshot.toWearEpisode(defaultPodcastId: String): WearEpisode =
    WearEpisode(
        id = id,
        podcastId = getString("podcastId")?.takeIf { it.isNotBlank() } ?: defaultPodcastId,
        title = getString("title").orEmpty(),
        description = getString("topics").orEmpty(),
        status = getString("status") ?: "generating",
        generatedAudioSeconds = getDouble("generatedAudioSeconds"),
        audioComplete = getBoolean("audioComplete") ?: false,
        audioDurationSeconds = getDouble("audioDurationSeconds"),
        createdAtMillis = parseCreatedAtMillis(get("createdAt")),
    )

/** Same tolerance as the phone's `FirestoreMappers.parseCreatedAtMillis`. */
internal fun parseCreatedAtMillis(createdAt: Any?): Long = when (createdAt) {
    is Timestamp -> createdAt.toDate().time
    is java.util.Date -> createdAt.time
    is Number -> createdAt.toLong()
    is String -> runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }
        .getOrElse { createdAt.toLongOrNull() ?: 0L }
    else -> 0L
}
