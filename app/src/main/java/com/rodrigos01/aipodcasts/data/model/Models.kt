package com.rodrigos01.aipodcasts.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Voice(
    @Json(name = "id") val id: String,
    @Json(name = "gender") val gender: String,
    @Json(name = "characterTrait") val characterTrait: String
)

@JsonClass(generateAdapter = true)
data class Host(
    @Json(name = "id") val id: String = "",
    @Json(name = "name") val name: String,
    @Json(name = "voice") val voice: String,
    @Json(name = "persona") val persona: String
)

@JsonClass(generateAdapter = true)
data class Podcast(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "structure") val structure: String,
    @Json(name = "hosts") val hosts: List<Host> = emptyList(),
    @Json(name = "createdAt") val createdAt: Any? = null
)

@JsonClass(generateAdapter = true)
data class CreatePodcastRequest(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "structure") val structure: String,
    @Json(name = "hosts") val hosts: List<Host>
)

@JsonClass(generateAdapter = true)
data class UpdatePodcastRequest(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "structure") val structure: String? = null,
    @Json(name = "hosts") val hosts: List<Host>? = null
)

@JsonClass(generateAdapter = true)
data class PodcastOption(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "structure") val structure: String,
    @Json(name = "hosts") val hosts: List<Host> = emptyList(),
    @Json(name = "predictedChanges") val predictedChanges: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PodcastWizardOptionsRequest(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "sourceMaterial") val sourceMaterial: String? = null
)

@JsonClass(generateAdapter = true)
data class PodcastWizardOptionsResponse(
    @Json(name = "options") val options: List<PodcastOption>
)

@JsonClass(generateAdapter = true)
data class PodcastWizardReviseRequest(
    @Json(name = "options") val options: List<PodcastOption>,
    @Json(name = "targetIndex") val targetIndex: Int? = null,
    @Json(name = "instruction") val instruction: String
)

// Sources
@JsonClass(generateAdapter = true)
data class Source(
    @Json(name = "id") val id: String,
    @Json(name = "podcastId") val podcastId: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "contents") val contents: String,
    @Json(name = "sourceType") val sourceType: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateSourceRequest(
    @Json(name = "title") val title: String,
    @Json(name = "contents") val contents: String
)

@JsonClass(generateAdapter = true)
data class CreateDriveSourceRequest(
    @Json(name = "fileId") val fileId: String,
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "title") val title: String? = null
)

// Episodes
@JsonClass(generateAdapter = true)
data class EpisodeGuest(
    @Json(name = "name") val name: String,
    @Json(name = "voice") val voice: String,
    @Json(name = "persona") val persona: String
)

@JsonClass(generateAdapter = true)
data class EpisodeDraft(
    @Json(name = "title") val title: String,
    @Json(name = "topics") val topics: String,
    @Json(name = "productionNotes") val productionNotes: String = "",
    @Json(name = "guests") val guests: List<EpisodeGuest> = emptyList(),
    @Json(name = "predictedChanges") val predictedChanges: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EpisodeWizardOptionsRequest(
    @Json(name = "sourceIds") val sourceIds: List<String>,
    @Json(name = "prompt") val prompt: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeWizardDraftResponse(
    @Json(name = "draft") val draft: EpisodeDraft
)

@JsonClass(generateAdapter = true)
data class EpisodeWizardReviseRequest(
    @Json(name = "draft") val draft: EpisodeDraft,
    @Json(name = "instruction") val instruction: String
)

@JsonClass(generateAdapter = true)
data class CreateEpisodeRequest(
    @Json(name = "title") val title: String,
    @Json(name = "topics") val topics: String,
    @Json(name = "length") val length: String, // "short" | "medium" | "long"
    @Json(name = "sourceIds") val sourceIds: List<String>,
    @Json(name = "participantHostIds") val participantHostIds: List<String>,
    @Json(name = "guests") val guests: List<EpisodeGuest> = emptyList(),
    @Json(name = "productionNotes") val productionNotes: String = ""
)

@JsonClass(generateAdapter = true)
data class UpdateEpisodeRequest(
    @Json(name = "title") val title: String? = null,
    @Json(name = "topics") val topics: String? = null,
    @Json(name = "productionNotes") val productionNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeProgress(
    @Json(name = "stage") val stage: String? = null,
    @Json(name = "wordCount") val wordCount: Int? = null,
    @Json(name = "totalWords") val totalWords: Int? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeStatusResponse(
    @Json(name = "status") val status: String,
    @Json(name = "progress") val progress: EpisodeProgress? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "generatedAudioSeconds") val generatedAudioSeconds: Double? = null
)

@JsonClass(generateAdapter = true)
data class TranscriptItem(
    @Json(name = "speaker") val speaker: String = "",
    @Json(name = "text") val text: String = "",
    @Json(name = "action") val action: String? = null
)

@JsonClass(generateAdapter = true)
data class Episode(
    @Json(name = "id") val id: String,
    @Json(name = "podcastId") val podcastId: String = "",
    @Json(name = "title") val title: String,
    @Json(name = "topics") val topics: String = "",
    @Json(name = "length") val length: String = "short",
    @Json(name = "sourceIds") val sourceIds: List<String> = emptyList(),
    @Json(name = "participantHostIds") val participantHostIds: List<String> = emptyList(),
    @Json(name = "guests") val guests: List<EpisodeGuest> = emptyList(),
    @Json(name = "productionNotes") val productionNotes: String? = null,
    @Json(name = "status") val status: String = "generating",
    @Json(name = "progress") val progress: EpisodeProgress? = null,
    @Json(name = "transcript") val transcript: Any? = null,
    @Json(name = "ttsPrompt") val ttsPrompt: String? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String = "ok"
)
