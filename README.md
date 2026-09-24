# Live Voice Chat for Gemini

An Android app for real-time voice conversations with Gemini through the [Gemini Live API](https://ai.google.dev/gemini-api/docs/live-api). Live captions of both the user and the model are shown on screen during the conversation.

## Features

- Connects directly to the Live API over WebSocket (`BidiGenerateContent`), streaming 16 kHz PCM from the microphone and playing back 24 kHz PCM
- Live captions for both sides via `inputAudioTranscription` / `outputAudioTranscription`
- Barge-in: model playback stops immediately when the user starts speaking
- Microphone mute and caption clearing
- Session resumption and context window compression, so the conversation automatically continues when the connection expires (`goAway`) or the network drops
- Settings screen:
  - API key
  - Live API model (defaults to `gemini-3.8-live`; any other model ID can be entered)
  - System instruction
  - Model voice (defaults to `Zephyr`; pick from 30 prebuilt voices)
  - Caption language (defaults to Traditional Chinese and English; check one or more languages from a list, or none for automatic detection)

## Building

GitHub Actions (`.github/workflows/android.yml`) runs `gradle :app:assembleRelease` on every push and uploads the release APK, optimized and shrunk with R8, as a workflow artifact.

To let each CI build update the app already installed on a device, the workflow signs the APK with a fixed key stored in these repository secrets (without them, the APK is signed with the build machine's debug key, which on CI changes every run):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `SIGNING_KEYSTORE_PASSWORD` | The keystore password (also used as the key password) |
| `SIGNING_KEY_ALIAS` | The key alias |

Create a keystore with `keytool` (bundled with Android Studio under `jbr/bin`) and keep it out of the repository:

```
keytool -genkeypair -keystore geminilive.jks -storetype PKCS12 -alias geminilive -keyalg RSA -keysize 2048 -validity 10000
```

Requirements: AGP 9.4, Gradle 9.6 or later, JDK 17 or later, compileSdk 37. The project does not include a Gradle wrapper; to build locally, generate one first by running `gradle wrapper` with Gradle 9.7.1.

## Notes

- The API key is stored in the app's private DataStore and is excluded from cloud backups and device transfers.
- Audio is only captured while the app is in the foreground; Android stops delivering microphone audio once the app is in the background.
- On the loudspeaker, the app relies on the device's built-in echo cancellation, which varies by device. Headphones give the best experience.
