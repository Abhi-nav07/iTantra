# Module 8D: Low-End Performance Audit

## Measurement Methodology
Physical device performance measurements (PSS, actual load latency, frame drops, battery, thermal) are marked as **NOT_TESTED** since no physical Android device was available during automated validation.

The architecture includes proactive low-end device safeguards:
1. **DeviceCapabilityDetector**: Measures Total RAM, Available RAM, Low-RAM flag, API level, ABI list, CPU core count, Manufacturer, and Model. Heuristic profiles: `FULL_AI` (>=4 GB), `STANDARD_AI` (2-4 GB), `CORE_ONLY` (<2 GB or isLowRamDevice).
2. **Audio Stream Bounds**: `MicrophoneAudioSource.kt` employs `extraBufferCapacity = 0` on its SharedFlow, bounding infinite accumulation.
3. **MT Tokens Bounded**: `CTranslate2TranslationEngine.kt` auto-regressive generation is hard-capped at max tokens.
4. **Dynamic Language Unloading**: `ActiveLanguageSessionManager.kt` unloads previous language engines before switching to new ones, preventing memory accumulation.

## OOM / ANR Prevention
- Dynamic RAM checking refuses heavy neural model loads if `DeviceCapabilityDetector` categorizes the device as `CORE_ONLY` (<2 GB total RAM or `isLowRamDevice`).
- On constrained devices, state is set to `ERROR` while keeping emergency and text communication available without crashing.
- Unused AI layers cleanly cascade failures to text-only UI preservation.

## Storage Accounting
- **Shared STT**: ~99 MB (Whisper Tiny int8)
- **10-Language TTS**: ~1.21 GB (MMS/VITS ONNX)
- **IndicTrans2 MT**: ~1.63 GB (CT2 indic-en + en-indic)
- **Total Model Store**: ~2.87 GB
- **APK**: ~136 MB

## Performance Benchmark Harness
`tools/android_performance_benchmark.ps1` implements 6 sustained scenarios via ADB:
- `APP_IDLE`, `CONTINUOUS_VAD`, `REPEATED_STT`, `REPEATED_TTS`, `REPEATED_MT`, `MIXED_TRANSCEIVER_LOOP`
- Captures meminfo PSS, CPU via top/dumpsys, battery level, battery temperature
- If no device is connected, marks all device metrics `NOT_TESTED`

## Remaining
- Physical RAM profiling: NOT_TESTED
- Sustained inference thermal impact: NOT_TESTED
- Low-end device (<2 GB) field test: NOT_TESTED
