# Real AI Baseline Audit

## Current Production Implementation
- **STT Implementation:** `SherpaOnnxSpeechRecognizer` utilizing the `k2fsa/sherpa-onnx` native library for on-device inference. Audio is fed via raw PCM streams and decoded locally.
- **TTS Implementation:** `SherpaOnnxSpeechSynthesizer` utilizing the `k2fsa/sherpa-onnx` native library for on-device VITS generation.

## Real Model Selected
- **Language:** Hindi (hi)
- **STT Model:** indicconformer-sherpa-onnx
- **TTS Model:** vits-mms-hin
- **Training/Model Lineage:** AI4Bharat IndicConformer (STT) and AI4Bharat IndicTTS / Meta MMS VITS (TTS).
- **Model Source:** HuggingFace (`parismitaglobalsolutions/indicconformer-sherpa-onnx` and `csukuangfj/vits-mms-hin`).
- **Official/Community Status:** Official base models (AI4Bharat), community converted/quantized for Sherpa-ONNX compatibility.
- **Format:** ONNX
- **Quantization:** int8 (STT) / fp32 (TTS)
- **Runtime:** sherpa-onnx (offline C++ backend)

## Model Files and Sizes
- **STT Files:** `model.int8.onnx`, `tokens.txt`
- **TTS Files:** `tts_model.onnx`, `lexicon.txt`, `tts_tokens.txt`
- **Actual Sizes:** 197 MB (STT), 35 MB (TTS)

## Inference Status
- **Offline Status:** Yes. Models operate 100% locally with no network calls in the inference path.
- **Real-device test status:** BLOCKED. Requires manual physical testing with a real microphone to validate latency, audio capture, and synthesis.
- **WER Status:** NOT MEASURED. Awaiting real human baseline test utterances.

## Known Limitations
- Initial loading of ONNX models requires up to ~500MB of temporary working memory.
- Inference speed (RTF) and memory footprint on low-end ARM devices remains pending physical validation.
