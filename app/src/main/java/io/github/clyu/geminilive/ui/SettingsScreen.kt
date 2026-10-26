package io.github.clyu.geminilive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.clyu.geminilive.R
import io.github.clyu.geminilive.data.LIVE_MODELS
import io.github.clyu.geminilive.data.LiveSettings
import io.github.clyu.geminilive.data.PREBUILT_VOICES
import io.github.clyu.geminilive.data.SystemPrompt
import io.github.clyu.geminilive.data.THINKING_LEVELS
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
    // Only listed levels can be selected, so any other stored value falls back to automatic.
    var thinkingLevel by rememberSaveable {
        mutableStateOf(THINKING_LEVELS.find { it.value.equals(initial.thinkingLevel, ignoreCase = true) }?.value.orEmpty())
    }
    // Only prebuilt voices can be selected, so any other stored name falls back to the default.
    var voice by rememberSaveable {
        mutableStateOf(
            PREBUILT_VOICES.find { it.name.equals(initial.voice, ignoreCase = true) }?.name ?: LiveSettings.DEFAULT_VOICE,
        )
    }
    // Codes missing from the check list could be neither shown nor unchecked, so they are dropped.
    var transcriptionLanguages by rememberSaveable {
        mutableStateOf(
            TRANSCRIPTION_LANGUAGES.map { it.code }
                .filter { code -> initial.transcriptionLanguages.any { it.equals(code, ignoreCase = true) } },
        )
    }
    var systemPrompts by rememberSaveable(stateSaver = SystemPromptsSaver) { mutableStateOf(initial.systemPrompts) }
    var activeSystemPromptId by rememberSaveable { mutableStateOf(initial.activeSystemPromptId) }
    // The choice made before the active prompt, which deleting that prompt returns to.
    var previousSystemPromptId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Prompts added on this visit and not edited since, which leaving the screen drops as if deleted.
    var untouchedSystemPromptIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var showApiKey by rememberSaveable { mutableStateOf(false) }

    // Settings are saved whenever the user leaves the screen.
    val done = {
        val dropsActivePrompt = activeSystemPromptId in untouchedSystemPromptIds
        onDone(
            LiveSettings(
                apiKey = apiKey,
                model = model,
                thinkingLevel = thinkingLevel,
                voice = voice,
                transcriptionLanguages = transcriptionLanguages,
                systemPrompts = systemPrompts.filterNot { it.id in untouchedSystemPromptIds },
                activeSystemPromptId = if (dropsActivePrompt) previousSystemPromptId else activeSystemPromptId,
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
            ThinkingLevelSelectField(
                level = thinkingLevel,
                onLevelChange = { thinkingLevel = it },
                label = stringResource(R.string.settings_thinking_level),
                supportingText = stringResource(R.string.settings_thinking_level_hint),
            )
            VoiceSelectField(
                voice = voice,
                onVoiceChange = { voice = it },
                label = stringResource(R.string.settings_voice),
                supportingText = stringResource(R.string.settings_default_value, LiveSettings.DEFAULT_VOICE),
            )
            LanguageSelectField(
                selected = transcriptionLanguages,
                onSelectedChange = { transcriptionLanguages = it },
                label = stringResource(R.string.settings_transcription_language),
                supportingText = stringResource(
                    R.string.settings_transcription_language_hint,
                    languageNames(LiveSettings.DEFAULT_TRANSCRIPTION_LANGUAGES),
                ),
            )
            val activePrompt = systemPrompts.find { it.id == activeSystemPromptId }
            val selectPrompt = { id: Long? ->
                if (id != activeSystemPromptId) {
                    // An untouched new prompt is dropped on leaving the screen, so it is not one to return to.
                    if (activeSystemPromptId !in untouchedSystemPromptIds) {
                        previousSystemPromptId = activeSystemPromptId
                    }
                    activeSystemPromptId = id
                }
            }
            val replacePrompt = { edited: SystemPrompt ->
                systemPrompts = systemPrompts.map { if (it.id == edited.id) edited else it }
                untouchedSystemPromptIds -= edited.id
            }
            // A new prompt is named after its id, which no other prompt has, so that the names do not repeat.
            val nextPromptId = (systemPrompts.maxOfOrNull { it.id } ?: 0L) + 1
            val newPromptName = stringResource(R.string.system_prompt_default_name, nextPromptId)
            SystemPromptSelectField(
                prompts = systemPrompts,
                selected = activePrompt,
                onSelect = { selectPrompt(it?.id) },
                onRename = { name -> activePrompt?.let { replacePrompt(it.copy(name = name)) } },
                onAdd = {
                    systemPrompts = systemPrompts + SystemPrompt(id = nextPromptId, name = newPromptName, text = "")
                    untouchedSystemPromptIds += nextPromptId
                    selectPrompt(nextPromptId)
                },
                onDelete = {
                    systemPrompts = systemPrompts.filterNot { it.id == activeSystemPromptId }
                    // Back to the choice made before, unless that is gone as well.
                    activeSystemPromptId = previousSystemPromptId?.takeIf { id -> systemPrompts.any { it.id == id } }
                    previousSystemPromptId = null
                },
                label = stringResource(R.string.settings_system_instruction),
                supportingText = stringResource(R.string.settings_system_prompt_hint),
            )
            if (activePrompt != null) {
                // Keyed by prompt so that switching to another one starts the text field afresh.
                key(activePrompt.id) {
                    OutlinedTextField(
                        value = activePrompt.text,
                        onValueChange = { replacePrompt(activePrompt.copy(text = it)) },
                        label = { Text(stringResource(R.string.settings_system_prompt_text)) },
                        supportingText = { Text(stringResource(R.string.settings_system_instruction_hint)) },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
    SelectField(value, label, supportingText, onValueChange) { close ->
        suggestions.forEach { suggestion ->
            DropdownMenuItem(
                text = { Text(suggestion) },
                onClick = {
                    onValueChange(suggestion)
                    close()
                },
            )
        }
    }
}

/** A read-only field showing the selected thinking level, picked from a drop-down of [THINKING_LEVELS]. */
@Composable
private fun ThinkingLevelSelectField(
    level: String,
    onLevelChange: (String) -> Unit,
    label: String,
    supportingText: String,
) {
    val selected = THINKING_LEVELS.first { it.value == level }
    SelectField(stringResource(selected.label), label, supportingText) { close ->
        THINKING_LEVELS.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.label)) },
                onClick = {
                    onLevelChange(option.value)
                    close()
                },
            )
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
 * A read-only field showing the names of the languages whose codes are [selected], which are picked
 * from a check list of [TRANSCRIPTION_LANGUAGES] and kept in its order.
 */
@Composable
private fun LanguageSelectField(
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    label: String,
    supportingText: String,
) {
    SelectField(languageNames(selected), label, supportingText) { _ ->
        TRANSCRIPTION_LANGUAGES.forEach { language ->
            val checked = language.code in selected
            DropdownMenuItem(
                text = { Text(stringResource(language.label)) },
                // The menu stays open so that several languages can be checked in a row.
                onClick = {
                    onSelectedChange(
                        TRANSCRIPTION_LANGUAGES.map { it.code }
                            .filter { if (it == language.code) !checked else it in selected },
                    )
                },
                leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
            )
        }
    }
}

/**
 * A field showing the name of the [selected] system prompt, which can be typed in to rename it, or
 * the choice of none, which is read-only. Its drop-down picks one of [prompts] or none, adds a new
 * prompt, and deletes the selected one after asking for confirmation.
 */
@Composable
private fun SystemPromptSelectField(
    prompts: List<SystemPrompt>,
    selected: SystemPrompt?,
    onSelect: (SystemPrompt?) -> Unit,
    onRename: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    label: String,
    supportingText: String,
) {
    val none = stringResource(R.string.system_prompt_none)
    val focusManager = LocalFocusManager.current
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    SelectField(
        value = selected?.name ?: none,
        label = label,
        supportingText = supportingText,
        onValueChange = if (selected != null) onRename else null,
    ) { close ->
        // Picking from the menu also ends any renaming, so that the keyboard does not stay up over a
        // field that may have turned read-only.
        val closeMenu = {
            close()
            focusManager.clearFocus()
        }
        DropdownMenuItem(
            text = { Text(none) },
            onClick = {
                onSelect(null)
                closeMenu()
            },
        )
        prompts.forEach { prompt ->
            DropdownMenuItem(
                text = { Text(promptName(prompt), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onClick = {
                    onSelect(prompt)
                    closeMenu()
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_add_system_prompt)) },
            onClick = {
                onAdd()
                closeMenu()
            },
            leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        )
        if (selected != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_delete_system_prompt, promptName(selected)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onClick = {
                    confirmingDelete = true
                    closeMenu()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            )
        }
    }
    if (confirmingDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_system_prompt_title, promptName(selected))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * A field that opens a drop-down menu when tapped; [menuContent] receives a callback closing it.
 * It is read-only unless [onValueChange] is given, which lets the text be typed as well.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    value: String,
    label: String,
    supportingText: String,
    onValueChange: ((String) -> Unit)? = null,
    menuContent: @Composable (close: () -> Unit) -> Unit,
) {
    val editable = onValueChange != null
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange ?: {},
            readOnly = !editable,
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    // Tapping an editable field also places the cursor, so its icon toggles the menu too.
                    modifier = if (editable) {
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
                    } else {
                        Modifier
                    },
                )
            },
            modifier = Modifier
                .menuAnchor(
                    if (editable) {
                        ExposedDropdownMenuAnchorType.PrimaryEditable
                    } else {
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable
                    },
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}

/** The names of the languages of [TRANSCRIPTION_LANGUAGES] whose codes are in [codes], in check-list order. */
@Composable
private fun languageNames(codes: List<String>): String =
    TRANSCRIPTION_LANGUAGES.filter { it.code in codes }
        .map { stringResource(it.label) }
        .joinToString(stringResource(R.string.list_separator))

/** The name of [prompt], or failing that the first non-blank line of its text. */
@Composable
private fun promptName(prompt: SystemPrompt): String =
    (sequenceOf(prompt.name) + prompt.text.lineSequence())
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: stringResource(R.string.system_prompt_untitled)

/**
 * Saves the prompts as a flat list of their ids, names and texts. Unlike with listSaver, an empty
 * list is saved as such rather than as nothing, which would be restored as a null state value.
 */
private val SystemPromptsSaver = Saver<List<SystemPrompt>, ArrayList<Any>>(
    save = { prompts -> prompts.flatMapTo(ArrayList()) { listOf(it.id, it.name, it.text) } },
    restore = { fields ->
        fields.chunked(3) { (id, name, text) -> SystemPrompt(id as Long, name as String, text as String) }
    },
)
