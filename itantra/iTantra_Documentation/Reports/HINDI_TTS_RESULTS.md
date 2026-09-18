# Hindi TTS Results (Task 03A)

This document tracks the offline Text-To-Speech inference metrics for the Hindi `vits-mms-hin` model running locally on Android using `sherpa-onnx`.

## Model Metadata
- **Engine**: `sherpa-onnx` `OfflineTts`
- **Model**: `vits-mms-hin` (ONNX)
- **Quantization**: fp32
- **Size on disk**: ~35MB
- **Sample Rate**: 22050 Hz

## Target Metrics

| Metric | Target | Actual (TBD) |
|---|---|---|
| Model Load Time | < 500ms | |
| Time to First Audio (TTFA) | < 300ms | |
| Real-Time Factor (RTF) | < 0.25x | |
| RAM Overhead | < 150MB | |

*Note: Actuals will be filled following physical device testing. The framework is now in place to measure these directly via the Diagnostics screen.*

## Findings
- **Integration**: The STT and TTS models share the `sherpa-onnx` `.aar`, keeping the base APK size unaffected.
- **Audio Output**: `SpeakerAudioSink` routes the PCM array directly to the system speaker without introducing media player latency.
- **Session Lifecycle**: The `ActiveLanguageSessionManager` correctly tears down old engines before loading new ones to maintain a predictable PSS memory envelope.
