package com.miireader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miireader.data.download.DownloadManager
import com.miireader.data.download.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)

    private val downloadsFlow = downloadRepository.getAllDownloads()
        .map { downloads -> downloads.map { it.toUi() } }

    val uiState: StateFlow<DownloadsUiState> = combine(
        downloadsFlow,
        _errorMessage
    ) { downloads, errorMessage ->
        DownloadsUiState(downloads = downloads, errorMessage = errorMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadsUiState()
    )

    /** Retries a failed download. */
    fun retryDownload(id: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                downloadManager.retryDownload(id)
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't retry download: ${e.message}"
            }
        }
    }

    /** Cancels and removes a download. */
    fun cancelDownload(id: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                downloadManager.cancelDownload(id)
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't cancel download: ${e.message}"
            }
        }
    }

    /** Removes a completed or failed download from the list. */
    fun removeDownload(id: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                downloadManager.cancelDownload(id)
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't remove download: ${e.message}"
            }
        }
    }

    /** Clears any error message currently shown. */
    fun clearError() {
        _errorMessage.value = null
    }
}