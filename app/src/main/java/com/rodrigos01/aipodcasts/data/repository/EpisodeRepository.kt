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

class EpisodeRepository(
    private val api: PodcastApiService = ApiClient.apiService
) {

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
        instruction: String
    ): List<EpisodeSuggestion> {
        val request = EpisodeWizardReviseRequest(
            suggestions = suggestions,
            length = length,
            targetSuggestionIndex = targetSuggestionIndex,
            instruction = instruction
        )
        return api.reviseEpisodeSuggestions(podcastId, request).suggestions
    }

    suspend fun createEpisode(
        podcastId: String,
        title: String,
        topics: String,
        length: String,
        sourceIds: List<String>,
        participantHostIds: List<String>,
        guests: List<EpisodeGuest>,
        productionNotes: String
    ): Episode {
        require(participantHostIds.size + guests.size == 2) {
            "An episode must have exactly 2 speakers (2 hosts or 1 host + 1 guest)"
        }

        val request = CreateEpisodeRequest(
            episodes = listOf(
                EpisodeCreateInput(
                    title = title,
                    topics = topics,
                    length = length,
                    sourceIds = sourceIds,
                    participantHostIds = participantHostIds,
                    guests = guests,
                    productionNotes = productionNotes
                )
            )
        )
        return api.createEpisodes(podcastId, request).episodes.first()
    }

    suspend fun getEpisodes(podcastId: String): List<Episode> {
        return api.getEpisodes(podcastId)
    }

    suspend fun getEpisode(podcastId: String, episodeId: String): Episode {
        return api.getEpisode(podcastId, episodeId)
    }

    suspend fun getEpisodeStatus(podcastId: String, episodeId: String): EpisodeStatusResponse {
        return api.getEpisodeStatus(podcastId, episodeId)
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

    suspend fun regenerateEpisode(podcastId: String, episodeId: String): Episode {
        return api.regenerateEpisode(podcastId, episodeId)
    }
}
