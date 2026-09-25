package com.rodrigos01.aipodcasts.data.repository

import com.rodrigos01.aipodcasts.data.api.ApiClient
import com.rodrigos01.aipodcasts.data.api.PodcastApiService
import com.rodrigos01.aipodcasts.data.model.CreateDriveSourceRequest
import com.rodrigos01.aipodcasts.data.model.CreateSourceRequest
import com.rodrigos01.aipodcasts.data.model.Source
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.rodrigos01.aipodcasts.data.firestore.PodcastFirestoreDataSource
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

import kotlinx.coroutines.flow.Flow

class SourceRepository(
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

    fun getSourcesFlow(podcastId: String): Flow<List<Source>> {
        return querySource.getSourcesFlow(podcastId)
    }

    suspend fun getSources(podcastId: String): List<Source> {
        return querySource.getSources(podcastId)
    }

    suspend fun getSource(podcastId: String, sourceId: String): Source {
        return querySource.getSource(podcastId, sourceId)
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
        fileName: String = file.name,
        customTitle: String? = null
    ): Source {
        val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
        val title = customTitle?.takeIf { it.isNotBlank() } ?: fileName
        val titlePart = title.toRequestBody("text/plain".toMediaTypeOrNull())
        return api.uploadSourceFile(podcastId, body, titlePart)
    }

    suspend fun createDriveSource(
        podcastId: String,
        fileId: String,
        accessToken: String,
        title: String? = null
    ): Source {
        val request = CreateDriveSourceRequest(
            fileId = fileId,
            accessToken = accessToken,
            title = title
        )
        return api.createDriveSource(podcastId, request)
    }

    suspend fun deleteSource(podcastId: String, sourceId: String): Boolean {
        val res = api.deleteSource(podcastId, sourceId)
        return res.isSuccessful
    }
}
