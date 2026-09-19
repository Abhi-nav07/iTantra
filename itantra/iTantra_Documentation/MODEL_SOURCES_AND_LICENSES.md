# Model Sources and Licenses

| Model Use | Artifact / Repo | Source Repository | Layered License Breakdown | Evidence Level |
|---|---|---|---|---|
| STT (All 10 Languages - Shared) | Whisper Tiny Multilingual (`tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx`, `tiny-tokens.txt`) | csukuangfj/sherpa-onnx-whisper-tiny | Runtime (Sherpa-ONNX): Apache 2.0<br>Repo wrapper: Apache 2.0<br>Upstream Whisper Model: MIT | SOURCE_VERIFIED (Provisioning & Architecture) |
| TTS (All 10 Languages) | MMS TTS ONNX (`model.onnx`, `tokens.txt` per language) | willwade/mms-tts-multilingual-models-onnx | Runtime (Sherpa-ONNX): Apache 2.0<br>Repo wrapper: MIT<br>Upstream MMS Model weights: CC-BY-NC 4.0 | SOURCE_VERIFIED (Provisioning & Architecture) |
| MT Indic->English | IndicTrans2 CT2 (`model.bin`, `config.json`, vocab files) | adalat-ai/ct2-rotary-indictrans2-indic-en-dist-200M | Runtime (CTranslate2): MIT<br>CT2 Port Repo: MIT<br>Upstream IndicTrans2 Model: CC-BY-4.0 | HOST_VERIFIED (Host MT Inference) |
| MT English->Indic | IndicTrans2 CT2 (`model.bin`, `config.json`, vocab files) | adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M | Runtime (CTranslate2): MIT<br>CT2 Port Repo: MIT<br>Upstream IndicTrans2 Model: CC-BY-4.0 | HOST_VERIFIED (Host MT Inference) |
| Voice Activity Detection | Silero VAD (`silero_vad.onnx`) | snakers4/silero-vad | Runtime (ONNX Runtime): MIT<br>Model weights: MIT | SOURCE_VERIFIED (Unit Tested) |
