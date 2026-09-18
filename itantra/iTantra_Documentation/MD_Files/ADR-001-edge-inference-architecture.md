# ADR-001: Edge Inference Architecture

**Status:** Accepted
**Date:** Task 01
**Deciders:** iTantra engineering (SIH 2026, PS 173)

## Context

iTantra must work as an emergency/field communication tool with no
guaranteed internet or telecom connectivity, on low/mid-range Android
hardware, across 10 Indian languages, within a 4-day hackathon build
window. This ADR records the foundational architectural decisions made in
Task 01 and why.

## Decision 1: Offline on-device inference only

STT and TTS both run entirely on-device. No cloud STT/TTS API (Google
Speech, OpenAI, Gemini, or otherwise) is used or planned.

**Why:** The product's core value proposition is working without network
connectivity (disaster/field scenarios). A cloud dependency would defeat
the purpose even if it improved quality or reduced initial engineering
effort.

**Consequence:** All model choices are constrained by what can run within
a phone's CPU/RAM/storage budget, ruling out large models as published
(see `docs/MODEL_STRATEGY.md`).

## Decision 2: Modular, downloadable language packs (not bundled)

Language assets are represented as versioned `LanguagePackManifest`
entries and installed independently, rather than shipping all 10
languages' models inside the APK.

**Why:** Bundling all 10 languages' STT+TTS assets would make the app
unshippable in size for low-end devices, and most users only need 1-2
languages at a time. A modular pack system lets storage scale with actual
usage.

**Consequence:** The app needs real download/install/checksum
infrastructure eventually (not built in Task 01 — only the manifest
schema and repository contract exist so far).

## Decision 3: Exactly one active language's inference assets in memory

`ActiveLanguageSessionManager` enforces that switching the active
language fully unloads the previous language's STT+TTS engines before
loading the new language's engines. Multiple packs may be *installed* on
disk simultaneously; at most one may be *active* in memory.

**Why:** Low/mid-range Android devices cannot hold multiple languages'
STT+TTS models resident in RAM simultaneously without risking OOM kills
or unacceptable memory pressure on other apps. This constraint is a first-
class architectural rule, not an optimization to add later.

**Consequence:** Language switching has a load-time cost (model load
time) that must be measured and shown to the user/judges honestly via the
metrics framework, rather than hidden or hand-waved.

## Decision 4: Quantized/mobile-friendly inference as a future requirement

No specific model or quantization scheme is finalized yet, but every
candidate evaluated (see `docs/MODEL_STRATEGY.md`) is judged against
whether it can reasonably reach a quantized, mobile-deployable form —
full-precision research checkpoints are explicitly treated as references/
teachers, not deployment targets.

**Why:** SIH judging and real field usability both depend on the app
actually running acceptably on low/mid-range hardware, not just on
demo-day hardware with excess headroom.

**Consequence:** `ModelAssetInfo` in the manifest schema already carries
a `quantization` field, and `ActiveLanguageSessionManager` design assumes
engines are cheap enough to load/unload on a language switch — an
assumption that must be validated once a real model is chosen.

## Decision 5: Measurable performance instead of subjective "fast" claims

Every performance figure in the app (`InferenceMetrics`,
`TransmissionMetrics`) is either a real, timestamped measurement or the
literal value "N/A" — via the `Measurement<T>` sealed type. No metric is
ever a fabricated or estimated placeholder number.

**Why:** SIH judging explicitly rewards measurable efficiency claims over
subjective ones, and a wrong or fabricated number is worse than an honest
"not yet measured" — it actively misleads evaluators and future
engineering decisions (e.g. "is Tamil switch time acceptable?" cannot be
answered from a made-up figure).

**Consequence:** The diagnostics screen will show mostly "N/A" until real
STT/TTS/transport implementations call into `MetricsRecorder` — this is
expected and correct for Task 01, not a bug to silently patch with fake
numbers.

## Alternatives considered

- **Bundling 1-2 default languages in the APK and downloading the rest**
  was considered as a middle ground, and is reflected in the mock
  repository's initial state (Hindi + English pre-"installed") as a
  plausible real-world default — but no APK-bundling decision has
  actually been made or implemented; the mock's initial state is for UI
  demonstration only.
- **A DI framework (Hilt) for the dependency graph** was considered and
  rejected for Task 01's scope — see `app/AppGraph.kt`'s doc comment. Not
  a permanent decision; revisit if the app grows past a single module.
