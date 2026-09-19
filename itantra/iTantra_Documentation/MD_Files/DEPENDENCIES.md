# iTantra Dependencies

## Project Core
- **Kotlin Standard Library**: Primary programming language.
- **AndroidX Core KTX**: Standard Android Kotlin extensions.
- **Android Lifecycle**: ViewModels and lifecycle components.

## UI Layer
- **Jetpack Compose**: Declarative UI toolkit.
- **Material3**: Design system for UI components.
- **Navigation Compose**: Type-safe declarative routing.

## Concurrency & Data
- **Kotlin Coroutines**: For lightweight asynchronous tasks.
- **Kotlinx Serialization JSON**: Used for parsing `LanguagePackManifest` natively without heavy reflection libraries like Gson.

## Machine Learning & Inference
- **Sherpa-Onnx (`1.13.8`)**: Local `.aar` integration.
  - Used for strictly offline, on-device Speech-To-Text transcription.
  - Selected due to its robust audio feature extraction and out-of-the-box support for NeMo/Conformer ONNX exports.

## Testing
- **JUnit 4**: Unit testing.
- **Espresso & Compose UI Test**: UI layer verification.

> Note: All Cloud SDKs, telemetry, Firebase, and authentication SDKs are strictly prohibited. Runtime inference and peer communication are fully offline after required models have been provisioned.
