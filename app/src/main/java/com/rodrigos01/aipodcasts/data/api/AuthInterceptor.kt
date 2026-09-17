package com.rodrigos01.aipodcasts.data.api

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class AuthInterceptor : Interceptor {

    private val auth: FirebaseAuth?
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // /health and /voices don't require auth
        if (path.endsWith("/health") || path.endsWith("/voices")) {
            return chain.proceed(originalRequest)
        }

        val user = auth?.currentUser
        val token = if (user != null) {
            try {
                val tokenTask = user.getIdToken(false)
                val result = Tasks.await(tokenTask, 5, TimeUnit.SECONDS)
                result?.token
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
