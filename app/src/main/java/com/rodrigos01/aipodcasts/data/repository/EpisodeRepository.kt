package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeRequest
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeCreateInput
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.EpisodeSuggestion
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardOptionsRequest
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.UpdateEpisodeRequest

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.rodrigos01.aipodcasts.data.firestore.PodcastFirestoreDataSource

import kotlinx.coroutines.flow.Flow

/** Wizard suggestions plus the voice-design session id that goes with them. */
data class EpisodeSuggestionsResult(
    val sessionId: String?,
    val suggestions: List<EpisodeSuggestion>,
    val languageCode: String? = null
)

class EpisodeRepository(
    private val api: PodcastApiService = ApiClient.apiService,
    private val firestoreDataSource: PodcastFirestoreDataSource? = null
) {
    private val querySource: PodcastFirestoreDataSource
        get() = firestoreDataSource ?: PodcastFirestoreDataSource(
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "podcasts")
        )

    constructor(
        api: PodcastApiService = ApiClient.apiService,
        firestore: FirebaseFirestore
    ) : this(api, PodcastFirestoreDataSource(firestore))

    suspend fun generateEpisodeSuggestions(
        podcastId: String,
        sourceIds: List<String>,
        length: String,
        prompt: String? = null
    ): EpisodeSuggestionsResult {
        val request = EpisodeWizardOptionsRequest(sourceIds = sourceIds, prompt = prompt, length = length)
        val response = api.generateEpisodeSuggestions(podcastId, request)
        return EpisodeSuggestionsResult(response.sessionId, response.suggestions, response.languageCode)
    }

    suspend fun reviseEpisodeSuggestions(
        podcastId: String,
        suggestions: List<EpisodeSuggestion>,
        length: String,
        targetSuggestionIndex: Int,
        targetEpisodeIndex: Int? = null,
        instruction: String,
        sessionId: String? = null
    ): EpisodeSuggestionsResult {
        val request = EpisodeWizardReviseRequest(
            suggestions = suggestions,
            length = length,
            sessionId = sessionId,
            targetSuggestionIndex = targetSuggestionIndex,
            targetEpisodeIndex = targetEpisodeIndex,
            instruction = instruction
        )
        val response = api.reviseEpisodeSuggestions(podcastId, request)
        return EpisodeSuggestionsResult(response.sessionId ?: sessionId, response.suggestions, response.languageCode)
    }

    // Confirms a whole suggestion at once: 1 entry for a single episode, or 2
    // for a confirmed split. Each entry must independently satisfy the
    // 2-speaker rule.
    suspend fun createEpisodes(
        podcastId: String,
        episodes: List<EpisodeCreateInput>,
        sessionId: String? = null
    ): List<Episode> {
        episodes.forEach { episode ->
            require(episode.participantHostIds.size + episode.guests.size == 2) {
                "Every episode must have exactly 2 speakers (2 hosts or 1 host + 1 guest)"
            }
        }
        val request = CreateEpisodeRequest(episodes = episodes, sessionId = sessionId)
        return api.createEpisodes(podcastId, request).episodes
    }

    fun getEpisodesFlow(podcastId: String): Flow<List<Episode>> {
        return querySource.getEpisodesFlow(podcastId)
    }

    fun getEpisodeFlow(podcastId: String, episodeId: String): Flow<Episode?> {
        return querySource.getEpisodeFlow(podcastId, episodeId)
    }

    suspend fun getEpisodes(podcastId: String): List<Episode> {
        return querySource.getEpisodes(podcastId)
    }

    suspend fun getEpisode(podcastId: String, episodeId: String): Episode {
        return querySource.getEpisode(podcastId, episodeId)
    }

    suspend fun getEpisodeStatus(podcastId: String, episodeId: String): EpisodeStatusResponse {
        return querySource.getEpisodeStatus(podcastId, episodeId)
    }

    suspend fun updateEpisode(
        podcastId: String,
        episodeId: String,
        title: String? = null,
        topics: String? = null,
        productionNotes: String? = null,
        guests: List<EpisodeGuest>? = null
    ): Episode {
        val request = UpdateEpisodeRequest(
            title = title,
            topics = topics,
            productionNotes = productionNotes,
            guests = guests
        )
        return api.updateEpisode(podcastId, episodeId, request)
    }

    suspend fun deleteEpisode(podcastId: String, episodeId: String): Boolean {
        val res = api.deleteEpisode(podcastId, episodeId)
        return res.isSuccessful
    }

    suspend fun regenerateEpisode(podcastId: String, episodeId: String): Boolean {
        val res = api.regenerateEpisode(podcastId, episodeId)
        return res.isSuccessful
    }
}
