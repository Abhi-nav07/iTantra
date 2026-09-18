# iTantra - Comprehensive Project Status & Engineering Report
**Date:** September 18, 2026
**Target:** Smart India Hackathon (SIH) 2026, Problem Statement 173 (ISRO)
**Classification:** FINAL PROTOTYPE PARTIAL (Module 11 - Final Freeze)

---

## 1. Executive Summary

The iTantra project aims to deliver an offline, multilingual, neural voice transceiver capable of facilitating secure, peer-to-peer (P2P) voice communication across language barriers without internet infrastructure. This repository represents the Android client implementation designed for extreme field conditions.

As of the Module 11 architecture freeze, the system features a robust, structurally verified foundation. The user interface, offline Voice Activity Detection (VAD), secure cryptographic exchange layer, and semantic data packaging systems are fully implemented and verified via unit and integration tests.

However, the final "Golden Path" (End-to-End Voice Translation) remains **partially blocked** due to profound limitations in deploying large autoregressive Machine Translation (MT) models (IndicTrans2) natively onto low-end Android hardware without a custom-built C++ Key-Value (KV) cache inference wrapper. Consequently, while the pipeline routes correctly, true localized cross-language translation is currently non-functional.

This report documents every system engineered, every limitation encountered, the immediate next steps required, and strict guidelines for the ISRO evaluation presentation.

---

## 2. Comprehensive Breakdown of Engineered Systems (What Has Been Done)

The development was systematically tackled across 11 modules. The following subsystems have been completely engineered and syntactically verified.

### 2.1 UI/UX and Application Architecture (Modules 1 - 4)
The application avoids modern consumer aesthetics (bright gradients, white space) in favor of a "FAST, SERIOUS, TECHNICAL, TRUSTWORTHY, FIELD-READY" design language.
*   **Design System:** Implemented a unified dark-mode Jetpack Compose architecture using near-black backgrounds, high-contrast signal green, warning amber, and critical red accents. 
*   **Navigation & Screens:**
    *   `ConnectScreen`: Handles peer discovery and pairing initialization.
    *   `TransceiverScreen`: The main operational hub featuring dual input modes.
    *   `DiagnosticsScreen`: Exposes real-time internal telemetry (memory, active models, secure bytes transmitted, connection status) for transparency.
    *   `LanguagePacksScreen`: Interface for managing offline neural model downloads.
*   **Interaction Paradigms:** 
    *   Injected `LocalHapticFeedback` to provide tactile confirmation for critical events (PTT button press, emergency broadcast confirmation).
    *   Implemented clear, non-blocking visual states (`LISTENING`, `SPEECH_DETECTED`, `SEGMENT_READY`) to give the user absolute confidence in the system's state.

### 2.2 Voice Activity Detection & Audio Pipeline (Module 5A)
A critical requirement was enabling hands-free "Continuous Listening" without sacrificing the reliability of manual Push-To-Talk (PTT).
*   **Silero VAD Integration:** Integrated the highly efficient `silero_vad.onnx` model via the `sherpa-onnx` framework.
*   **ContinuousListenEngine:** Built a state machine (`ContinuousListenEngine.kt`) that continuously polls the microphone, utilizing VAD to slice audio streams into processable utterances.
*   **Acoustic Tuning:** Tuned VAD parameters specifically for short, single-word confirmations common in field ops:
    *   `minSpeechDuration`: 150ms (captures short words like "हाँ" / "Yes").
    *   `minSilenceDuration`: 700ms (prevents fragmenting a single sentence due to a natural pause).
*   **Echo-Trigger Suppression:** Upgraded the `TransceiverCoordinator` to strictly pause (`stop()`) the VAD microphone polling while the local device speaker is active (via `KokoroTtsEngine`). This completely prevents the system from triggering on its own generated speech.

### 2.3 Cryptography and Security Layer (Modules 8 - 9)
Given the potential deployment by ISRO and emergency responders, security could not be compromised.
*   **Elliptic-Curve Diffie-Hellman (ECDH):** Implemented in `SecureSessionManager` to generate secure, ephemeral shared secrets between two offline devices without transmitting private keys.
*   **HMAC-based Key Derivation Function (HKDF):** Used to derive strong cryptographic keys from the ECDH shared secret.
*   **AES-256-GCM:** All outgoing semantic payloads are encrypted using Authenticated Encryption with Associated Data (AEAD), ensuring complete confidentiality and integrity.
*   **Replay Protection:** Engineered monotonic message counters and cryptographic nonces. If an adversary captures a packet and attempts to replay it 5 minutes later to trigger a false emergency, the `PacketDecoder` mathematically rejects it.
*   **Short Authentication String (SAS):** Generated a 4-character visual SAS that users must manually verify out-of-band to prevent Man-in-the-Middle (MitM) attacks during initial pairing.

### 2.4 Semantic Framing and Networking (Module 7)
Instead of streaming raw, heavy, unencrypted audio over weak Bluetooth links, the system converts speech to text, packages it semantically, encrypts it, and reconstructs it on the receiving device.
*   **ItantraPacket Structure:** Designed a lightweight binary protocol that encodes:
    *   Source and Target Language IDs.
    *   Priority Flags (`NORMAL`, `HIGH`, `CRITICAL`).
    *   Message Type (`TEXT`, `AUDIO`, `EMERGENCY_BROADCAST`, `ACK`, `HUMAN_ACK`).
    *   The encrypted payload.
*   **TransportCoordinator:** Abstracts the underlying network hardware away from the UI and Audio engines. 
*   **Scaffolded Transports:** Created `BluetoothPeerTransport` and `WifiPeerTransport` interfaces capable of parsing the `ItantraPacket` streams.

### 2.5 Model Provisioning and Repository Hygiene (Modules 10 - 11)
To ensure reproducible deployments without bloating the Git repository with gigabytes of model weights:
*   **Secure Provisioning Script (`tools/provision_models.py`):** Rewrote the python deployment script to fetch *exact, verified* models directly from Hugging Face.
    *   **STT:** Multilingual `csukuangfj/sherpa-onnx-whisper-tiny` (Apache 2.0).
    *   **TTS:** `csukuangfj/vits-piper-hi_IN-pratham-medium` (Hindi) and `vits-piper-en_US-amy-medium` (English).
*   **Atomic Installations:** The script downloads models to a temporary directory and uses atomic copies to prevent corrupted or partially-downloaded models from ever being marked as `READY` by the Android app.
*   **Documentation Audit:** Consolidated all architecture files, requirement matrices, and module reports into `iTantra_Documentation/`, purging obsolete `.zip` and `.apk` files to free up 780 MB of repository space.

---

## 3. What is Currently Missing or Blocked (Known Limitations)

The following components represent the genuine engineering blockers that prevented a 100% functional prototype.

### 3.1 Machine Translation (IndicTrans2) is Natively BLOCKED
*   **The Architecture Problem:** The core requirement was localized, offline translation between 10 Indic languages. The selected state-of-the-art model for this is AI4Bharat's IndicTrans2.
*   **The Engineering Blocker:** IndicTrans2 is a massive Transformer model. While ONNX exports exist, deploying an autoregressive encoder-decoder model on mobile requires a specialized inference loop. At every step of generating a translated word, the model must feed its previous state (KV-cache) back into itself.
*   **The Reality:** Standard ONNX Runtime (ORT) in Kotlin does not support this out-of-the-box without immense memory overhead or custom C++ operators. Because we lacked a dedicated C++ wrapper (like Sherpa-ONNX provides for STT/TTS), `RealTranslationEngine.kt` is currently forced to throw an `UnsupportedOperationException`.
*   **Result:** The translation step in the "Golden Path" is currently bypassed/blocked.

### 3.2 Physical Hardware Validation is MISSING
*   **The Reality:** Throughout the 11 modules, compilation, architectural logic, and unit tests (like `SecureSessionManagerTest` and `PacketTest`) passed perfectly in synthetic environments.
*   **The Blocker:** We have not deployed this APK to two physical Android devices in the same room. 
*   **Impact:** 
    *   We do not know the true Word Error Rate (WER) of the Whisper-Tiny model in a windy field.
    *   We do not know the true end-to-end latency (E2E) of the hardware Bluetooth chips handshaking.
    *   We do not know if the Android OS will violently throttle the CPU when running VITS TTS and Whisper STT simultaneously.

### 3.3 Full 10-Language Expansion is MISSING
*   **The Reality:** We verified the provision paths for Hindi (hi) and English (en).
*   **The Blocker:** The remaining 8 languages (Bengali, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia) lack provisioned models in the Python script. Expanding to 10 languages before proving the Golden Path (Hi-En) would have been an irresponsible use of engineering resources.

---

## 4. What To Do Next (Future Roadmap)

To evolve this from a "Partial Prototype" to a "Production-Ready Field Device", the following roadmap must be strictly adhered to:

### Phase 1: Unblock Machine Translation
*   **Option A (Pivot):** Abandon IndicTrans2 for a smaller, older model (like MarianMT/Helsinki-NLP) that has established, lightweight mobile wrappers, sacrificing linguistic accuracy for functional execution.
*   **Option B (Engineering):** Dedicate a sprint to writing a JNI (Java Native Interface) layer in C++ that specifically handles the IndicTrans2 KV-cache state looping using ONNX Runtime C++ APIs, heavily optimized for ARM64 architectures.

### Phase 2: Physical Device Field Testing
*   **Procurement:** Obtain two low-end Android devices (e.g., standard issue government/military spec smartphones).
*   **Acoustic Loop Testing:** Place the devices in proximity, trigger TTS, and ensure the local microphone's VAD does not pick up the speaker output (verifying the `TransceiverCoordinator` suppression logic).
*   **Network Throttling:** Move devices to the absolute edge of Bluetooth range and verify that the `ItantraPacket` framing logic gracefully handles dropped bytes and reconnects securely without breaking the AES-GCM nonce sequence.

### Phase 3: Language Expansion
*   Once MT is unblocked and physical E2E latency is measured under 3.5 seconds, scale `provision_models.py` to ingest the remaining VITS Piper and STT language tokens.

---

## 5. Strict Guidelines for ISRO / SIH Evaluation (What NOT To Do)

During the final presentation to ISRO evaluators or SIH judges, absolute engineering honesty is paramount. Falsifying capabilities in a safety-critical communication system invalidates the entire project.

1.  **DO NOT Fake the Translation:** Do not hardcode English-to-Hindi string maps to "prove" the system works. Explicitly state: *"The STT, TTS, Security, and Transport layers are functional. Machine Translation requires a C++ JNI wrapper for local KV-caching which exceeded the hackathon time constraints."*
2.  **DO NOT Fake Performance Metrics:** If asked about Latency, RAM usage, or Word Error Rate (WER), state clearly that synthetic measurements are meaningless and physical field-testing was outside the current scope. Do not invent numbers like "98% accuracy".
3.  **DO NOT Present 10 Languages as Functional:** State that the architecture is explicitly built to *support* 10 languages (via the `LanguageCode` enums and `LanguagePackManifest`), but only English and Hindi have been scaffolded for the immediate prototype.
4.  **DO Focus on the Security and Architecture:** You have built a highly secure, modular, offline communication protocol. Emphasize the ECDH/AES-GCM implementations, the monotonic replay protection, and the semantic framing that reduces heavy audio streams to a few encrypted kilobytes. **This is your strongest differentiator.** 

---
*End of Report*
