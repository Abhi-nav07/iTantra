# Final Device Matrix

## Overview
This matrix defines the lowest tested Android, RAM, and ABI profiles capable of running iTantra, based on the architectural constraints and unit test validations performed up to Module 8G. Due to environment limitations, full physical validation was substituted with simulated unit constraints where appropriate.

## Capability Tiers
- **CORE communication**: Wi-Fi/Bluetooth transport, Security (AES-GCM, SAS), Emergency SOS (AI-independent).
- **STANDARD AI**: Local STT, Text exchange, Local TTS (Monolingual).
- **FULL AI**: STT, Machine Translation (MT), TTS (Cross-lingual).

## Hardware Matrix
| Profile Parameter | Lowest Tested Threshold | Status / Evidence |
|-------------------|-------------------------|-------------------|
| **Android API**   | API 26 (Android 8.0) | `LEVEL 1 BUILD/UNIT VERIFIED` (Target is API 34). |
| **RAM**           | ~2GB | `NOT TESTED` (Physical device profiling unavailable in automated runner). |
| **32-bit (v7a)**  | N/A | `UNSUPPORTED` (Sherpa-ONNX targets 64-bit primarily for neural models in this project scope). |
| **64-bit (v8a)**  | Required | `LEVEL 1 BUILD/UNIT VERIFIED` |

## Subsystem Capability Mapping
| Tier | Minimum Required Profile | Status / Evidence |
|---|---|---|
| **CORE communication** | API 26 | `LEVEL 1 BUILD/UNIT VERIFIED` |
| **STANDARD AI** | API 26 + 64-bit + 2GB RAM | `MODEL MISSING` (App architecture is ready, but physical `.onnx` model files are missing from `assets/`). |
| **FULL AI** | API 26 + 64-bit + 3GB+ RAM | `MODEL MISSING` (App architecture is ready, but physical `.onnx` model files are missing from `assets/`). |

## Summary
The application compiles securely for API 26+. However, since the heavy neural model binaries are excluded from the repository, actual hardware benchmarking (Thermal, CPU, RAM) cannot be physically validated without sideloading the ~500MB+ models onto an actual device.
