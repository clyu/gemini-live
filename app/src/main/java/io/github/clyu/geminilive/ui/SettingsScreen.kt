package io.github.clyu.geminilive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.clyu.geminilive.R
import io.github.clyu.geminilive.data.LIVE_MODELS
import io.github.clyu.geminilive.data.LiveSettings
import io.github.clyu.geminilive.data.PREBUILT_VOICES
import io.github.clyu.geminilive.data.TRANSCRIPTION_LANGUAGES
import io.github.clyu.geminilive.data.TranscriptionLanguage

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val initial = loaded
    if (initial == null) {
        BackHandler(onBack = onBack)
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    SettingsForm(
        initial = initial,
        onDone = { edited ->
            viewModel.save(edited)
            onBack()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsForm(initial: LiveSettings, onDone: (LiveSettings) -> Unit) {
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    // Only prebuilt voices can be selected, so any other stored name falls back to the default.
    var voice by rememberSaveable {
        mutableStateOf(
            PREBUILT_VOICES.find { it.name.equals(initial.voice, ignoreCase = true) }?.name ?: LiveSettings.DEFAULT_VOICE,
        )
    }
    // Codes missing from the check list could be neither shown nor unchecked, so they are dropped.
    var transcriptionLanguages by rememberSaveable {
        mutableStateOf(selectedLanguages(initial.transcriptionLanguages).toCodes())
    }
    var systemInstruction by rememberSaveable { mutableStateOf(initial.systemInstruction) }
    var showApiKey by rememberSaveable { mutableStateOf(false) }

    // Settings are saved whenever the user leaves the screen.
    val done = {
        onDone(
            LiveSettings(
                apiKey = apiKey,
                model = model,
                voice = voice,
                transcriptionLanguages = transcriptionLanguages,
                systemInstruction = systemInstruction,
            ),
        )
    }
    BackHandler(onBack = done)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = done) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.settings_api_key)) },
                supportingText = { Text(stringResource(R.string.settings_api_key_hint)) },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            painter = painterResource(if (showApiKey) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = stringResource(if (showApiKey) R.string.action_hide_key else R.string.action_show_key),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SuggestionTextField(
                value = model,
                onValueChange = { model = it },
                label = stringResource(R.string.settings_model),
                supportingText = stringResource(R.string.settings_default_value, LiveSettings.DEFAULT_MODEL),
                suggestions = LIVE_MODELS,
            )
            VoiceSelectField(
                voice = voice,
                onVoiceChange = { voice = it },
                label = stringResource(R.string.settings_voice),
                supportingText = stringResource(R.string.settings_default_value, LiveSettings.DEFAULT_VOICE),
            )
            LanguageSelectField(
                selected = selectedLanguages(transcriptionLanguages),
                onSelectedChange = { transcriptionLanguages = it.toCodes() },
                label = stringResource(R.string.settings_transcription_language),
                supportingText = stringResource(
                    R.string.settings_transcription_language_hint,
                    languageNames(selectedLanguages(LiveSettings.DEFAULT_TRANSCRIPTION_LANGUAGES)),
                ),
            )
            OutlinedTextField(
                value = systemInstruction,
                onValueChange = { systemInstruction = it },
                label = { Text(stringResource(R.string.settings_system_instruction)) },
                supportingText = { Text(stringResource(R.string.settings_system_instruction_hint)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A free-text field with a drop-down of suggested values. */
@Composable
private fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    suggestions: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(painterResource(R.drawable.ic_arrow_drop_down), stringResource(R.string.action_show_suggestions))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A read-only field showing the selected voice, picked from a drop-down of [PREBUILT_VOICES]. */
@Composable
private fun VoiceSelectField(
    voice: String,
    onVoiceChange: (String) -> Unit,
    label: String,
    supportingText: String,
) {
    SelectField(voice, label, supportingText) { close ->
        PREBUILT_VOICES.forEach { prebuilt ->
            DropdownMenuItem(
                text = {
                    val styleColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val style = stringResource(prebuilt.style)
                    Text(
                        buildAnnotatedString {
                            append(prebuilt.name)
                            withStyle(SpanStyle(color = styleColor)) { append(" ($style)") }
                        },
                    )
                },
                onClick = {
                    onVoiceChange(prebuilt.name)
                    close()
                },
            )
        }
    }
}

/**
 * A read-only field showing the names of the selected languages, which are picked from a check list
 * of [TRANSCRIPTION_LANGUAGES].
 */
@Composable
private fun LanguageSelectField(
    selected: List<TranscriptionLanguage>,
    onSelectedChange: (List<TranscriptionLanguage>) -> Unit,
    label: String,
    supportingText: String,
) {
    SelectField(languageNames(selected), label, supportingText) { _ ->
        TRANSCRIPTION_LANGUAGES.forEach { language ->
            val checked = language in selected
            DropdownMenuItem(
                text = { Text(stringResource(language.label)) },
                // The menu stays open so that several languages can be checked in a row.
                onClick = {
                    onSelectedChange(TRANSCRIPTION_LANGUAGES.filter { if (it == language) !checked else it in selected })
                },
                leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
            )
        }
    }
}

/** A read-only field that opens a drop-down menu when tapped; [menuContent] receives a callback closing it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    value: String,
    label: String,
    supportingText: String,
    menuContent: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}

@Composable
private fun languageNames(languages: List<TranscriptionLanguage>): String =
    languages.map { stringResource(it.label) }.joinToString(stringResource(R.string.list_separator))

/** The languages of [TRANSCRIPTION_LANGUAGES] whose codes appear in [codes], in check-list order. */
private fun selectedLanguages(codes: String): List<TranscriptionLanguage> {
    val parsed = LiveSettings.parseLanguageCodes(codes)
    return TRANSCRIPTION_LANGUAGES.filter { language -> parsed.any { it.equals(language.code, ignoreCase = true) } }
}

private fun List<TranscriptionLanguage>.toCodes(): String = joinToString(", ") { it.code }
