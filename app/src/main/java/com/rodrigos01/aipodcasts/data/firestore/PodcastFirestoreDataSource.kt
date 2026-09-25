package com.rodrigos01.aipodcasts.data.firestore
 
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PodcastFirestoreDataSource(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth? = null
) {

    private val currentUserId: String?
        get() = (auth ?: runCatching { FirebaseAuth.getInstance() }.getOrNull())?.currentUser?.uid

    fun getPodcastsFlow(): Flow<List<Podcast>> = callbackFlow {
        val authInstance = auth ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
        var snapshotRegistration: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
            val uid = fbAuth.currentUser?.takeIf { !it.isAnonymous }?.uid
            snapshotRegistration?.remove()
            if (uid == null) {
                trySend(emptyList())
            } else {
                snapshotRegistration = firestore.collection("podcasts")
                    .whereEqualTo("ownerId", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.documents.map { it.toPodcast() })
                        }
                    }
            }
        }

        if (authInstance != null) {
            authInstance.addAuthStateListener(authListener)
        } else {
            val uid = currentUserId
            if (uid != null) {
                snapshotRegistration = firestore.collection("podcasts")
                    .whereEqualTo("ownerId", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.documents.map { it.toPodcast() })
                        }
                    }
            } else {
                trySend(emptyList())
            }
        }

        awaitClose {
            snapshotRegistration?.remove()
            authInstance?.removeAuthStateListener(authListener)
        }
    }

    fun getPodcastFlow(podcastId: String): Flow<Podcast?> = callbackFlow {
        val registration = firestore.collection("podcasts")
            .document(podcastId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toPodcast())
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    fun getEpisodesFlow(podcastId: String): Flow<List<Episode>> = callbackFlow {
        val registration = firestore.collection("podcasts")
            .document(podcastId)
            .collection("episodes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val episodes = snapshot.documents
                        .map { it.toEpisode(podcastId) }
                        .sortedWith(
                            compareByDescending<Episode> { FirestoreMappers.parseCreatedAtMillis(it.createdAt) }
                                .thenByDescending { it.id }
                        )
                    trySend(episodes)
                }
            }
        awaitClose { registration.remove() }
    }

    fun getEpisodeFlow(podcastId: String, episodeId: String): Flow<Episode?> = callbackFlow {
        val registration = firestore.collection("podcasts")
            .document(podcastId)
            .collection("episodes")
            .document(episodeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toEpisode(podcastId))
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    fun getSourcesFlow(podcastId: String): Flow<List<Source>> = callbackFlow {
        val registration = firestore.collection("podcasts")
            .document(podcastId)
            .collection("sources")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.map { it.toSource(podcastId) })
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun getPodcasts(): List<Podcast> {
        val uid = currentUserId ?: return emptyList()
        val snapshot = firestore.collection("podcasts")
            .whereEqualTo("ownerId", uid)
            .get()
            .await()
        return snapshot.documents.map { it.toPodcast() }
    }

    suspend fun getPodcast(podcastId: String): Podcast {
        val doc = firestore.collection("podcasts").document(podcastId).get().await()
        if (!doc.exists()) {
            throw NoSuchElementException("Podcast with id $podcastId not found")
        }
        return doc.toPodcast()
    }

    suspend fun getSources(podcastId: String): List<Source> {
        val snapshot = firestore.collection("podcasts")
            .document(podcastId)
            .collection("sources")
            .get()
            .await()
        return snapshot.documents.map { it.toSource(podcastId) }
    }

    suspend fun getSource(podcastId: String, sourceId: String): Source {
        val doc = firestore.collection("podcasts")
            .document(podcastId)
            .collection("sources")
            .document(sourceId)
            .get()
            .await()
        if (!doc.exists()) {
            throw NoSuchElementException("Source with id $sourceId not found")
        }
        return doc.toSource(podcastId)
    }

    suspend fun getEpisodes(podcastId: String): List<Episode> {
        val snapshot = firestore.collection("podcasts")
            .document(podcastId)
            .collection("episodes")
            .get()
            .await()
        return snapshot.documents
            .map { it.toEpisode(podcastId) }
            .sortedWith(
                compareByDescending<Episode> { FirestoreMappers.parseCreatedAtMillis(it.createdAt) }
                    .thenByDescending { it.id }
            )
    }

    suspend fun getEpisode(podcastId: String, episodeId: String): Episode {
        val doc = firestore.collection("podcasts")
            .document(podcastId)
            .collection("episodes")
            .document(episodeId)
            .get()
            .await()
        if (!doc.exists()) {
            throw NoSuchElementException("Episode with id $episodeId not found")
        }
        return doc.toEpisode(podcastId)
    }

    suspend fun getEpisodeStatus(podcastId: String, episodeId: String): EpisodeStatusResponse {
        val doc = firestore.collection("podcasts")
            .document(podcastId)
            .collection("episodes")
            .document(episodeId)
            .get()
            .await()
        if (!doc.exists()) {
            throw NoSuchElementException("Episode with id $episodeId not found")
        }
        return doc.toEpisodeStatusResponse()
    }
}
