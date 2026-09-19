# iTantra Efficiency & Hardware Performance Matrix

This matrix documents actual measured storage footprints, process memory (PSS), model load latency, inference timing, CPU consumption, and thermal/battery impact for iTantra.

> **Truthful Evidence Protocol**  
> - **STATIC_MEASURED**: Concrete files measured on disk / filesystem (APK, model blobs).
> - **SOURCE_READY**: Measurement harness, lifecycle contracts, and profiling scripts implemented and unit-tested in source code.
> - **DEVICE_MEASURED**: Measured via live physical Android target via ADB profiler.
> - **NOT_TESTED**: When physical device execution has not occurred, no synthetic numbers are fabricated.

---

## 1. System Efficiency & Performance Table

| Metric | Source / Method | Measured Value | Evidence Level | Device | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APK Size** | Release debug build filesystem | `142,801,738 bytes` (136.19 MB) | STATIC_MEASURED | Host Build System | Includes compiled Kotlin classes, assets, resources, and JNI `.so` libraries. |
| **Shared STT Model Size** | `provisioned_models/shared/stt/` | `103,609,903 bytes` (98.81 MB) | STATIC_MEASURED | Host Model Store | Whisper Tiny Multilingual INT8 ONNX (encoder: 12.94 MB, decoder: 89.86 MB, tokens: 0.82 MB). Shared once across all 10 languages. |
| **10-Language TTS Total Size** | `provisioned_models/*/tts/` | `1,266,749,039 bytes` (1,208.07 MB) | STATIC_MEASURED | Host Model Store | Meta MMS VITS ONNX models across 10 languages (~108.76 MB - 169.01 MB per language). |
| **Translation (MT) Total Size** | `provisioned_models/mt/` | `1,712,502,545 bytes` (1,633.17 MB) | STATIC_MEASURED | Host Model Store | IndicTrans2 INT8 (indic-en: 816.59 MB, en-indic: 816.58 MB). Enables direct and 2-hop pivot translation across all language pairs. |
| **Total All-Model Store** | Cumulative unique models | `3,082,861,487 bytes` (2,940.05 MB) | STATIC_MEASURED | Host Model Store | Full offline multilingual AI stack (Shared STT + 10 TTS + 2-way MT). |
| **Baseline App PSS** | `dumpsys meminfo com.itantra` | NOT_TESTED | NOT_TESTED | Device Required | Awaiting physical device connection (`adb devices`). |
| **STT Loaded PSS** | `dumpsys meminfo com.itantra` | NOT_TESTED | NOT_TESTED | Device Required | Measured during active Whisper STT session. |
| **TTS Loaded PSS** | `dumpsys meminfo com.itantra` | NOT_TESTED | NOT_TESTED | Device Required | Measured during active VITS TTS synthesis. |
| **MT Loaded PSS** | `dumpsys meminfo com.itantra` | NOT_TESTED | NOT_TESTED | Device Required | Measured during active CTranslate2 inference. |
| **Peak PSS Observed** | Session peak profiler | NOT_TESTED | NOT_TESTED | Device Required | Peak total PSS memory across all phases. |
| **Idle App CPU %** | `top -b` via ADB | NOT_TESTED | NOT_TESTED | Device Required | CPU usage with app in foreground idle. |
| **Continuous VAD CPU %** | `top -b` via ADB | NOT_TESTED | NOT_TESTED | Device Required | Background audio buffering and voice activity detection. |
| **STT Inference CPU %** | `top -b` via ADB | NOT_TESTED | NOT_TESTED | Device Required | Multi-threaded ONNX Whisper transcription. |
| **TTS Synthesis CPU %** | `top -b` via ADB | NOT_TESTED | NOT_TESTED | Device Required | VITS acoustic waveform generation. |
| **MT Translation CPU %** | `top -b` via ADB | NOT_TESTED | NOT_TESTED | Device Required | CTranslate2 beam search inference. |
| **STT Cold Load Latency** | `MetricsRecorder.recordSttModelLoadTime` | SOURCE_READY | SOURCE_READY | Device Required | Timed in production `SherpaOnnxSpeechRecognizer.load()`. |
| **TTS Cold Load Latency** | `MetricsRecorder.recordTtsModelLoadTime` | SOURCE_READY | SOURCE_READY | Device Required | Timed in production `SherpaOnnxSpeechSynthesizer.load()`. |
| **MT Load Latency** | `CTranslate2TranslationEngine.init` | SOURCE_READY | SOURCE_READY | Device Required | Timed during `nativeCreateEngine` execution. |
| **15-Min Battery Drain** | `dumpsys battery` delta | NOT_TESTED | NOT_TESTED | Device Required | Measured via `tools/android_performance_benchmark.ps1`. |
| **15-Min Thermal Delta** | Battery thermistor / sysfs | NOT_TESTED | NOT_TESTED | Device Required | Measured via `tools/android_performance_benchmark.ps1`. |

---

## 2. Per-Language Storage Breakdown

| Language | ISO Code | STT Bytes (Shared) | TTS Bytes (VITS) | MT Model Availability | Total Storage Per Lang Pack |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hindi** | `hi` | Shared (98.81 MB) | 177,189,258 B (168.98 MB) | IndicTrans2 Shared | ~168.98 MB standalone |
| **English** | `en` | Shared (98.81 MB) | 177,219,374 B (169.01 MB) | IndicTrans2 Shared | ~169.01 MB standalone |
| **Bengali** | `bn` | Shared (98.81 MB) | 114,045,156 B (108.76 MB) | IndicTrans2 Shared | ~108.76 MB standalone |
| **Gujarati** | `gu` | Shared (98.81 MB) | 114,034,326 B (108.75 MB) | IndicTrans2 Shared | ~108.75 MB standalone |
| **Marathi** | `mr` | Shared (98.81 MB) | 114,044,398 B (108.76 MB) | IndicTrans2 Shared | ~108.76 MB standalone |
| **Kannada** | `kn` | Shared (98.81 MB) | 114,045,931 B (108.76 MB) | IndicTrans2 Shared | ~108.76 MB standalone |
| **Malayalam** | `ml` | Shared (98.81 MB) | 114,052,927 B (108.77 MB) | IndicTrans2 Shared | ~108.77 MB standalone |
| **Tamil** | `ta` | Shared (98.81 MB) | 114,032,763 B (108.75 MB) | IndicTrans2 Shared | ~108.75 MB standalone |
| **Telugu** | `te` | Shared (98.81 MB) | 114,038,199 B (108.76 MB) | IndicTrans2 Shared | ~108.76 MB standalone |
| **Odia** | `or` | Shared (98.81 MB) | 114,046,707 B (108.76 MB) | IndicTrans2 Shared | ~108.76 MB standalone |

*Note on Hindi & English: Active provisioned MMS-VITS model size in manifest is `114,044,080 B` (Hindi) and `114,017,949 B` (English) totaling `1,140,402,436 B` active TTS store. Host development directories also retain legacy unlinked Piper weights (`63,145,178 B` and `63,201,425 B`), totaling `1,266,749,039 B` on the host disk.*

---

## 3. Internal Engineering Targets vs Measured Results

| Metric | Internal Target | Measured Device Result | Target Status |
| :--- | :--- | :--- | :--- |
| **Out-Of-Memory (OOM)** | Zero OOM crashes | NOT_TESTED | Target Defined (Guarded by `CORE_ONLY` policy) |
| **Application Not Responding (ANR)** | Zero ANR timeouts | NOT_TESTED | Target Defined (Background coroutines used) |
| **STT Real-Time Factor (RTF)** | $< 1.0$ (Faster than real time) | NOT_TESTED | Target Defined |
| **TTS Real-Time Factor (RTF)** | $< 0.5$ (Batch compute duration $\le 50\%$ audio duration) | NOT_TESTED | Target Defined |
| **Memory Policy** | Only 1 language active in memory at a time | SOURCE_VERIFIED | Enforced by `ActiveLanguageSessionManager` mutex |
| **Low-RAM Safety** | Auto-disable heavy models on $\le 2\text{ GB}$ devices | SOURCE_VERIFIED | Enforced by `DeviceCapabilityDetector.CORE_ONLY` |
