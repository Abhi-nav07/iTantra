# ADR-003: Hindi TTS Runtime Selection

## Context
iTantra is an offline-first multilingual neural voice transceiver. For our Text-To-Speech (TTS) feature, we require an inference runtime capable of running offline on Android devices with minimal latency, low disk footprint, and fast synthesis.

## Requirements
- Must support Android natively.
- Fully offline inference capabilities.
- Compatible with modern TTS architectures (e.g., VITS).
- Low footprint (RAM and Disk) on the device.
- Must integrate cleanly with our existing `sherpa-onnx` setup to avoid pulling in multiple heavy inference engines.

## Options Considered
1. **AI4Bharat Indic-TTS/Indic-Parler**: Excellent accuracy and naturalness. However, the models are massive (>1GB), based on Diffusion/Transformers, and require complex Android conversion. They exceed our memory constraints for edge devices.
2. **sherpa-onnx OfflineTts (VITS)**: The `sherpa-onnx` library we use for STT also supports VITS-based TTS models. Using this engine allows us to reuse the existing C++ native binaries, minimizing APK bloat.

## Selected Runtime
**sherpa-onnx OfflineTts**

## Selected Candidate Hindi Model
- **Source:** HuggingFace repository `csukuangfj/vits-mms-hin` (Massively Multilingual Speech Hindi VITS model).
- **Format:** ONNX (Sherpa-compatible).
- **Size:** ~35MB.
- **Sample Rate:** 22050 Hz (Standard VITS).

## Why Selected
Using the existing `sherpa-onnx` framework provides maximum architectural cohesion. By selecting a VITS-MMS model, we limit the disk space required for TTS to roughly 35MB. The Real-Time Factor (RTF) of this model on ARM devices is extremely low, allowing audio playback to begin rapidly.

## Integration Plan
- Use Android's `AudioTrack` (`MODE_STREAM`) with `ENCODING_PCM_FLOAT` to play the generated synthesized audio synchronously.
- Measure TTFA (Time to First Audio) and RTF dynamically.
