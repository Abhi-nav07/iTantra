# Language Model Readiness Audit

As part of Module 6A, a survey of the 10 target Indic languages was conducted to verify that high-quality, offline-capable STT and TTS models exist and can be legally integrated into iTantra.

## Target Languages
1. Hindi (hi)
2. English (en)
3. Bengali (bn)
4. Gujarati (gu)
5. Marathi (mr)
6. Kannada (kn)
7. Malayalam (ml)
8. Tamil (ta)
9. Telugu (te)
10. Odia (or)

## Speech-to-Text (STT) Candidates
- **Primary Candidate:** AI4Bharat IndicWav2Vec / Shruti
  - **Coverage:** Supports all 10 target languages.
  - **Format:** PyTorch models can be exported to ONNX (int8 quantization recommended for edge).
  - **License:** MIT or CC-BY-4.0 (Open for commercial/offline use).
  - **Size:** ~40-60 MB per language (quantized).
- **Secondary Candidate:** Sherpa-ONNX (Zipformer)
  - **Coverage:** English, Hindi. (Missing other Indic languages).

**STT Recommendation:** Adopt AI4Bharat's ONNX-exported models for robust offline performance.

## Text-to-Speech (TTS) Candidates
- **Primary Candidate:** AI4Bharat IndicTTS (VITS)
  - **Coverage:** Supports all 10 target languages.
  - **Format:** ONNX format directly available.
  - **License:** MIT or CC-BY-4.0.
  - **Size:** ~30-40 MB per language.
- **Secondary Candidate:** Meta MMS (Massively Multilingual Speech)
  - **Coverage:** Supports all 10 target languages.
  - **Format:** VITS architecture, exportable to ONNX.
  - **License:** CC-BY-NC-4.0 (Non-commercial constraint may be an issue if iTantra goes commercial).

**TTS Recommendation:** Adopt AI4Bharat's IndicTTS VITS models due to the permissive license and excellent native speaker quality.

## Model Lifecycle & Size Estimation
Each language pack requires:
- STT ONNX + tokens: ~50MB
- TTS ONNX + lexicon: ~40MB
- Total per language: ~90MB.

Downloading all 10 languages would take ~900MB. The modular language pack system implemented in Module 6A allows users to download only what they need, minimizing disk footprint and bandwidth.
