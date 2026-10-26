package io.github.clyu.geminilive.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.clyu.geminilive.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore

    val settings: Flow<LiveSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val storedPrompts = prefs[SYSTEM_PROMPTS]
            // Earlier versions stored a single system instruction, which is read as the only saved
            // prompt, named like a newly added one.
            val legacyPrompt = prefs[SYSTEM_INSTRUCTION]?.takeIf { it.isNotBlank() }?.let {
                SystemPrompt(id = 1, name = appContext.getString(R.string.system_prompt_default_name, 1), text = it)
            }
            LiveSettings(
                apiKey = prefs[API_KEY].orEmpty(),
                model = prefs[MODEL]?.takeIf { it.isNotBlank() } ?: LiveSettings.DEFAULT_MODEL,
                voice = prefs[VOICE]?.takeIf { it.isNotBlank() } ?: LiveSettings.DEFAULT_VOICE,
                thinkingLevel = prefs[THINKING_LEVEL].orEmpty(),
                transcriptionLanguages = prefs[TRANSCRIPTION_LANGUAGES]?.let(::parseLanguageCodes)
                    ?: LiveSettings.DEFAULT_TRANSCRIPTION_LANGUAGES,
                systemPrompts = storedPrompts?.let(::parseSystemPrompts) ?: listOfNotNull(legacyPrompt),
                activeSystemPromptId = if (storedPrompts != null) prefs[ACTIVE_SYSTEM_PROMPT] else legacyPrompt?.id,
            )
        }

    suspend fun save(settings: LiveSettings) {
        val prompts = settings.systemPrompts.map { it.copy(name = it.name.trim(), text = it.text.trim()) }
        val activeId = settings.activeSystemPromptId?.takeIf { id -> prompts.any { it.id == id } }
        dataStore.edit { prefs ->
            prefs[API_KEY] = settings.apiKey.trim()
            prefs[MODEL] = settings.model.trim()
            prefs[VOICE] = settings.voice.trim()
            prefs[THINKING_LEVEL] = settings.thinkingLevel.trim()
            prefs[TRANSCRIPTION_LANGUAGES] = settings.transcriptionLanguages.joinToString(", ")
            prefs[SYSTEM_PROMPTS] = formatSystemPrompts(prompts)
            if (activeId != null) prefs[ACTIVE_SYSTEM_PROMPT] = activeId else prefs.remove(ACTIVE_SYSTEM_PROMPT)
            // The single instruction of earlier versions has been carried over into the saved prompts.
            prefs.remove(SYSTEM_INSTRUCTION)
        }
    }

    /**
     * The codes are stored as one comma-separated string. Earlier versions stored what was typed
     * into a text field, where a full-width comma or a space separated them as well.
     */
    private fun parseLanguageCodes(text: String): List<String> =
        text.split(',', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * The prompts are stored as a JSON array. An unreadable one is read as no prompts, which the next
     * save overwrites.
     */
    private fun parseSystemPrompts(json: String): List<SystemPrompt> = try {
        val array = JSONArray(json)
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            SystemPrompt(id = item.getLong("id"), name = item.getString("name"), text = item.getString("text"))
        }
    } catch (e: JSONException) {
        Log.w(TAG, "Discarding unreadable system prompts", e)
        emptyList()
    }

    private fun formatSystemPrompts(prompts: List<SystemPrompt>): String {
        val array = JSONArray()
        for (prompt in prompts) {
            array.put(JSONObject().put("id", prompt.id).put("name", prompt.name).put("text", prompt.text))
        }
        return array.toString()
    }

    private companion object {
        const val TAG = "SettingsRepository"
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val VOICE = stringPreferencesKey("voice")
        val THINKING_LEVEL = stringPreferencesKey("thinking_level")
        val TRANSCRIPTION_LANGUAGES = stringPreferencesKey("transcription_languages")
        val SYSTEM_PROMPTS = stringPreferencesKey("system_prompts")
        val ACTIVE_SYSTEM_PROMPT = longPreferencesKey("active_system_prompt")
        /** Only read, to carry over the single system instruction stored by earlier versions. */
        val SYSTEM_INSTRUCTION = stringPreferencesKey("system_instruction")
    }
}
