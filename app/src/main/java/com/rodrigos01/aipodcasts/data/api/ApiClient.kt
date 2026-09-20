package com.rodrigos01.aipodcasts.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val DEFAULT_BASE_URL = "https://ai-podcast-api-883622140264.us-central1.run.app/"
    const val EMULATOR_LOCAL_BASE_URL = "http://10.0.2.2:3000/"

    @Volatile
    var currentBaseUrl: String = DEFAULT_BASE_URL
        set(value) {
            val normalized = if (value.endsWith("/")) value else "$value/"
            field = normalized
        }

    private val dynamicUrlInterceptor = Interceptor { chain ->
        var request = chain.request()
        val newBase = currentBaseUrl.toHttpUrlOrNull()
        if (newBase != null) {
            val originalUrl = request.url
            val newUrl = originalUrl.newBuilder()
                .scheme(newBase.scheme)
                .host(newBase.host)
                .port(newBase.port)
                .build()
            request = request.newBuilder().url(newUrl).build()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(dynamicUrlInterceptor)
        .addInterceptor(AuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(DEFAULT_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: PodcastApiService = retrofit.create(PodcastApiService::class.java)

    /**
     * Builds the direct streaming URL for Media3 / ExoPlayer playback
     * Includes Firebase ID token and optional resume timestamp in seconds (?t=)
     */
    fun buildAudioStreamUrl(
        podcastId: String,
        episodeId: String,
        idToken: String?,
        timeSeconds: Double? = null
    ): String {
        val base = currentBaseUrl.trimEnd('/')
        var url = "$base/podcasts/$podcastId/episodes/$episodeId/audio/stream"
        val params = mutableListOf<String>()
        if (!idToken.isNullOrBlank()) {
            params.add("token=$idToken")
        }
        if (timeSeconds != null && timeSeconds > 0) {
            params.add("t=%.1f".format(java.util.Locale.US, timeSeconds))
        }
        if (params.isNotEmpty()) {
            url += "?" + params.joinToString("&")
        }
        return url
    }
}
