package com.rodrigos01.aipodcasts.data.api

import com.rodrigos01.aipodcasts.AIPodcastsApplication
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
    const val STAGING_BASE_URL = "https://staging-ai-podcast-api-883622140264.us-central1.run.app/"
    const val LOCAL_BASE_URL = "http://10.0.2.2:3000/"

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

    // Commands (POST/PUT/PATCH/DELETE) are the ones that wait on the LLM backend. Keep the app
    // network-capable while they run, even if the user switches away.
    private val keepAliveInterceptor = Interceptor { chain ->
        if (chain.request().method == "GET") {
            chain.proceed(chain.request())
        } else {
            val context = AIPodcastsApplication.instance
            ApiKeepAliveService.acquire(context)
            try {
                chain.proceed(chain.request())
            } finally {
                ApiKeepAliveService.release(context)
            }
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(dynamicUrlInterceptor)
        .addInterceptor(AuthInterceptor())
        .addInterceptor(keepAliveInterceptor)
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

    /** Public sample URL of a designed voice (no auth needed), also valid for a person's stored voice. */
    fun buildVoicePreviewUrl(voiceId: String): String =
        "${currentBaseUrl.trimEnd('/')}/voices/$voiceId/preview"

    /**
     * Returns [url] with its resume offset (`t=<seconds>`) set to [timeSeconds], replacing any
     * existing one; null or non-positive removes it. Used on the public cast URL, which carries
     * no other query parameters.
     */
    fun withStartTime(url: String, timeSeconds: Double?): String {
        val fragmentAt = url.indexOf('#').let { if (it < 0) url.length else it }
        val fragment = url.substring(fragmentAt)
        val beforeFragment = url.substring(0, fragmentAt)
        val path = beforeFragment.substringBefore('?')
        val params = beforeFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() && it != "t" && !it.startsWith("t=") }
            .toMutableList()
        if (timeSeconds != null && timeSeconds > 0) {
            params.add("t=%.1f".format(java.util.Locale.US, timeSeconds))
        }
        return path + (if (params.isEmpty()) "" else "?" + params.joinToString("&")) + fragment
    }

    /**
     * Builds the direct streaming URL for Media3 / ExoPlayer playback
     * Includes Firebase ID token and optional resume timestamp in seconds (?t=)
     */
    fun buildAudioStreamUrl(
        podcastId: String,
        episodeId: String,
        idToken: String? = null,
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
