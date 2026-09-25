package com.rodrigos01.aipodcasts.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeProgress
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.Source

object FirestoreMappers {

    fun mapToPodcast(id: String, data: Map<String, Any?>): Podcast {
        val rawHosts = data["hosts"] as? List<*>
        val hosts = rawHosts?.mapNotNull { item ->
            if (item is Map<*, *>) {
                Host(
                    id = item["id"] as? String ?: "",
                    name = item["name"] as? String ?: "",
                    voice = item["voice"] as? String ?: "",
                    persona = item["persona"] as? String ?: ""
                )
            } else null
        } ?: emptyList()

        return Podcast(
            id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: id,
            title = data["title"] as? String ?: "",
            description = data["description"] as? String ?: "",
            structure = data["structure"] as? String ?: "",
            hosts = hosts,
            createdAt = data["createdAt"]
        )
    }

    fun mapToSource(id: String, data: Map<String, Any?>, defaultPodcastId: String? = null): Source {
        return Source(
            id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: id,
            podcastId = (data["podcastId"] as? String) ?: defaultPodcastId,
            title = data["title"] as? String ?: "",
            contents = data["contents"] as? String ?: "",
            sourceType = data["sourceType"] as? String
        )
    }

    fun mapToEpisode(id: String, data: Map<String, Any?>, defaultPodcastId: String? = null): Episode {
        val rawGuests = data["guests"] as? List<*>
        val guests = rawGuests?.mapNotNull { item ->
            if (item is Map<*, *>) {
                EpisodeGuest(
                    name = item["name"] as? String ?: "",
                    voice = item["voice"] as? String ?: "",
                    persona = item["persona"] as? String ?: ""
                )
            } else null
        } ?: emptyList()

        val sourceIds = (data["sourceIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val participantHostIds = (data["participantHostIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val rawProgress = data["progress"] as? Map<*, *>
        val progress = rawProgress?.let {
            EpisodeProgress(
                stage = it["stage"] as? String,
                wordCount = (it["wordCount"] as? Number)?.toInt(),
                totalWords = (it["totalWords"] as? Number)?.toInt()
            )
        }

        return Episode(
            id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: id,
            podcastId = (data["podcastId"] as? String) ?: defaultPodcastId ?: "",
            title = data["title"] as? String ?: "",
            topics = data["topics"] as? String ?: "",
            length = data["length"] as? String ?: "short",
            sourceIds = sourceIds,
            participantHostIds = participantHostIds,
            guests = guests,
            productionNotes = data["productionNotes"] as? String,
            status = data["status"] as? String ?: "generating",
            progress = progress,
            transcript = data["transcript"],
            ttsPrompt = data["ttsPrompt"] as? String,
            error = data["error"] as? String,
            generatedAudioSeconds = (data["generatedAudioSeconds"] as? Number)?.toDouble(),
            createdAt = data["createdAt"]
        )
    }

    fun parseCreatedAtMillis(createdAt: Any?): Long {
        return when (createdAt) {
            is Timestamp -> createdAt.toDate().time
            is java.util.Date -> createdAt.time
            is Number -> createdAt.toLong()
            is String -> {
                runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }
                    .getOrElse { createdAt.toLongOrNull() ?: 0L }
            }
            else -> 0L
        }
    }

    fun mapToEpisodeStatusResponse(data: Map<String, Any?>): EpisodeStatusResponse {
        val rawProgress = data["progress"] as? Map<*, *>
        val progress = rawProgress?.let {
            EpisodeProgress(
                stage = it["stage"] as? String,
                wordCount = (it["wordCount"] as? Number)?.toInt(),
                totalWords = (it["totalWords"] as? Number)?.toInt()
            )
        }
        val generatedAudioSeconds = (data["generatedAudioSeconds"] as? Number)?.toDouble()

        return EpisodeStatusResponse(
            status = data["status"] as? String ?: "generating",
            progress = progress,
            error = data["error"] as? String,
            generatedAudioSeconds = generatedAudioSeconds
        )
    }
}

fun DocumentSnapshot.toPodcast(): Podcast {
    return FirestoreMappers.mapToPodcast(id, data ?: emptyMap())
}

fun DocumentSnapshot.toSource(defaultPodcastId: String? = null): Source {
    return FirestoreMappers.mapToSource(id, data ?: emptyMap(), defaultPodcastId)
}

fun DocumentSnapshot.toEpisode(defaultPodcastId: String? = null): Episode {
    return FirestoreMappers.mapToEpisode(id, data ?: emptyMap(), defaultPodcastId)
}

fun DocumentSnapshot.toEpisodeStatusResponse(): EpisodeStatusResponse {
    return FirestoreMappers.mapToEpisodeStatusResponse(data ?: emptyMap())
}
