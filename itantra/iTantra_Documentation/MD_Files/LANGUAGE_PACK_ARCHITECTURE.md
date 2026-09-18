# iTantra Language Pack Architecture

## Overview
To support 10 offline Indic languages efficiently without bloating the base APK, iTantra uses a dynamic **Language Pack Storage** architecture. 

A Language Pack consists of:
- **STT (Speech-to-Text)** resources (ONNX weights, tokens)
- **TTS (Text-to-Speech)** resources (MMS/VITS ONNX weights, lexicon, tokens)

These are managed independently per language. A user can install STT without TTS, TTS without STT, or both, minimizing device storage consumption.

## Directory Structure
Packs are stored on disk in the application's private files directory, separated by language code:

```
app_data/
└── language_packs/
    ├── hi/
    │   ├── model.int8.onnx         (STT)
    │   ├── tokens.txt              (STT)
    │   ├── vits-mms-hin.onnx       (TTS)
    │   ├── lexicon.txt             (TTS)
    │   └── tts_tokens.txt          (TTS)
    ├── en/
    └── kn/
```

## State Management

### 1. Persistent Storage State (`LanguagePackInstallState`)
Defined in `LanguagePackState.kt`, it tracks the on-disk readiness of a language's components.
Because STT and TTS are independent, the `LanguagePackSummary` tracks two states per language:
- `sttInstallState`
- `ttsInstallState`

States include: `NOT_INSTALLED`, `DOWNLOADING`, `INSTALLING`, `INSTALLED`, `UPDATE_AVAILABLE`, `CORRUPTED`, `ERROR`.

### 2. Live Session State (`LanguageSessionState`)
Defined in `ActiveLanguageSessionManager.kt`, it enforces the **One Active Language Rule**: Only one language's heavy STT/TTS engine assets can reside in memory at a time.
States include: `IDLE`, `LOADING_STT`, `LOADING_TTS`, `READY`, `ERROR`.

## Fallback Gracefulness
If a user switches to a language that only has TTS installed:
- Received voice messages (if they somehow arrive) will be synthesized.
- Attempting to send a voice message will result in a local error: `STT Pack Required`.
- The Transceiver gracefully suppresses features tied to missing dependencies instead of crashing.

## Protocol Transparency
Over Bluetooth RFCOMM, the STT/TTS readiness of each peer is exchanged via `CAPABILITIES` packet, using bitmasks. This enables the UI to eventually show the user if their remote peer lacks the TTS required to hear their messages.
