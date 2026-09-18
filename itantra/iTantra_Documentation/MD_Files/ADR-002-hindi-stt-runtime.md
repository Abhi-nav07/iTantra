# ADR-002: Hindi STT Runtime Selection

## Context
iTantra is an offline-first multilingual neural voice transceiver. For our Hindi Speech-To-Text (STT) feature, we require an inference runtime capable of running state-of-the-art ASR models fully offline on Android devices.

## Requirements
- Must support Android natively.
- Fully offline inference capabilities.
- Compatible with modern transducer or CTC architectures.
- Low footprint (RAM and Disk) on the device.
- Must support efficient 8-bit quantized models.

## Options Considered
1. **ONNX Runtime Android**: Requires custom audio preprocessing pipelines, native C++ bridging, and custom decoding graphs. High complexity for STT specifically.
2. **sherpa-onnx**: Dedicated wrapper around ONNX Runtime for speech processing created by the Next-Gen Kaldi (k2-fsa) community. Provides simple Android AARs and Kotlin APIs, robust feature extraction, and built-in decoding.

## Selected Runtime
**sherpa-onnx**

## Selected Candidate Hindi Model
- **Source:** HuggingFace repository `parismitaglobalsolutions/indicconformer-sherpa-onnx`
- **Official/Community Status:** Community export of the AI4Bharat IndicConformer model.
- **Model Format:** ONNX (Sherpa-compatible offline CTC).
- **Quantization:** `int8`
- **Size:** ~197MB (download size).
- **Sample Rate:** 16000 Hz.
- **License:** Open (inherited from AI4Bharat).

## Why Selected
`sherpa-onnx` encapsulates the entire audio processing, STT graph execution, and final decoding into a clean API that reduces Android development time. The `indicconformer-sherpa-onnx` model provides the exact `int8` quantization required for mobile constraints while retaining robust transcription accuracy for Hindi.

## Model Size & RAM Implications
The 197MB `int8` model translates to roughly a 200MB disk footprint for the Hindi pack. At runtime, memory mapping will consume ~250MB-400MB RAM, which remains within safe bounds for modern Android devices.

## Latency Implications
Using `sherpa-onnx` with CTC enables real-time factor (RTF) decoding often far below 1.0 on modern ARM cores, allowing for fast finalization upon PTT release. 

## Android Compatibility
The official `sherpa-onnx.aar` seamlessly supports ARM64/ARMv7 Android ABIs with a JNI Kotlin bridge.

## Risks
- The model is a community conversion, so we depend on its correct export from the official AI4Bharat checkpoint.
- Lack of official Maven Central artifacts for `sherpa-onnx` requires manual management of the `.aar` binary.

## Fallback Plan
If `sherpa-onnx` lacks stability, pivot to a generic TensorFlow Lite deployment (if a TFLite export is available) or raw ONNX Runtime (developing our own audio processing bridge).

## Future Path for Remaining Languages
`sherpa-onnx` remains language-agnostic; once the pipeline is validated for Hindi, English and other supported languages can cleanly plug into the identical runtime by simply downloading the respective language packs.
