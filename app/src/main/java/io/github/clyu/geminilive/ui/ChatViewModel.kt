package io.github.clyu.geminilive.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.clyu.geminilive.R
import io.github.clyu.geminilive.audio.AudioPlayer
import io.github.clyu.geminilive.audio.AudioRecorder
import io.github.clyu.geminilive.audio.AudioRouting
import io.github.clyu.geminilive.data.LiveSettings
import io.github.clyu.geminilive.data.Role
import io.github.clyu.geminilive.data.SettingsRepository
import io.github.clyu.geminilive.data.TranscriptEntry
import io.github.clyu.geminilive.data.TranscriptRepository
import io.github.clyu.geminilive.live.LiveEvent
import io.github.clyu.geminilive.live.LiveSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class SessionStatus { Idle, Connecting, Connected, Reconnecting }

data class ChatUiState(
    val status: SessionStatus = SessionStatus.Idle,
    val micMuted: Boolean = false,
    val modelSpeaking: Boolean = false,
    val transcript: List<TranscriptEntry> = emptyList(),
    /** IDs of the captions selected for deletion; only ever non-empty between conversations. */
    val selection: Set<Long> = emptySet(),
    val message: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val transcriptRepository = TranscriptRepository(application)
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val routing = AudioRouting(application)
    private val player = AudioPlayer()
    private val recorder = AudioRecorder { pcm -> session?.sendAudio(pcm) }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // Session events tagged with the generation of the connection that produced them; handled on
    // the main thread so events from a replaced or closed connection can be dropped.
    private val events = Channel<Pair<Int, LiveEvent>>(Channel.UNLIMITED)

    @Volatile private var session: LiveSession? = null
    @Volatile private var generation = 0
    private var settings: LiveSettings? = null
    private var resumeHandle: String? = null
    private var reconnectAttempts = 0
    private var startJob: Job? = null
    private var reconnectJob: Job? = null

    private var nextEntryId = 0L
    private var openUserEntryId: Long? = null
    private var openModelEntryId: Long? = null

    /** Restores the transcript saved before the app was last closed. */
    private val restoreJob: Job
    private var savedTranscript = emptyList<TranscriptEntry>()
    // Written one at a time so that an older transcript never overwrites a newer one.
    private val pendingSaves = Channel<List<TranscriptEntry>>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for ((gen, event) in events) {
                if (gen == generation) handleEvent(event)
            }
        }
        viewModelScope.launch {
            player.speaking.collect { speaking -> _state.update { it.copy(modelSpeaking = speaking) } }
        }
        restoreJob = viewModelScope.launch {
            val saved = transcriptRepository.load()
            nextEntryId = (saved.maxOfOrNull { it.id } ?: -1L) + 1
            savedTranscript = saved
            _state.update { it.copy(transcript = saved) }
        }
        viewModelScope.launch {
            for (transcript in pendingSaves) transcriptRepository.save(transcript)
        }
    }

    /** Starts a voice session. The caller must already hold the RECORD_AUDIO permission. */
    fun start() {
        if (_state.value.status != SessionStatus.Idle) return
        _state.update { it.copy(status = SessionStatus.Connecting, micMuted = false, selection = emptySet()) }
        startJob = viewModelScope.launch {
            // New captions must not arrive before the saved transcript has been restored.
            restoreJob.join()
            val current = settingsRepository.settings.first()
            if (current.apiKey.isBlank()) {
                _state.update { it.copy(status = SessionStatus.Idle, message = getString(R.string.error_missing_api_key)) }
                return@launch
            }
            settings = current
            resumeHandle = null
            reconnectAttempts = 0
            try {
                routing.start()
                player.start()
            } catch (e: Exception) {
                endSession(getString(R.string.error_audio, e.message ?: e.javaClass.simpleName))
                return@launch
            }
            openSession()
        }
    }

    fun stop() = endSession(null)

    fun toggleMute() {
        val muted = !_state.value.micMuted
        _state.update { it.copy(micMuted = muted) }
        if (muted) {
            recorder.stop()
            session?.sendAudioStreamEnd()
        } else if (_state.value.status == SessionStatus.Connected) {
            startRecorder()
        }
    }

    fun clearTranscript() {
        closeTurn()
        _state.update { it.copy(transcript = emptyList(), selection = emptySet()) }
    }

    /**
     * Selects or deselects a caption for deletion. Only allowed between conversations, when no
     * caption is still growing.
     */
    fun toggleSelection(id: Long) {
        if (_state.value.status != SessionStatus.Idle) return
        _state.update { it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id) }
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun deleteSelection() = _state.update { state ->
        state.copy(transcript = state.transcript.filterNot { it.id in state.selection }, selection = emptySet())
    }

    /**
     * Writes the transcript to storage if it has changed. To keep writes down, captions are only
     * held in memory while they arrive; this runs when a conversation ends and when the app leaves
     * the foreground, the last chance before the process can be killed without warning. Captions
     * that arrive while the app is in the background are therefore not written until the next of
     * these, by design.
     */
    fun saveTranscript() {
        val transcript = _state.value.transcript
        if (transcript == savedTranscript) return
        savedTranscript = transcript
        pendingSaves.trySend(transcript)
    }

    fun showMessage(message: String) = _state.update { it.copy(message = message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun openSession() {
        val current = settings ?: return
        val gen = ++generation
        session = LiveSession(httpClient, current, resumeHandle) { event -> onSessionEvent(gen, event) }
            .also { it.connect() }
    }

    /** Called on OkHttp's thread. Audio bypasses the main thread to keep playback latency low. */
    private fun onSessionEvent(gen: Int, event: LiveEvent) {
        if (gen != generation) return
        when (event) {
            is LiveEvent.Audio -> player.enqueue(event.pcm)
            else -> {
                if (event is LiveEvent.Interrupted) player.interrupt()
                events.trySend(gen to event)
            }
        }
    }

    private fun handleEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.SetupComplete -> {
                reconnectAttempts = 0
                _state.update { it.copy(status = SessionStatus.Connected) }
                if (!_state.value.micMuted) startRecorder()
            }
            is LiveEvent.InputTranscription -> appendTranscript(Role.User, event.text)
            is LiveEvent.OutputTranscription -> appendTranscript(Role.Model, event.text)
            is LiveEvent.Interrupted -> {
                markModelEntryInterrupted()
                closeTurn()
            }
            is LiveEvent.TurnComplete -> closeTurn()
            is LiveEvent.ResumptionHandle -> resumeHandle = event.handle
            // The server is about to drop this connection; move to a fresh one right away.
            is LiveEvent.GoAway -> if (resumeHandle != null) reconnect(delayMs = 0)
            is LiveEvent.Closed -> onSessionClosed(event)
            is LiveEvent.Audio -> Unit
        }
    }

    private fun onSessionClosed(event: LiveEvent.Closed) {
        if (resumeHandle != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            reconnect(delayMs = RECONNECT_BACKOFF_MS * reconnectAttempts)
            return
        }
        val detail = event.reason?.takeIf { it.isNotBlank() }
            ?: event.error?.let { it.message ?: it.javaClass.simpleName }
            ?: "code ${event.code}"
        endSession(getString(R.string.error_connection_closed, detail))
    }

    /** Resumes the conversation on a new connection; the microphone and speaker keep running. */
    private fun reconnect(delayMs: Long) {
        session?.close()
        session = null
        generation++
        closeTurn()
        _state.update { it.copy(status = SessionStatus.Reconnecting) }
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            openSession()
        }
    }

    private fun endSession(message: String?) {
        generation++
        startJob?.cancel()
        startJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        session?.close()
        session = null
        recorder.stop()
        player.stop()
        routing.stop()
        closeTurn()
        settings = null
        resumeHandle = null
        _state.update { it.copy(status = SessionStatus.Idle, micMuted = false, message = message ?: it.message) }
        saveTranscript()
    }

    private fun startRecorder() {
        try {
            recorder.start()
        } catch (e: Exception) {
            endSession(getString(R.string.error_audio, e.message ?: e.javaClass.simpleName))
        }
    }

    /**
     * Transcriptions arrive as incremental fragments. Fragments are appended to the open entry of
     * the same speaker until the model's turn completes or is interrupted.
     */
    private fun appendTranscript(role: Role, text: String) {
        val openId = if (role == Role.User) openUserEntryId else openModelEntryId
        if (openId != null) {
            _state.update { state ->
                state.copy(transcript = state.transcript.map { if (it.id == openId) it.copy(text = it.text + text) else it })
            }
            return
        }
        val entry = TranscriptEntry(id = nextEntryId++, role = role, text = text)
        val answeringId = openModelEntryId
        if (role == Role.User) openUserEntryId = entry.id else openModelEntryId = entry.id
        _state.update { state ->
            val transcript = state.transcript.toMutableList()
            // Input transcription has no guaranteed ordering and can arrive after the model has
            // started answering; keep the user's words above that answer.
            val index = if (role == Role.User && answeringId != null) {
                transcript.indexOfFirst { it.id == answeringId }
            } else {
                -1
            }
            if (index >= 0) transcript.add(index, entry) else transcript.add(entry)
            state.copy(transcript = transcript)
        }
    }

    private fun markModelEntryInterrupted() {
        val id = openModelEntryId ?: return
        _state.update { state ->
            state.copy(transcript = state.transcript.map { if (it.id == id) it.copy(interrupted = true) else it })
        }
    }

    private fun closeTurn() {
        openUserEntryId = null
        openModelEntryId = null
    }

    private fun getString(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    override fun onCleared() {
        endSession(null)
        events.close()
        httpClient.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BACKOFF_MS = 1_000L
    }
}
