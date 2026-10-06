package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreatePodcastRequest
import com.rodrigos01.aipodcasts.data.model.DesignedVoice
import com.rodrigos01.aipodcasts.data.model.VoiceDesignRequest
import com.rodrigos01.aipodcasts.data.model.Host
import com.rodrigos01.aipodcasts.data.model.HostUpdate
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.PodcastOption
import com.rodrigos01.aipodcasts.data.model.PodcastWizardOptionsRequest
import com.rodrigos01.aipodcasts.data.model.PodcastWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.UpdatePodcastRequest
import com.rodrigos01.aipodcasts.data.model.Voice

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.rodrigos01.aipodcasts.data.firestore.PodcastFirestoreDataSource

import kotlinx.coroutines.flow.Flow

/** Wizard options plus the voice-design session id that goes with them. */
data class PodcastOptionsResult(val sessionId: String?, val options: List<PodcastOption>)

class PodcastRepository(
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

    suspend fun checkHealth(): Boolean {
        return try {
            val res = api.getHealth()
            res.status.equals("ok", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getVoices(): List<Voice> {
        return api.getVoices()
    }

    fun getPodcastsFlow(): Flow<List<Podcast>> {
        return querySource.getPodcastsFlow()
    }

    fun getPodcastFlow(id: String): Flow<Podcast?> {
        return querySource.getPodcastFlow(id)
    }

    suspend fun getPodcasts(): List<Podcast> {
        return querySource.getPodcasts()
    }

    suspend fun getPodcast(id: String): Podcast {
        return querySource.getPodcast(id)
    }

    suspend fun generatePodcastOptions(
        prompt: String,
        sourceMaterial: String? = null
    ): PodcastOptionsResult {
        val request = PodcastWizardOptionsRequest(prompt = prompt, sourceMaterial = sourceMaterial)
        val response = api.generatePodcastOptions(request)
        return PodcastOptionsResult(response.sessionId, response.options)
    }

    suspend fun revisePodcastOptions(
        options: List<PodcastOption>,
        targetIndex: Int? = null,
        instruction: String,
        sessionId: String? = null
    ): PodcastOptionsResult {
        val request = PodcastWizardReviseRequest(
            options = options,
            sessionId = sessionId,
            targetIndex = targetIndex,
            instruction = instruction
        )
        val response = api.revisePodcastOptions(request)
        return PodcastOptionsResult(response.sessionId ?: sessionId, response.options)
    }

    /** Designs up to 3 candidate voices from [prompt] in the podcast's [languageCode] within the given design session. */
    suspend fun designVoices(sessionId: String, prompt: String, languageCode: String? = null): List<DesignedVoice> {
        return api.designVoices(
            VoiceDesignRequest(sessionId = sessionId, prompt = prompt, languageCode = languageCode)
        ).voices
    }

    suspend fun createPodcast(
        title: String,
        description: String,
        structure: String,
        hosts: List<Host>,
        sessionId: String? = null,
        languageCode: String? = null
    ): Podcast {
        val request = CreatePodcastRequest(
            title = title,
            description = description,
            structure = structure,
            languageCode = languageCode,
            hosts = hosts,
            sessionId = sessionId
        )
        return api.createPodcast(request)
    }

    suspend fun updatePodcast(
        id: String,
        title: String? = null,
        description: String? = null,
        structure: String? = null,
        hosts: List<Host>? = null
    ): Podcast {
        val request = UpdatePodcastRequest(
            title = title,
            description = description,
            structure = structure,
            hosts = hosts?.map {
                HostUpdate(it.id.ifBlank { null }, it.name, it.voice, it.persona, it.voicePrompt, it.resolvedVoiceId)
            }
        )
        return api.updatePodcast(id, request)
    }

    suspend fun deletePodcast(id: String): Boolean {
        val res = api.deletePodcast(id)
        return res.isSuccessful
    }
}
