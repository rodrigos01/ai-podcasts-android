package com.rodrigos01.aipodcasts.ui.screens.source

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigos01.aipodcasts.AIPodcastsApplication
import com.rodrigos01.aipodcasts.data.model.Source
import com.rodrigos01.aipodcasts.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class SourcesUiState(
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val sources: List<Source> = emptyList(),
    val sourceTitle: String = "",
    val sourceContent: String = "",
    val showAddDialog: Boolean = false,
    val errorMessage: String? = null
)

class SourcesViewModel(
    private val sourceRepo: SourceRepository = AIPodcastsApplication.instance.sourceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    fun loadSources(podcastId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val list = sourceRepo.getSources(podcastId)
                _uiState.value = _uiState.value.copy(isLoading = false, sources = list)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            sourceTitle = "",
            sourceContent = "",
            errorMessage = null
        )
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(sourceTitle = title)
    }

    fun onContentChanged(content: String) {
        _uiState.value = _uiState.value.copy(sourceContent = content)
    }

    fun createTextSource(podcastId: String) {
        val title = _uiState.value.sourceTitle.trim()
        val content = _uiState.value.sourceContent.trim()
        if (title.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
            try {
                val created = sourceRepo.createTextSource(podcastId, title, content)
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    showAddDialog = false,
                    sources = listOf(created) + _uiState.value.sources
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun uploadPdfSource(context: Context, podcastId: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.pdf")
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val created = sourceRepo.uploadPdfSource(podcastId, tempFile)
                tempFile.delete()

                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    showAddDialog = false,
                    sources = listOf(created) + _uiState.value.sources
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = e.localizedMessage ?: e.message
                )
            }
        }
    }

    fun deleteSource(podcastId: String, sourceId: String) {
        viewModelScope.launch {
            try {
                sourceRepo.deleteSource(podcastId, sourceId)
                _uiState.value = _uiState.value.copy(
                    sources = _uiState.value.sources.filter { it.id != sourceId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage ?: e.message)
            }
        }
    }
}
