package com.rodrigos01.aipodcasts.data.api

import com.rodrigos01.aipodcasts.data.model.CreateDriveSourceRequest
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeRequest
import com.rodrigos01.aipodcasts.data.model.CreateEpisodeResponse
import com.rodrigos01.aipodcasts.data.model.CreatePodcastRequest
import com.rodrigos01.aipodcasts.data.model.CreateSourceRequest
import com.rodrigos01.aipodcasts.data.model.Episode
import com.rodrigos01.aipodcasts.data.model.EpisodeStatusResponse
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardOptionsRequest
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.EpisodeWizardSuggestionsResponse
import com.rodrigos01.aipodcasts.data.model.HealthResponse
import com.rodrigos01.aipodcasts.data.model.Podcast
import com.rodrigos01.aipodcasts.data.model.PodcastWizardOptionsRequest
import com.rodrigos01.aipodcasts.data.model.PodcastWizardOptionsResponse
import com.rodrigos01.aipodcasts.data.model.PodcastWizardReviseRequest
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.model.UpdateEpisodeRequest
import com.rodrigos01.aipodcasts.data.model.UpdatePodcastRequest
import com.rodrigos01.aipodcasts.data.model.Voice
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PodcastApiService {

    // Health & Reference
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("voices")
    suspend fun getVoices(): List<Voice>

    // Podcast Wizard & CRUD
    @POST("podcasts/wizard/options")
    suspend fun generatePodcastOptions(
        @Body request: PodcastWizardOptionsRequest
    ): PodcastWizardOptionsResponse

    @POST("podcasts/wizard/revise")
    suspend fun revisePodcastOptions(
        @Body request: PodcastWizardReviseRequest
    ): PodcastWizardOptionsResponse

    @POST("podcasts")
    suspend fun createPodcast(
        @Body request: CreatePodcastRequest
    ): Podcast

    @GET("podcasts")
    suspend fun getPodcasts(): List<Podcast>

    @GET("podcasts/{podcastId}")
    suspend fun getPodcast(
        @Path("podcastId") podcastId: String
    ): Podcast

    @PATCH("podcasts/{podcastId}")
    suspend fun updatePodcast(
        @Path("podcastId") podcastId: String,
        @Body request: UpdatePodcastRequest
    ): Podcast

    @DELETE("podcasts/{podcastId}")
    suspend fun deletePodcast(
        @Path("podcastId") podcastId: String
    ): Response<Unit>

    // Sources
    @POST("podcasts/{podcastId}/sources")
    suspend fun createSource(
        @Path("podcastId") podcastId: String,
        @Body request: CreateSourceRequest
    ): Source

    @POST("podcasts/{podcastId}/sources")
    suspend fun createDriveSource(
        @Path("podcastId") podcastId: String,
        @Body request: CreateDriveSourceRequest
    ): Source

    @Multipart
    @POST("podcasts/{podcastId}/sources")
    suspend fun uploadSourceFile(
        @Path("podcastId") podcastId: String,
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody? = null
    ): Source

    @GET("podcasts/{podcastId}/sources")
    suspend fun getSources(
        @Path("podcastId") podcastId: String
    ): List<Source>

    @GET("podcasts/{podcastId}/sources/{sourceId}")
    suspend fun getSource(
        @Path("podcastId") podcastId: String,
        @Path("sourceId") sourceId: String
    ): Source

    @DELETE("podcasts/{podcastId}/sources/{sourceId}")
    suspend fun deleteSource(
        @Path("podcastId") podcastId: String,
        @Path("sourceId") sourceId: String
    ): Response<Unit>

    // Episode Wizard & CRUD
    @POST("podcasts/{podcastId}/episodes/wizard/options")
    suspend fun generateEpisodeSuggestions(
        @Path("podcastId") podcastId: String,
        @Body request: EpisodeWizardOptionsRequest
    ): EpisodeWizardSuggestionsResponse

    @POST("podcasts/{podcastId}/episodes/wizard/revise")
    suspend fun reviseEpisodeSuggestions(
        @Path("podcastId") podcastId: String,
        @Body request: EpisodeWizardReviseRequest
    ): EpisodeWizardSuggestionsResponse

    @POST("podcasts/{podcastId}/episodes")
    suspend fun createEpisodes(
        @Path("podcastId") podcastId: String,
        @Body request: CreateEpisodeRequest
    ): CreateEpisodeResponse

    @GET("podcasts/{podcastId}/episodes")
    suspend fun getEpisodes(
        @Path("podcastId") podcastId: String
    ): List<Episode>

    @GET("podcasts/{podcastId}/episodes/{episodeId}")
    suspend fun getEpisode(
        @Path("podcastId") podcastId: String,
        @Path("episodeId") episodeId: String
    ): Episode

    @GET("podcasts/{podcastId}/episodes/{episodeId}/status")
    suspend fun getEpisodeStatus(
        @Path("podcastId") podcastId: String,
        @Path("episodeId") episodeId: String
    ): EpisodeStatusResponse

    @PATCH("podcasts/{podcastId}/episodes/{episodeId}")
    suspend fun updateEpisode(
        @Path("podcastId") podcastId: String,
        @Path("episodeId") episodeId: String,
        @Body request: UpdateEpisodeRequest
    ): Episode

    @DELETE("podcasts/{podcastId}/episodes/{episodeId}")
    suspend fun deleteEpisode(
        @Path("podcastId") podcastId: String,
        @Path("episodeId") episodeId: String
    ): Response<Unit>

    @POST("podcasts/{podcastId}/episodes/{episodeId}/regenerate")
    suspend fun regenerateEpisode(
        @Path("podcastId") podcastId: String,
        @Path("episodeId") episodeId: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<Unit>
}
