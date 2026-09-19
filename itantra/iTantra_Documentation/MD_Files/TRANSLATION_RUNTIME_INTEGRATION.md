# Module 7B: Translation Runtime Integration

This document outlines the real offline translation integration as implemented for iTantra, using native CTranslate2 inference via JNI.

## Runtime Selection
- **Library**: CTranslate2 (C++ native, cross-compiled for ARM64-v8a via CMake)
- **Tokenizer**: SentencePiece (C++ native, cross-compiled for ARM64-v8a)
- **JNI Bridge**: `itantra_mt_jni.cpp` exposes `nativeCreateEngine`, `nativeTranslate`, `nativeDestroyEngine`
- **Justification**: CTranslate2 provides optimized auto-regressive inference for Transformer models with efficient INT8 quantization. Native C++ execution avoids Java/ONNX overhead and provides direct access to the CTranslate2 generation API.

## Translation Architecture
The `CTranslate2TranslationEngine` manages two directional models:
1. **indic-en**: Translates any of 10 Indic languages to English
2. **en-indic**: Translates English to any of 10 Indic languages

### Input Token Structure
Tokens are constructed as: `[SOURCE_LANG_TAG, TARGET_LANG_TAG, *SP_TOKENS]`
- Source/target tags use exact FLORES codes: `hin_Deva`, `eng_Latn`, `ben_Beng`, `guj_Gujr`, `mar_Deva`, `kan_Knda`, `mal_Mlym`, `tam_Taml`, `tel_Telu`, `ory_Orya`

### Translation Flow
1. **Pre-processing**: FLORES language tag prepended based on source and target language
2. **Tokenization**: SentencePiece model segments input text into subword tokens
3. **Inference**: CTranslate2 `Translator::translate_batch()` runs auto-regressive generation
4. **Detokenization**: SentencePiece decodes output tokens back to Unicode text

### Indic-to-Indic Pivot
For Indic↔Indic pairs (e.g., Hindi→Tamil):
1. First pass: indic-en model translates Hindi → English
2. Second pass: en-indic model translates English → Tamil
3. Both models must be loaded (`indicEnReady && enIndicReady`)

## Same-Language Bypass
When `sourceLang == targetLang`, the engine immediately returns the input text without invoking any model (0ms MT latency).

## Failure Handling
- If models are not loaded, the engine returns `MODEL_NOT_INSTALLED` state
- If translation fails at runtime, the original source text is passed through to TTS to ensure resilient communication

## Model Files
Models are provisioned via `tools/provision_models.py` and stored at:
- `mt/indic-en/model.bin`, `config.json`, `source_vocabulary.json`, `target_vocabulary.json`, `vocab/model.SRC`, `vocab/model.TGT`
- `mt/en-indic/` (symmetric structure)
