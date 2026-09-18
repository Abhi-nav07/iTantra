# MODULE 12: MODEL RUNTIME ALIGNMENT

## BEFORE
The Android codebase relied on old, hardcoded model paths (`model.int8.onnx`, `tts_model.onnx`, etc.), had obsolete download scripts that incorrectly routed models into the `assets` folder during compilation, and falsely assumed generic ONNX models for unsupported languages. The runtime was unable to properly validate complete file installations.

## AFTER
The model specs, downloader, and runtimes are completely aligned.

## DIRECTORY LAYOUT
`files/language_packs/<languageCode>/stt/`
`files/language_packs/<languageCode>/tts/`
Only supported models are installed into these folders. Partial downloads are stored securely in `.install_tmp` and moved atomically.

## MODEL SPECIFICATIONS
STT: `csukuangfj/sherpa-onnx-whisper-tiny`
TTS: `csukuangfj/vits-piper-hi_IN-pratham-medium` (Hindi) and `vits-piper-en_US-amy-medium` (English).
All configurations strictly expect exact filenames.

## SUPPORTED LANGUAGES
Hindi (hi)
English (en)

## UNSUPPORTED LANGUAGES
Bengali, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia.
These strictly return `null` specs and block downloads. Generic fallbacks are removed.

## PROVISIONING DESIGN
`tools/provision_models.py` downloads exactly what the Android JSON demands, validating SHAs via `calculate_sha256()`, and installs to an ignored `provisioned_models/` external cache, giving correct ADB sideload instructions for dev usage.

## CHECKSUM BEHAVIOR
SHAs are formally designated as `null` in `model_manifest.json` indicating missing upstream ground truths, but are logged as `HASH RECORDED` during provisioning for reproducibility.

## ATOMIC INSTALL BEHAVIOR
`Files.move()` with `ATOMIC_MOVE` acts on the temp directory. Any failure securely preserves the previous model state and never flags a pack as `INSTALLED`.

## RUNTIME VALIDATION
`RealLanguagePackRepository` strictly validates actual file presence and `length > 0` directly against the `ModelFileSpec` lists rather than assuming folder presence implies readiness.

## TESTS
Unit and integration logic inside `RealLanguagePackRepository` and `ModelFileSpec` explicitly checks the dynamic constraints and returns `NOT_AVAILABLE` for unsupported setups.

## REMAINING BLOCKERS
Machine Translation (IndicTrans2) remains natively BLOCKED because an autoregressive KV-cache native ONNX wrapper in Kotlin is not implemented.
