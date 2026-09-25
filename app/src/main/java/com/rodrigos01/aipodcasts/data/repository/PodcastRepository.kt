package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreatePodcastRequest
import com.rodrigos01.aipodcasts.data.model.Host
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
    ): List<PodcastOption> {
        val request = PodcastWizardOptionsRequest(prompt = prompt, sourceMaterial = sourceMaterial)
        return api.generatePodcastOptions(request).options
    }

    suspend fun revisePodcastOptions(
        options: List<PodcastOption>,
        targetIndex: Int? = null,
        instruction: String
    ): List<PodcastOption> {
        val request = PodcastWizardReviseRequest(
            options = options,
            targetIndex = targetIndex,
            instruction = instruction
        )
        return api.revisePodcastOptions(request).options
    }

    suspend fun createPodcast(
        title: String,
        description: String,
        structure: String,
        hosts: List<Host>
    ): Podcast {
        val request = CreatePodcastRequest(
            title = title,
            description = description,
            structure = structure,
            hosts = hosts
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
            hosts = hosts
        )
        return api.updatePodcast(id, request)
    }

    suspend fun deletePodcast(id: String): Boolean {
        val res = api.deletePodcast(id)
        return res.isSuccessful
    }
}
