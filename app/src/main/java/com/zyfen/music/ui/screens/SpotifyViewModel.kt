package com.zyfen.music.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyfen.music.data.spotify.ImportedSpotifyPlaylist
import com.zyfen.music.data.spotify.SpotifyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SpotifyViewModel(
    private val repo: SpotifyRepository
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _result = MutableStateFlow<ImportedSpotifyPlaylist?>(null)
    val result = _result.asStateFlow()
    private val _progress = MutableStateFlow<String?>(null)
    val progress = _progress.asStateFlow()

    fun import(link: String) = viewModelScope.launch {
        _busy.value = true; _error.value = null; _result.value = null
        _progress.value = null
        try {
            _result.value = repo.importPlaylist(link) { done, total ->
                _progress.value = "Importing $done / $total"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message ?: "Import failed"
        }
        _progress.value = null
        _busy.value = false
    }

    fun clear() { _error.value = null; _result.value = null }
}
