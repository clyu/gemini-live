package io.github.clyu.geminilive.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<LiveSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            LiveSettings(
                apiKey = prefs[API_KEY].orEmpty(),
                model = prefs[MODEL]?.takeIf { it.isNotBlank() } ?: LiveSettings.DEFAULT_MODEL,
                voice = prefs[VOICE]?.takeIf { it.isNotBlank() } ?: LiveSettings.DEFAULT_VOICE,
                thinkingLevel = prefs[THINKING_LEVEL].orEmpty(),
                transcriptionLanguages = prefs[TRANSCRIPTION_LANGUAGES]?.let(::parseLanguageCodes)
                    ?: LiveSettings.DEFAULT_TRANSCRIPTION_LANGUAGES,
                systemInstruction = prefs[SYSTEM_INSTRUCTION].orEmpty(),
            )
        }

    suspend fun save(settings: LiveSettings) {
        dataStore.edit { prefs ->
            prefs[API_KEY] = settings.apiKey.trim()
            prefs[MODEL] = settings.model.trim()
            prefs[VOICE] = settings.voice.trim()
            prefs[THINKING_LEVEL] = settings.thinkingLevel.trim()
            prefs[TRANSCRIPTION_LANGUAGES] = settings.transcriptionLanguages.joinToString(", ")
            prefs[SYSTEM_INSTRUCTION] = settings.systemInstruction.trim()
        }
    }

    /**
     * The codes are stored as one comma-separated string. Earlier versions stored what was typed
     * into a text field, where a full-width comma or a space separated them as well.
     */
    private fun parseLanguageCodes(text: String): List<String> =
        text.split(',', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val VOICE = stringPreferencesKey("voice")
        val THINKING_LEVEL = stringPreferencesKey("thinking_level")
        val TRANSCRIPTION_LANGUAGES = stringPreferencesKey("transcription_languages")
        val SYSTEM_INSTRUCTION = stringPreferencesKey("system_instruction")
    }
}
