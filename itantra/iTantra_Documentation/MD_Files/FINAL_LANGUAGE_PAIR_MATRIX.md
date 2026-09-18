# Final Language Pair Matrix

## Overview
This matrix represents the physical capability of the system to route and translate between the 10 target languages.

**Allowed Statuses (Per Prompt constraints):**
- `BYPASS VERIFIED`
- `ROUTE SUPPORTED`
- `MODEL MISSING`
- `QUALITY MEASURED`
- `AVD TWO-PEER VERIFIED`
- `PHYSICAL TWO-PEER VERIFIED`
- `NOT TESTED`
- `UNSUPPORTED`

## The Matrix (Source \ Target)

| Source \ Target | hi | en | bn | gu | mr | kn | ml | ta | te | or |
|-----------------|---|---|---|---|---|---|---|---|---|---|
| **hi** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **en** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **bn** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **gu** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **mr** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **kn** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **ml** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **ta** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **te** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |
| **or** | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` | `MODEL MISSING` |

## Explanation
Although the `TranslationRouter` is logically capable of handling any pair via `ROUTE SUPPORTED` and same-language endpoints are logically `BYPASS VERIFIED`, the physical validation rule mandates that if real model files do not exist, we must mark it as `MODEL MISSING`. Since the repository contains only JSON manifests and lacks the actual multi-megabyte `.onnx` model weights for all 10 languages, every pair is physically impossible to execute offline without further provisioning. Thus, the truthful classification for the entire matrix is `MODEL MISSING`.
