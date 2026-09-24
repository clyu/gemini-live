package io.github.clyu.geminilive.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.clyu.geminilive.R
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenSettings: () -> Unit, viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.start()
        } else {
            viewModel.showMessage(context.getString(R.string.error_permission_denied))
        }
    }
    val onStart = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
            viewModel.consumeMessage()
        }
    }

    val view = LocalView.current
    val active = state.status != SessionStatus.Idle
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = viewModel::clearTranscript, enabled = state.transcript.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_clear))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings))
                    }
                },
            )
        },
        bottomBar = {
            ControlBar(
                state = state,
                onStart = onStart,
                onStop = viewModel::stop,
                onToggleMute = viewModel::toggleMute,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        TranscriptList(
            entries = state.transcript,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun TranscriptList(entries: List<TranscriptEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.transcript_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // The list is reversed so the newest caption stays pinned to the bottom while it grows.
    val listState = rememberLazyListState()

    // Follow the newest caption unless the user has scrolled up to read earlier ones.
    var followLatest by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect { followLatest = listState.firstVisibleItemIndex == 0 }
    }

    // The list keeps its position anchored to an item's key, so a newly added caption would land
    // just out of view below the previous one. scrollToItem drops that anchor, and unlike
    // animateScrollToItem it works even when this runs before the list has laid out the new item.
    val newest = entries.last()
    LaunchedEffect(newest.id, newest.text.length) {
        if (followLatest && !listState.isScrollInProgress) listState.scrollToItem(0)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        items(entries.asReversed(), key = { it.id }) { entry ->
            TranscriptBubble(entry)
        }
    }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val isUser = entry.role == Role.User
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isUser) 48.dp else 0.dp, end = if (isUser) 0.dp else 48.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = stringResource(if (isUser) R.string.speaker_user else R.string.speaker_model),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = entry.text.trim(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (entry.interrupted) {
            Text(
                text = stringResource(R.string.label_interrupted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ControlBar(
    state: ChatUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleMute: () -> Unit,
) {
    val active = state.status != SessionStatus.Idle
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onToggleMute,
                    enabled = active,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(if (state.micMuted) R.drawable.ic_mic_off else R.drawable.ic_mic),
                        contentDescription = stringResource(if (state.micMuted) R.string.action_unmute else R.string.action_mute),
                    )
                }
                if (active) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_call_end),
                            contentDescription = stringResource(R.string.action_stop),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else {
                    FilledIconButton(onClick = onStart, modifier = Modifier.size(72.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_call),
                            contentDescription = stringResource(R.string.action_start),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                // Balances the mute button so the call button stays centred.
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}

@Composable
private fun statusText(state: ChatUiState): String = stringResource(
    when (state.status) {
        SessionStatus.Idle -> R.string.status_idle
        SessionStatus.Connecting -> R.string.status_connecting
        SessionStatus.Reconnecting -> R.string.status_reconnecting
        SessionStatus.Connected -> when {
            state.modelSpeaking -> R.string.status_model_speaking
            state.micMuted -> R.string.status_muted
            else -> R.string.status_listening
        }
    },
)
