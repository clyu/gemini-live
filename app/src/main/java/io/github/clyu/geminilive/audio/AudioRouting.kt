package io.github.clyu.geminilive.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Puts the device into voice-communication mode (which enables hardware echo cancellation) and
 * routes the output to a headset if one is connected, otherwise to the loudspeaker instead of
 * the earpiece.
 */
class AudioRouting(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var active = false

    fun start() {
        if (active) return
        active = true
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
            .also { audioManager.requestAudioFocus(it) }
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeOutput()
    }

    fun stop() {
        if (!active) return
        active = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            setSpeakerphoneLegacy(false)
        }
        audioManager.mode = previousMode
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun routeOutput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val device = devices.firstOrNull { it.type in HEADSET_TYPES }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (device != null) runCatching { audioManager.setCommunicationDevice(device) }
        } else {
            val hasWiredHeadset = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type in WIRED_HEADSET_TYPES }
            setSpeakerphoneLegacy(!hasWiredHeadset)
        }
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneLegacy(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    private companion object {
        val WIRED_HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        val HEADSET_TYPES = WIRED_HEADSET_TYPES + setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
