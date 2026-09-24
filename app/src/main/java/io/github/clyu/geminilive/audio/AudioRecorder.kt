package io.github.clyu.geminilive.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import kotlin.concurrent.thread

/** Captures 16 kHz, 16-bit mono PCM from the microphone, the input format of the Live API. */
class AudioRecorder(private val onChunk: (ByteArray) -> Unit) {

    private var record: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureThread: Thread? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested before a session starts.
    fun start() {
        if (running) return
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        // VOICE_COMMUNICATION enables the platform echo canceller, so the model does not hear itself.
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuffer, CHUNK_BYTES * 4),
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("AudioRecord could not be initialized")
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.also { it.setEnabled(true) }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)?.also { it.setEnabled(true) }
        }
        audioRecord.startRecording()
        record = audioRecord
        running = true
        captureThread = thread(name = "live-mic") { captureLoop(audioRecord) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { record?.stop() } // Unblocks the pending read().
        captureThread?.join(STOP_TIMEOUT_MS)
        captureThread = null
        record?.release()
        record = null
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    private fun captureLoop(audioRecord: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = ByteArray(CHUNK_BYTES)
        while (running) {
            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                onChunk(buffer.copyOf(read))
            } else if (read < 0) {
                break
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_BYTES = SAMPLE_RATE * 2 / 20 // 50 ms
        const val STOP_TIMEOUT_MS = 500L
    }
}
