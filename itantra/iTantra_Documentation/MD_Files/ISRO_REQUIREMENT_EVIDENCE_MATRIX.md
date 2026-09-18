# ISRO Requirement Evidence Matrix

## Overview
This document directly maps ISRO operational requirements to the implemented architectural features, identifying where verification has occurred and where physical hardware gaps (blockers) exist in the current automated pipeline.

| Requirement | Implementation File/Module | Evidence | Status | Blocker |
|-------------|----------------------------|----------|--------|---------|
| **10 Languages Support** | `RealLanguagePackRepository.kt`, `LanguageCatalog.kt` | Unit tests verify JSON manifest parsing. | `LEVEL 1 BUILD/UNIT VERIFIED` | `MODEL MISSING`: Physical `.onnx` models are excluded from the repository to save space. |
| **Offline STT/TTS** | `SherpaOnnxSpeechRecognizer.kt`, `SherpaOnnxTextToSpeech.kt` | Architecture is fully local. | `LEVEL 1 BUILD/UNIT VERIFIED` | `MODEL MISSING`: Cannot perform live STT/TTS benchmark without physical weights. |
| **Low Bitrate Transport** | `TransceiverCoordinator.kt` | Semantic packet metrics show ~75 bytes payload per voice message. | `LEVEL 1 BUILD/UNIT VERIFIED` | `NOT TESTED` in real-world physical transmission due to emulator limits. |
| **PTT Mode** | `TransceiverViewModel.kt`, `TransceiverScreen.kt` | State management is wired to `audioSource`. | `LEVEL 1 BUILD/UNIT VERIFIED` | Live microphone validation requires physical Android device. |
| **Continuous Mode** | `ContinuousListenEngine.kt` | Uses VAD windowing to segment speech. | `LEVEL 1 BUILD/UNIT VERIFIED` | Requires live microphone for true noise robustness testing. |
| **Bluetooth Support** | `BluetoothPeerTransport.kt` | Abstracted beneath `TransportCoordinator`. | `LEVEL 1 BUILD/UNIT VERIFIED` | Android Emulator does not natively support physical Bluetooth RFCOMM pairing. |
| **Wi-Fi Peer Support** | `WifiPeerTransport.kt` | Standard `java.net.Socket` integration. | `LEVEL 1 BUILD/UNIT VERIFIED` | Automated testing lacks two distinct LAN endpoints for live P2P testing. |
| **Alert/Emergency SOS** | `EmergencyPhraseResolver.kt`, `TransceiverCoordinator.kt` | Priority byte `0x02` prepended. Retries configured. Preemption handled. | `LEVEL 1 BUILD/UNIT VERIFIED` | Physical device testing absent. |
| **Low RAM/Storage** | `LanguagePackStorage.kt` | Models can be dynamically uninstalled. | `NOT TESTED` | RAM profiling cannot be reliably emulated on CI hardware. |
| **Low Latency** | `TransceiverCoordinator.kt` | Metric tracking implemented for STT/TTS/E2E latency. | `NOT MEASURED` | Measurements on a CI VM do not reflect ARM physical CPU limits. |
| **WER/Intelligibility** | Not directly implemented yet | N/A | `NOT MEASURED` | Requires extensive physical field testing with human speakers. |
