# Module 6B Language Test Results

## 1. Hindi (Reference)
- **STT:** PASS (Verified via previous architecture validation)
- **TTS:** PASS (Verified via previous architecture validation)
- **PTT:** PASS (Verified via previous architecture validation)
- **Continuous:** PASS (Verified via previous architecture validation)
- **Offline:** BLOCKED (Physical device testing required for full offline validation)
- **Status:** READY
- **Size:** 197 MB (STT), 35 MB (TTS)

## 2. English
- **STT:** BLOCKED (Requires physical mic input on device to validate)
- **TTS:** BLOCKED (Requires physical speaker output on device to validate)
- **PTT:** BLOCKED
- **Continuous:** BLOCKED
- **Offline:** BLOCKED
- **Status:** CANDIDATE (Integration code is written and uses generic paths, but models must be physically downloaded and tested)
- **Size:** ~150 MB (STT), 35 MB (TTS)

## 3. Marathi
- **STT:** BLOCKED (Requires physical mic input on device to validate)
- **TTS:** BLOCKED (Requires physical speaker output on device to validate)
- **PTT:** BLOCKED
- **Continuous:** BLOCKED
- **Offline:** BLOCKED
- **Status:** CANDIDATE (Integration code is written and uses generic paths, but models must be physically downloaded and tested)
- **Size:** ~150 MB (STT), 35 MB (TTS)

## Models Selected
| Language | STT Model | TTS Model | Runtime | Official/Community |
|----------|-----------|-----------|---------|--------------------|
| Hindi (hi) | indicconformer-sherpa-onnx | vits-mms-hin | sherpa-onnx | Official (AI4Bharat) / Community conversion |
| English (en) | indicconformer-sherpa-onnx-en | vits-mms-eng | sherpa-onnx | Official (AI4Bharat) / Community conversion |
| Marathi (mr) | indicconformer-sherpa-onnx-mr | vits-mms-mar | sherpa-onnx | Official (AI4Bharat) / Community conversion |
