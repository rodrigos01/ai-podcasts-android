package com.rodrigos01.aipodcasts.data.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class DriveFileInfo(
    val id: String,
    val name: String,
    val mimeType: String? = null
)

object GoogleDriveHelper {
    private const val TAG = "GoogleDriveHelper"

    val DOCS_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/documents.readonly"),
        Scope("https://www.googleapis.com/auth/drive.readonly")
    )

    private val httpClient by lazy { OkHttpClient() }

    /**
     * Initiates OAuth 2.0 authorization for Google Docs/Drive scopes without forcing repeated consent.
     * Passes the user's account if available to avoid asking the user to pick an account again.
     */
    suspend fun requestDocsAuthorization(context: Context, accountEmail: String? = null): AuthorizationResult {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(DOCS_SCOPES)

        val email = accountEmail ?: FirebaseAuth.getInstance().currentUser?.email
        if (!email.isNullOrBlank()) {
            builder.setAccount(Account(email, "com.google"))
        }

        val request = builder.build()

        return Identity.getAuthorizationClient(context)
            .authorize(request)
            .await()
    }

    /**
     * Retrieves the AuthorizationResult from an Activity result intent after user consent.
     */
    fun getAuthorizationResultFromIntent(context: Context, data: Intent?): AuthorizationResult? {
        if (data == null) return null
        return try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting authorization result from intent", e)
            null
        }
    }

    /**
     * Extracts a Google Doc or Drive File ID from a content URI, file path, or docs/drive URL.
     * Rejects encoded SAF IDs (e.g., enc=encoded=...).
     */
    fun extractGoogleDocId(uri: Uri): String? {
        val uriString = uri.toString()

        // 1. Check docs.google.com web URL format
        val docUrlRegex = Regex("/document/d/([a-zA-Z0-9_-]+)")
        docUrlRegex.find(uriString)?.let {
            return it.groupValues[1]
        }

        // 2. Check drive.google.com/file/d/...
        val driveFileRegex = Regex("/file/d/([a-zA-Z0-9_-]+)")
        driveFileRegex.find(uriString)?.let {
            return it.groupValues[1]
        }

        // 3. Check doc= or doc%3D query/path parameter
        val docParamRegex = Regex("(?:doc=|doc%3D)([a-zA-Z0-9_-]+)")
        docParamRegex.find(uriString)?.let { match ->
            val id = match.groupValues[1]
            if (!id.startsWith("enc") && id.length >= 25) {
                return id
            }
        }

        // 4. Check DocumentsContract document ID
        try {
            val docId = DocumentsContract.getDocumentId(uri)
            if (!docId.isNullOrBlank() && !docId.startsWith("enc=") && !docId.contains("encoded=")) {
                val docMatch = docParamRegex.find(docId)
                if (docMatch != null && !docMatch.groupValues[1].startsWith("enc")) {
                    return docMatch.groupValues[1]
                }
                val cleanId = docId.substringAfterLast(':').substringAfterLast('/')
                if (cleanId.length >= 25 && !cleanId.startsWith("enc")) return cleanId
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Resolves the real Google Drive File ID using the Google Drive REST API and user's OAuth access token.
     * Uses a resilient multi-step lookup (exact name, clean name, partial name, and recent Google Docs).
     */
    suspend fun resolveDriveFileId(
        rawName: String,
        cleanName: String?,
        accessToken: String
    ): String? = withContext(Dispatchers.IO) {
        val queries = mutableListOf<String>()

        val escapedRaw = rawName.replace("'", "\\'")
        queries.add("name = '$escapedRaw' and trashed = false")

        if (!cleanName.isNullOrBlank() && cleanName != rawName) {
            val escapedClean = cleanName.replace("'", "\\'")
            queries.add("name = '$escapedClean' and trashed = false")
        }

        val searchBase = cleanName?.takeIf { it.isNotBlank() } ?: rawName
        if (searchBase.isNotBlank()) {
            val escapedSearch = searchBase.replace("'", "\\'")
            queries.add("name contains '$escapedSearch' and trashed = false")
        }

        queries.add("mimeType = 'application/vnd.google-apps.document' and trashed = false")

        for (query in queries) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=10"

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val files = json.optJSONArray("files")
                            if (files != null && files.length() > 0) {
                                for (i in 0 until files.length()) {
                                    val file = files.getJSONObject(i)
                                    val fileName = file.optString("name")
                                    val fileId = file.optString("id")
                                    if (fileId.isNotBlank()) {
                                        if (fileName.equals(rawName, ignoreCase = true) ||
                                            fileName.equals(cleanName, ignoreCase = true) ||
                                            fileName.contains(searchBase, ignoreCase = true)) {
                                            Log.d(TAG, "Resolved file '$rawName' to Drive ID: $fileId (name: $fileName)")
                                            return@withContext fileId
                                        }
                                    }
                                }
                                if (query == queries.last()) {
                                    val firstDoc = files.getJSONObject(0)
                                    val firstId = firstDoc.optString("id")
                                    if (firstId.isNotBlank()) {
                                        Log.d(TAG, "Fallback: using most recently modified Google Doc: $firstId (${firstDoc.optString("name")})")
                                        return@withContext firstId
                                    }
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "Drive API error [${response.code}]: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing Drive query: $query", e)
            }
        }
        return@withContext null
    }

    /**
     * Fetches file metadata (display name, mimeType) from Drive API v3 using the access token.
     */
    suspend fun fetchDriveFileMetadata(fileId: String, accessToken: String): DriveFileInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext null
                        val json = JSONObject(body)
                        return@withContext DriveFileInfo(
                            id = json.optString("id", fileId),
                            name = json.optString("name", "Google Drive Document"),
                            mimeType = json.optString("mimeType")
                        )
                    }
                }
            } catch (_: Exception) {
                // Failure communicating with Drive API
            }
            null
        }
}

