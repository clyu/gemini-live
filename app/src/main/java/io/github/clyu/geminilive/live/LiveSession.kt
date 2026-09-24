package io.github.clyu.geminilive.live

import android.util.Base64
import android.util.Log
import io.github.clyu.geminilive.data.LiveSettings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

sealed interface LiveEvent {
    data object SetupComplete : LiveEvent
    class Audio(val pcm: ByteArray) : LiveEvent
    data class InputTranscription(val text: String) : LiveEvent
    data class OutputTranscription(val text: String) : LiveEvent
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
    data class GoAway(val timeLeft: String?) : LiveEvent
    data class ResumptionHandle(val handle: String) : LiveEvent
    data class Closed(val code: Int, val reason: String?, val error: Throwable?) : LiveEvent
}

/**
 * A single WebSocket connection to the Gemini Live API (BidiGenerateContent).
 *
 * Events are delivered on OkHttp's WebSocket reader thread. Once [close] is called no further
 * events are delivered.
 */
class LiveSession(
    private val client: OkHttpClient,
    private val settings: LiveSettings,
    private val resumeHandle: String?,
    private val onEvent: (LiveEvent) -> Unit,
) {
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var disposed = false
    private val finished = AtomicBoolean(false)

    fun connect() {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("key", settings.apiKey)
            .build()
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    /** Sends 16 kHz, 16-bit little-endian mono PCM. Dropped until the setup has completed. */
    fun sendAudio(pcm: ByteArray) {
        if (!ready) return
        val data = Base64.encodeToString(pcm, Base64.NO_WRAP)
        webSocket?.send("""{"realtimeInput":{"audio":{"mimeType":"audio/pcm;rate=16000","data":"$data"}}}""")
    }

    /** Tells the server the microphone was turned off so it can flush any cached audio. */
    fun sendAudioStreamEnd() {
        if (!ready) return
        webSocket?.send("""{"realtimeInput":{"audioStreamEnd":true}}""")
    }

    fun close() {
        disposed = true
        ready = false
        webSocket?.close(1000, null)
        webSocket = null
    }

    private fun buildSetupMessage(): String {
        val model = settings.model.trim().let { if (it.startsWith("models/")) it else "models/$it" }
        val voiceConfig = JSONObject().put(
            "prebuiltVoiceConfig",
            JSONObject().put("voiceName", settings.voice.trim()),
        )
        val setup = JSONObject()
            .put("model", model)
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", voiceConfig)),
            )
            .put("inputAudioTranscription", transcriptionConfig())
            .put("outputAudioTranscription", transcriptionConfig())
            // Lets the conversation outlive the ~10 minute connection limit and the 15 minute audio session limit.
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().apply { resumeHandle?.let { put("handle", it) } })
        if (settings.systemInstruction.isNotBlank()) {
            setup.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", settings.systemInstruction))),
            )
        }
        return JSONObject().put("setup", setup).toString()
    }

    private fun transcriptionConfig(): JSONObject {
        val codes = settings.transcriptionLanguageCodes
        return JSONObject().apply { if (codes.isNotEmpty()) put("languageCodes", JSONArray(codes)) }
    }

    private fun handleMessage(text: String) {
        val message = try {
            JSONObject(text)
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unparseable server message", e)
            return
        }
        if (message.has("setupComplete")) {
            ready = true
            emit(LiveEvent.SetupComplete)
        }
        message.optJSONObject("serverContent")?.let(::handleServerContent)
        message.optJSONObject("sessionResumptionUpdate")?.let { update ->
            val handle = update.optString("newHandle")
            if (update.optBoolean("resumable") && handle.isNotEmpty()) emit(LiveEvent.ResumptionHandle(handle))
        }
        message.optJSONObject("goAway")?.let { goAway ->
            emit(LiveEvent.GoAway(goAway.optString("timeLeft").ifEmpty { null }))
        }
    }

    private fun handleServerContent(content: JSONObject) {
        content.optJSONObject("inputTranscription")?.optString("text")
            ?.takeIf { it.isNotEmpty() }
            ?.let { emit(LiveEvent.InputTranscription(it)) }

        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val mimeType = inlineData.optString("mimeType")
                if (mimeType.isNotEmpty() && !mimeType.startsWith("audio/")) continue
                val data = inlineData.optString("data")
                if (data.isEmpty()) continue
                try {
                    emit(LiveEvent.Audio(Base64.decode(data, Base64.DEFAULT)))
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Ignoring malformed audio chunk", e)
                }
            }
        }

        content.optJSONObject("outputTranscription")?.optString("text")
            ?.takeIf { it.isNotEmpty() }
            ?.let { emit(LiveEvent.OutputTranscription(it)) }

        if (content.optBoolean("interrupted")) emit(LiveEvent.Interrupted)
        if (content.optBoolean("turnComplete")) emit(LiveEvent.TurnComplete)
    }

    private fun finish(code: Int, reason: String?, error: Throwable?) {
        if (!finished.compareAndSet(false, true)) return
        ready = false
        Log.i(TAG, "Session closed: code=$code reason=$reason", error)
        emit(LiveEvent.Closed(code, reason, error))
    }

    private fun emit(event: LiveEvent) {
        if (!disposed) onEvent(event)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(buildSetupMessage())
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

        // The server may deliver JSON messages as binary frames.
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            finish(code, reason, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(code, reason, null)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // The exception message already names the HTTP status, e.g. when the upgrade is rejected.
            finish(response?.code ?: -1, null, t)
        }
    }

    private companion object {
        const val TAG = "LiveSession"
        const val ENDPOINT =
            "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
