# Module 7B: Translation Runtime Integration

This document outlines the real offline translation integration as implemented for iTantra, prioritizing ONNX Runtime for Seq2Seq architectures.

## Runtime Selection
- **Library**: `com.microsoft.onnxruntime:onnxruntime-android:1.17.1`
- **Tokenizer Support**: `com.microsoft.onnxruntime:onnxruntime-extensions-android:1.17.1` (Optional for native tokenization, stubbed in current architecture).
- **Justification**: Sherpa-ONNX is highly optimized for speech (Transducer/Conformer) and TTS (VITS), but does not expose a generalized Seq2Seq auto-regressive generation loop necessary for Neural Machine Translation (like IndicTrans2). A dedicated ONNX Runtime dependency allows manual encoder/decoder lifecycle management.

## Real Generation Loop Architecture
The `RealTranslationEngine` provides a structural baseline for actual neural machine translation without relying on mock outputs:
1. **Pre-processing**: Prepends BCP-47 / model-specific language tags to the input string based on the `TranslationRouter` resolution.
2. **Tokenization**: Uses an ONNX session wrapper (typically SentencePiece via ORT extensions) to convert Unicode to `input_ids`.
3. **Encoder Execution**: Passes `input_ids` to `encoder_model.onnx` to generate `encoder_hidden_states`.
4. **Decoder Loop (Auto-Regressive)**: 
   - Initializes a `decoder_input_ids` array with the `BOS` token.
   - Feeds the `encoder_hidden_states` and previous tokens into `decoder_model.onnx`.
   - Generates the next token using greedy search (`argmax`).
   - Appends the generated token and repeats until the `EOS` token (e.g., `2L`) or `max_tokens` (128) is reached.
5. **Detokenization**: Translates the generated IDs back into the target Unicode text.

## Same-Language Bypass & Interception
The `TranslationRouter` sits inside the `TransceiverCoordinator`. When a `TEXT` packet is decrypted:
1. The packet's `languageCode` is extracted.
2. If it exactly matches the local `ActiveLanguageSessionManager`'s current active language, the MT engine is completely bypassed (0ms MT latency).
3. If they differ, the text is routed to the MT engine.
4. If the MT engine fails (missing files, OOM, timeout), the router catches the exception and immediately returns the original text to the TTS queue to ensure resilient communication.

## Offline Provisioning Requirement
Multi-hundred MB ONNX models are strictly excluded from the APK and Git repository. The engine expects models to be located in `context.filesDir/translation_models`. If these files do not exist at runtime, the `TranslationRouter` falls back to returning the original source text (state: `MODEL_NOT_INSTALLED`).
