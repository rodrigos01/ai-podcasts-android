package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreateSourceRequest
import com.rodrigos01.aipodcasts.data.model.Source
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class SourceRepository(
    private val api: PodcastApiService = ApiClient.apiService
) {

    suspend fun getSources(podcastId: String): List<Source> {
        return api.getSources(podcastId)
    }

    suspend fun getSource(podcastId: String, sourceId: String): Source {
        return api.getSource(podcastId, sourceId)
    }

    suspend fun createTextSource(
        podcastId: String,
        title: String,
        contents: String
    ): Source {
        val request = CreateSourceRequest(title = title, contents = contents)
        return api.createSource(podcastId, request)
    }

    suspend fun uploadPdfSource(
        podcastId: String,
        file: File,
        customTitle: String? = null
    ): Source {
        val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val titlePart = customTitle?.toRequestBody("text/plain".toMediaTypeOrNull())
        return api.uploadSourceFile(podcastId, body, titlePart)
    }

    suspend fun deleteSource(podcastId: String, sourceId: String): Boolean {
        val res = api.deleteSource(podcastId, sourceId)
        return res.isSuccessful
    }
}
