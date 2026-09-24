package io.github.clyu.geminilive.data

import androidx.annotation.StringRes
import io.github.clyu.geminilive.R

data class LiveSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val voice: String = DEFAULT_VOICE,
    /** Comma-separated BCP-47 codes hinting the caption language; blank means automatic detection. */
    val transcriptionLanguages: String = DEFAULT_TRANSCRIPTION_LANGUAGES,
    val systemInstruction: String = "",
) {
    val transcriptionLanguageCodes: List<String>
        get() = parseLanguageCodes(transcriptionLanguages)

    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-live"
        const val DEFAULT_VOICE = "Zephyr"
        const val DEFAULT_TRANSCRIPTION_LANGUAGES = "zh-Hant, en"

        /** Splits a comma- or space-separated list of language codes. */
        fun parseLanguageCodes(text: String): List<String> =
            text.split(',', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Live API models offered as suggestions; any other model id can still be typed in. */
val LIVE_MODELS = listOf(
    "gemini-3.8-live",
    "gemini-3.8-live-extended-thinking",
    "gemini-3.1-flash-live-preview",
    "gemini-2.5-flash-native-audio-preview-12-2025",
)

data class TranscriptionLanguage(val code: String, @StringRes val label: Int)

/**
 * Caption languages offered in the check list. Without a hint, Mandarin is transcribed in
 * Simplified Chinese because speech carries no information about the script.
 */
val TRANSCRIPTION_LANGUAGES = listOf(
    TranscriptionLanguage("zh-Hant", R.string.language_zh_hant),
    TranscriptionLanguage("zh-Hans", R.string.language_zh_hans),
    TranscriptionLanguage("en", R.string.language_en),
    TranscriptionLanguage("ja", R.string.language_ja),
    TranscriptionLanguage("ko", R.string.language_ko),
)

data class PrebuiltVoice(val name: String, @StringRes val style: Int)

/** The prebuilt voices listed in the Gemini speech generation docs. */
val PREBUILT_VOICES = listOf(
    PrebuiltVoice("Zephyr", R.string.voice_style_bright),
    PrebuiltVoice("Puck", R.string.voice_style_upbeat),
    PrebuiltVoice("Charon", R.string.voice_style_informative),
    PrebuiltVoice("Kore", R.string.voice_style_firm),
    PrebuiltVoice("Fenrir", R.string.voice_style_excitable),
    PrebuiltVoice("Leda", R.string.voice_style_youthful),
    PrebuiltVoice("Orus", R.string.voice_style_firm),
    PrebuiltVoice("Aoede", R.string.voice_style_breezy),
    PrebuiltVoice("Callirrhoe", R.string.voice_style_easy_going),
    PrebuiltVoice("Autonoe", R.string.voice_style_bright),
    PrebuiltVoice("Enceladus", R.string.voice_style_breathy),
    PrebuiltVoice("Iapetus", R.string.voice_style_clear),
    PrebuiltVoice("Umbriel", R.string.voice_style_easy_going),
    PrebuiltVoice("Algieba", R.string.voice_style_smooth),
    PrebuiltVoice("Despina", R.string.voice_style_smooth),
    PrebuiltVoice("Erinome", R.string.voice_style_clear),
    PrebuiltVoice("Algenib", R.string.voice_style_gravelly),
    PrebuiltVoice("Rasalgethi", R.string.voice_style_informative),
    PrebuiltVoice("Laomedeia", R.string.voice_style_upbeat),
    PrebuiltVoice("Achernar", R.string.voice_style_soft),
    PrebuiltVoice("Alnilam", R.string.voice_style_firm),
    PrebuiltVoice("Schedar", R.string.voice_style_even),
    PrebuiltVoice("Gacrux", R.string.voice_style_mature),
    PrebuiltVoice("Pulcherrima", R.string.voice_style_forward),
    PrebuiltVoice("Achird", R.string.voice_style_friendly),
    PrebuiltVoice("Zubenelgenubi", R.string.voice_style_casual),
    PrebuiltVoice("Vindemiatrix", R.string.voice_style_gentle),
    PrebuiltVoice("Sadachbia", R.string.voice_style_lively),
    PrebuiltVoice("Sadaltager", R.string.voice_style_knowledgeable),
    PrebuiltVoice("Sulafat", R.string.voice_style_warm),
)
