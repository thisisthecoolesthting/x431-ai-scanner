# F5 — Hands-Free Voice Mode

Wake-word voice control for Together Scanners AI. Greasy-hands-friendly.
Say **"Hey Together"** to activate, then speak any diagnostic command.

---

## File map

```
voice/
  VoiceMode.kt           — wake-word detection + utterance capture (SpeechRecognizer)
  VoiceIntentResolver.kt — utterance → VoiceIntent (keyword + Levenshtein, no network)
  VoiceCommander.kt      — orchestrator: VoiceMode → resolver → EngineDriver + TTS
overlay/compose/
  VoiceIndicator.kt      — Compose mic icon, pulse animation, ghost-text confirmation
```

---

## Wire-up

### 1. AndroidManifest.xml — add RECORD_AUDIO (already present in baseline)

```xml
<!-- Voice mode -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Register VoiceService if you run the commander from a foreground service
(recommended — keeps mic warm when overlay is over X431):

```xml
<service
    android:name=".voice.VoiceService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Hands-free voice control for Together diagnostic overlay" />
</service>
```

### 2. Runtime permission request

Request before calling `VoiceCommander.start()`:

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        RC_RECORD_AUDIO,
    )
}
```

### 3. Instantiate VoiceCommander in OverlayService

```kotlin
// In OverlayService or a dedicated VoiceService
private lateinit var voiceCommander: VoiceCommander

override fun onCreate() {
    super.onCreate()
    voiceCommander = VoiceCommander(
        context = this,
        engineCallback = object : VoiceCommander.EngineCallback {
            override fun onReadCodes()               = engineDriver.dispatch("read_dtcs")
            override fun onClearCodes()              = engineDriver.dispatch("clear_dtcs")
            override fun onGraphPid(pidLabel: String)= overlayVm.requestPidGraph(pidLabel)
            override fun onRunCapability(id: String) = engineDriver.dispatch(id)
            override fun onDismiss()                 = overlayVm.dismiss()
            override fun onPeek()                    = overlayVm.peek()
            override fun onHelp()                    = overlayVm.showHelp()
            override fun onVoiceStateChanged(s: VoiceMode.State) = overlayVm.setVoiceState(s)
            override fun onLastPhraseChanged(p: String)          = overlayVm.setLastPhrase(p)
        },
        catalogProvider = { CapabilityMap.ALL },  // swap with hot-patched list if available
    )
    voiceCommander.start()
}

override fun onDestroy() {
    voiceCommander.stop()
    super.onDestroy()
}
```

### 4. Place VoiceIndicator in your overlay Composable

```kotlin
@Composable
fun OverlayContent(vm: OverlayViewModel) {
    val voiceState by vm.voiceState.collectAsState()
    val lastPhrase by vm.lastPhrase.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // ... existing overlay content

        VoiceIndicator(
            state     = voiceState,
            lastPhrase = lastPhrase,
            modifier  = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 56.dp),
        )
    }
}
```

Add to `OverlayViewModel`:

```kotlin
private val _voiceState = MutableStateFlow(VoiceMode.State.IDLE)
val voiceState: StateFlow<VoiceMode.State> = _voiceState.asStateFlow()

private val _lastPhrase = MutableStateFlow("")
val lastPhrase: StateFlow<String> = _lastPhrase.asStateFlow()

fun setVoiceState(s: VoiceMode.State) { _voiceState.value = s }
fun setLastPhrase(p: String)          { _lastPhrase.value = p }
```

---

## Supported commands (examples)

| Spoken phrase                              | Intent class    | Capability ID             |
|--------------------------------------------|-----------------|---------------------------|
| "Hey Together, read codes"                 | ReadCodes       | read_dtcs                 |
| "Hey Together, clear codes"                | ClearCodes      | clear_dtcs                |
| "Hey Together, run full scan"              | RunCapability   | full_scan                 |
| "Hey Together, oil reset"                  | RunCapability   | oil_reset                 |
| "Hey Together, graph short term fuel trims bank one" | GraphPid | live_data_fuel_trim_b1  |
| "Hey Together, graph RPM"                  | GraphPid        | live_data_rpm             |
| "Hey Together, EPB"                        | RunCapability   | epb                       |
| "Hey Together, show live data"             | RunCapability   | live_data                 |
| "Hey Together, what can you do?"           | Help            | —                         |
| "Hey Together, dismiss"                    | Dismiss         | —                         |

---

## Design decisions

- **On-device only** — `EXTRA_PREFER_OFFLINE = true`. No API key, works in basements, zero latency.
- **Warm idle loop** — `VoiceMode` restarts the recognizer automatically after each result/error so there is always a live mic session. Delay is 400 ms to respect Android's `ERROR_RECOGNIZER_BUSY`.
- **Partial results** — wake-word is checked on every `onPartialResults` callback, so "Hey Together read codes" works as a single inline utterance with sub-second response.
- **No LLM in the resolve loop** — `VoiceIntentResolver` is pure Kotlin (keyword table + Levenshtein). Keeps latency <5 ms on-device.
- **Fuzzy matching** — Levenshtein distance ≤ 4 against capability labels catches speech-rec noise ("throttle relearn" vs "throttle re learn").
- **TTS confirmation** speaks before the capability executes, giving the tech auditory feedback without looking at the screen.

---

## Dependencies (add to build.gradle if not present)

```kotlin
// Compose Material3 (for VoiceIndicator icons)
implementation("androidx.compose.material3:material3:<version>")
implementation("androidx.compose.material:material-icons-extended:<version>")
// SpeechRecognizer and TextToSpeech are in the Android SDK — no extra dep needed
```

---

## Manifest permissions summary (RECORD_AUDIO already in baseline manifest)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<!-- Already present in AndroidManifest.xml baseline:
     INTERNET, FOREGROUND_SERVICE, SYSTEM_ALERT_WINDOW, WAKE_LOCK -->
```

`RECORD_AUDIO` is the **only new permission** F5 requires. All others were already declared for
the acoustic engine (baseline manifest line: `<!-- Acoustic engine listening -->`).
