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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
    var voice by rememberSaveable { mutableStateOf(initial.voice) }
    var transcriptionLanguages by rememberSaveable { mutableStateOf(initial.transcriptionLanguages) }
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
                suggestions = LIVE_MODELS.map { Suggestion(it) },
            )
            SuggestionTextField(
                value = voice,
                onValueChange = { voice = it },
                label = stringResource(R.string.settings_voice),
                supportingText = stringResource(R.string.settings_default_value, LiveSettings.DEFAULT_VOICE),
                suggestions = PREBUILT_VOICES.map { Suggestion(it.name, stringResource(it.style)) },
                inlineDescription = true,
            )
            SuggestionTextField(
                value = transcriptionLanguages,
                onValueChange = { transcriptionLanguages = it },
                label = stringResource(R.string.settings_transcription_language),
                supportingText = stringResource(
                    R.string.settings_transcription_language_hint,
                    LiveSettings.DEFAULT_TRANSCRIPTION_LANGUAGES,
                ),
                suggestions = TRANSCRIPTION_LANGUAGES.map { Suggestion(it.codes, stringResource(it.label)) },
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

private data class Suggestion(val value: String, val description: String? = null)

/**
 * A free-text field with a drop-down of suggested values. Descriptions are shown below each value,
 * or in parentheses after it when [inlineDescription] is set.
 */
@Composable
private fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    suggestions: List<Suggestion>,
    inlineDescription: Boolean = false,
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
                    text = {
                        if (inlineDescription) {
                            val descriptionColor = MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                buildAnnotatedString {
                                    append(suggestion.value)
                                    suggestion.description?.let {
                                        withStyle(SpanStyle(color = descriptionColor)) { append(" ($it)") }
                                    }
                                },
                            )
                        } else {
                            Column {
                                Text(suggestion.value)
                                suggestion.description?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        onValueChange(suggestion.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
