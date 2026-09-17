# iTantra

Offline, multilingual neural voice transceiver — SIH 2026, Problem
Statement 173. See `docs/ARCHITECTURE.md` for the full design, `docs/
MODEL_STRATEGY.md` for model research findings, and `docs/adr/` for
recorded engineering decisions.

**Status: Task 01 (repository audit + foundation) only.** No STT, TTS,
VAD, or Bluetooth/Wi-Fi transport is implemented. The app currently
demonstrates architecture, navigation, and UI shell only — see the Task
01 completion report for exactly what was verified vs. not.

## Building

Requires Android Studio (or a standalone Android SDK) and JDK 17+.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Note: this repository's Gradle wrapper JAR (`gradle/wrapper/gradle-
wrapper.jar`) is intentionally **not** committed by the environment that
authored Task 01, because that environment had no network access to fetch
it. Run `gradle wrapper` once with a locally installed Gradle (matching
the version in `gradle/wrapper/gradle-wrapper.properties`) to regenerate
it before using `./gradlew`, or open the project directly in Android
Studio, which will bootstrap the wrapper automatically.

## Project layout

```
app/src/main/java/com/itantra/
  app/            entry point, navigation, theme, manual DI (AppGraph)
  domain/model/   pure data types (Language, LanguagePackManifest, Measurement, ...)
  domain/repository/  LanguagePackRepository contract
  core/inference/ SpeechRecognizerEngine / SpeechSynthesizerEngine / VoiceActivityDetector
                  interfaces + ActiveLanguageSessionManager
  core/transport/ TransportEngine contract (Bluetooth/Wi-Fi Direct — not implemented)
  core/storage/   LanguagePackStorage contract (not implemented)
  core/metrics/   MetricsRecorder — real measurements only, never fabricated
  data/languagepack/  MockLanguagePackRepository (Task 01: in-memory only)
  feature/transceiver/  main screen
  feature/languages/    language packs screen
  feature/diagnostics/  diagnostics screen
```
