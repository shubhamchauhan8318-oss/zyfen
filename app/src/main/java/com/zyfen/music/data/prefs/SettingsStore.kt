package com.zyfen.music.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map

class SettingsStore(
    private val ds: DataStore<Preferences>
) {
    companion object {
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val THEME = stringPreferencesKey("theme")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "light", "dark", "amoled", "system"
        val ACCENT_COLOR = stringPreferencesKey("accent_color") // Hex, e.g. #8B5CF6
        val DYNAMIC_ARTWORK = booleanPreferencesKey("dynamic_artwork")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val UI_STYLE = stringPreferencesKey("ui_style") // "glass", "classic"
        val REVERB_PRESET = stringPreferencesKey("reverb_preset")
        val REVERB_ENABLED = booleanPreferencesKey("reverb_enabled")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    val shuffle = ds.data.map { it[SHUFFLE] ?: false }
    val repeatMode = ds.data.map { it[REPEAT_MODE] ?: 0 }
    val theme = ds.data.map { it[THEME] ?: "neon" }
    val themeMode = ds.data.map { it[THEME_MODE] ?: "dark" }
    val accentColor = ds.data.map { it[ACCENT_COLOR] ?: "#8B5CF6" }
    val dynamicArtwork = ds.data.map { it[DYNAMIC_ARTWORK] ?: false }
    val audioQuality = ds.data.map { it[AUDIO_QUALITY] ?: "320" }
    val equalizerPreset = ds.data.map { it[EQUALIZER_PRESET] ?: "Balanced" }
    val uiStyle = ds.data.map { it[UI_STYLE] ?: "glass" }
    val reverbPreset = ds.data.map { it[REVERB_PRESET] ?: "Off" }
    val reverbEnabled = ds.data.map { it[REVERB_ENABLED] ?: false }
    val searchHistory = ds.data.map { prefs ->
        val raw = prefs[SEARCH_HISTORY] ?: ""
        if (raw.isBlank()) emptyList<String>() else raw.split("\n").filter { it.isNotBlank() }
    }

    suspend fun setShuffle(v: Boolean) { ds.updateData { it.toMutablePreferences().apply { set(SHUFFLE, v) } } }
    suspend fun setRepeatMode(v: Int) { ds.updateData { it.toMutablePreferences().apply { set(REPEAT_MODE, v) } } }
    suspend fun setTheme(v: String) { ds.updateData { it.toMutablePreferences().apply { set(THEME, v) } } }
    suspend fun setThemeMode(v: String) { ds.updateData { it.toMutablePreferences().apply { set(THEME_MODE, v) } } }
    suspend fun setAccentColor(v: String) { ds.updateData { it.toMutablePreferences().apply { set(ACCENT_COLOR, v) } } }
    suspend fun setDynamicArtwork(v: Boolean) { ds.updateData { it.toMutablePreferences().apply { set(DYNAMIC_ARTWORK, v) } } }
    suspend fun setAudioQuality(v: String) { ds.updateData { it.toMutablePreferences().apply { set(AUDIO_QUALITY, v) } } }
    suspend fun setEqualizerPreset(v: String) { ds.updateData { it.toMutablePreferences().apply { set(EQUALIZER_PRESET, v) } } }
    suspend fun setUiStyle(v: String) { ds.updateData { it.toMutablePreferences().apply { set(UI_STYLE, v) } } }
    suspend fun setReverbPreset(v: String) { ds.updateData { it.toMutablePreferences().apply { set(REVERB_PRESET, v) } } }
    suspend fun setReverbEnabled(v: Boolean) { ds.updateData { it.toMutablePreferences().apply { set(REVERB_ENABLED, v) } } }

    suspend fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        ds.updateData { prefs ->
            val existing = (prefs[SEARCH_HISTORY] ?: "").split("\n").filter { it.isNotBlank() }
            val updated = listOf(trimmed) + existing.filter { !it.equals(trimmed, ignoreCase = true) }
            prefs.toMutablePreferences().apply {
                set(SEARCH_HISTORY, updated.take(30).joinToString("\n"))
            }
        }
    }

    suspend fun removeSearchHistory(query: String) {
        ds.updateData { prefs ->
            val existing = (prefs[SEARCH_HISTORY] ?: "").split("\n").filter { it.isNotBlank() }
            val updated = existing.filter { !it.equals(query.trim(), ignoreCase = true) }
            prefs.toMutablePreferences().apply {
                set(SEARCH_HISTORY, updated.joinToString("\n"))
            }
        }
    }

    suspend fun clearSearchHistory() {
        ds.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                remove(SEARCH_HISTORY)
            }
        }
    }
}
