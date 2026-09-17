package com.rodrigos01.aipodcasts.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {

    /**
     * Resolves the actual user-visible file name from a content or file Uri.
     * Uses OpenableColumns.DISPLAY_NAME if available, falling back to lastPathSegment.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore cursor query failure and fall back
            }
        }

        if (name.isNullOrBlank()) {
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank()) {
                name = segment.substringAfterLast('/')
            }
        }

        return if (!name.isNullOrBlank()) name!! else "document"
    }

    /**
     * Checks if the given URI or filename represents a plain text file.
     */
    fun isTextFile(context: Context, uri: Uri, fileName: String? = null): Boolean {
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        if (mimeType != null && (mimeType.startsWith("text/") || mimeType == "text/plain")) {
            return true
        }
        val name = fileName ?: getFileName(context, uri)
        return name.endsWith(".txt", ignoreCase = true)
    }

    /**
     * Checks if the given URI or filename represents a PDF file.
     */
    fun isPdfFile(context: Context, uri: Uri, fileName: String? = null): Boolean {
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        if (mimeType != null && (mimeType == "application/pdf" || mimeType.contains("pdf"))) {
            return true
        }
        val name = fileName ?: getFileName(context, uri)
        return name.endsWith(".pdf", ignoreCase = true)
    }

    /**
     * Extracts the raw display name from a content URI without stripping extensions.
     */
    fun getRawDisplayName(context: Context, uri: Uri): String? {
        var displayName: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore and fallback
            }
        }

        if (displayName == null) {
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank()) {
                displayName = segment.substringAfterLast('/')
            }
        }

        return displayName?.trim()?.ifBlank { null }
    }

    /**
     * Strips file extension and trims whitespace from a filename.
     */
    fun cleanFileName(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        return fileName.substringBeforeLast('.').trim().ifBlank { null }
    }

    /**
     * Extracts the display name from a content URI and strips the file extension (.txt, etc.).
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val raw = getRawDisplayName(context, uri)
        return cleanFileName(raw)
    }

    /**
     * Reads plain text from a content URI.
     */
    fun readTextFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: throw IOException("Could not read content from selected file.")
    }

    /**
     * Copies a content URI into a temporary cache file.
     */
    fun copyUriToTempFile(context: Context, uri: Uri, tempFileName: String): File {
        val tempFile = File(context.cacheDir, tempFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Could not copy file stream from URI.")
        return tempFile
    }

    /**
     * Creates an Intent to open the Google Drive native file picker.
     */
    fun createGoogleDrivePickerIntent(context: Context? = null, accountEmail: String? = null): android.content.Intent {
        val driveGetContent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                android.content.Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/plain",
                    "text/*",
                    "application/pdf",
                    "application/vnd.google-apps.document",
                    "*/*"
                )
            )
            if (!accountEmail.isNullOrBlank()) {
                putExtra("account_name", accountEmail)
                putExtra("authAccount", accountEmail)
                putExtra("account", accountEmail)
            }
            `package` = "com.google.android.apps.docs"
        }

        if (context != null) {
            val pm = context.packageManager
            if (driveGetContent.resolveActivity(pm) != null) {
                return driveGetContent
            }

            // Check ACTION_PICK for Drive
            val drivePick = android.content.Intent(android.content.Intent.ACTION_PICK).apply {
                type = "*/*"
                `package` = "com.google.android.apps.docs"
            }
            if (drivePick.resolveActivity(pm) != null) {
                return drivePick
            }

            // Fallback to DocumentsUI with initial URI pointing to Google Drive
            val driveDocIntent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    android.content.Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "text/plain",
                        "text/*",
                        "application/pdf",
                        "application/vnd.google-apps.document",
                        "*/*"
                    )
                )
                try {
                    putExtra(
                        android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.google.android.apps.docs.storage/document/root")
                    )
                } catch (_: Exception) {}
            }
            if (driveDocIntent.resolveActivity(pm) != null) {
                return driveDocIntent
            }
        }

        return driveGetContent
    }
}

