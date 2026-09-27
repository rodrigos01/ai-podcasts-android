package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeRequest
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeCreateInput
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.EpisodeSuggestion
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardOptionsRequest
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.UpdateEpisodeRequest

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.rodrigos01.aipodcasts.data.firestore.PodcastFirestoreDataSource

import kotlinx.coroutines.flow.Flow

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
    ): List<EpisodeSuggestion> {
        val request = EpisodeWizardOptionsRequest(sourceIds = sourceIds, prompt = prompt, length = length)
        return api.generateEpisodeSuggestions(podcastId, request).suggestions
    }

    suspend fun reviseEpisodeSuggestions(
        podcastId: String,
        suggestions: List<EpisodeSuggestion>,
        length: String,
        targetSuggestionIndex: Int,
        targetEpisodeIndex: Int? = null,
        instruction: String
    ): List<EpisodeSuggestion> {
        val request = EpisodeWizardReviseRequest(
            suggestions = suggestions,
            length = length,
            targetSuggestionIndex = targetSuggestionIndex,
            targetEpisodeIndex = targetEpisodeIndex,
            instruction = instruction
        )
        return api.reviseEpisodeSuggestions(podcastId, request).suggestions
    }

    // Confirms a whole suggestion at once: 1 entry for a single episode, or 2
    // for a confirmed split. Each entry must independently satisfy the
    // 2-speaker rule.
    suspend fun createEpisodes(
        podcastId: String,
        episodes: List<EpisodeCreateInput>
    ): List<Episode> {
        episodes.forEach { episode ->
            require(episode.participantHostIds.size + episode.guests.size == 2) {
                "Every episode must have exactly 2 speakers (2 hosts or 1 host + 1 guest)"
            }
        }
        val request = CreateEpisodeRequest(episodes = episodes)
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
        productionNotes: String? = null
    ): Episode {
        val request = UpdateEpisodeRequest(
            title = title,
            topics = topics,
            productionNotes = productionNotes
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
