package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeRequest
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeDraft
import com.rodrigos01.aipodcasts.data.model.EpisodeGuest
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardOptionsRequest
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.UpdateEpisodeRequest

class EpisodeRepository(
    private val api: PodcastApiService = ApiClient.apiService
) {

    suspend fun generateEpisodeDraft(
        podcastId: String,
        sourceIds: List<String>,
        prompt: String? = null
    ): EpisodeDraft {
        val request = EpisodeWizardOptionsRequest(sourceIds = sourceIds, prompt = prompt)
        return api.generateEpisodeDraft(podcastId, request).draft
    }

    suspend fun reviseEpisodeDraft(
        podcastId: String,
        draft: EpisodeDraft,
        instruction: String
    ): EpisodeDraft {
        val request = EpisodeWizardReviseRequest(draft = draft, instruction = instruction)
        return api.reviseEpisodeDraft(podcastId, request).draft
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
            title = title,
            topics = topics,
            length = length,
            sourceIds = sourceIds,
            participantHostIds = participantHostIds,
            guests = guests,
            productionNotes = productionNotes
        )
        return api.createEpisode(podcastId, request)
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
