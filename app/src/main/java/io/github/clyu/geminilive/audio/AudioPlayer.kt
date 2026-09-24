package io.github.clyu.geminilive.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Streams the model's 24 kHz, 16-bit mono PCM to an [AudioTrack].
 *
 * [enqueue] and [interrupt] may be called from any thread.
 */
class AudioPlayer {

    private class Chunk(val generation: Int, val pcm: ByteArray)

    private val queue = LinkedBlockingQueue<Chunk>()
    private val lock = Any()
    private var track: AudioTrack? = null // Guarded by lock.
    private var generation = 0 // Guarded by lock; bumped on interrupt so queued audio is dropped.
    private var framesWritten = 0L // Guarded by lock; reset whenever the track is flushed.
    private var playbackThread: Thread? = null
    @Volatile private var running = false

    private val _speaking = MutableStateFlow(false)
    /** True while model audio is actually coming out of the speaker. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun start() {
        if (running) return
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL)
                    .setEncoding(ENCODING)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, SLICE_BYTES * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.play()
        synchronized(lock) {
            track = audioTrack
            framesWritten = 0
        }
        running = true
        playbackThread = thread(name = "live-playback") { playbackLoop(audioTrack) }
    }

    fun enqueue(pcm: ByteArray) {
        if (!running) return
        synchronized(lock) { queue.offer(Chunk(generation, pcm)) }
    }

    /** Drops everything queued or buffered, e.g. when the user barges in. */
    fun interrupt() {
        synchronized(lock) {
            generation++
            queue.clear()
            framesWritten = 0
            track?.let {
                it.pause()
                it.flush()
                it.play()
            }
        }
        _speaking.value = false
    }

    fun stop() {
        if (!running) return
        running = false
        playbackThread?.join(STOP_TIMEOUT_MS)
        playbackThread = null
        synchronized(lock) {
            queue.clear()
            track?.let {
                runCatching { it.stop() }
                it.release()
            }
            track = null
            framesWritten = 0
        }
        _speaking.value = false
    }

    private fun playbackLoop(audioTrack: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (running) {
            val chunk = queue.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS)
            if (chunk == null) {
                synchronized(lock) {
                    if (_speaking.value && audioTrack.playbackHeadPosition.toLong() >= framesWritten) {
                        _speaking.value = false
                    }
                }
                continue
            }
            // Write in small slices so an interrupt takes effect within one slice.
            var offset = 0
            while (running && offset < chunk.pcm.size) {
                val written = synchronized(lock) {
                    if (chunk.generation != generation) {
                        -1
                    } else {
                        audioTrack.write(chunk.pcm, offset, minOf(SLICE_BYTES, chunk.pcm.size - offset))
                            .also { if (it > 0) framesWritten += it / BYTES_PER_FRAME }
                    }
                }
                if (written <= 0) break
                offset += written
                _speaking.value = true
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_FRAME = 2
        const val SLICE_BYTES = SAMPLE_RATE * BYTES_PER_FRAME / 50 // 20 ms
        const val IDLE_POLL_MS = 50L
        const val STOP_TIMEOUT_MS = 500L
    }
}
