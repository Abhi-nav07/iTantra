# iTantra Multilingual STT Accuracy & TTS Intelligibility Matrix

This matrix documents speech recognition (STT) accuracy and speech synthesis (TTS) intelligibility benchmarks across all 10 supported languages in iTantra.

> **Truthful Evidence Policy**  
> iTantra follows strict, non-fabricated reporting:
> - No synthetic WER or MOS values are manufactured.
> - Reference review status is transparently reported (`REVIEW_REQUIRED` for generated reference text until reviewed by fluent human speakers).
> - Device execution requires a physical Android target. When no device is attached (`adb devices` = empty), status is recorded as `NOT_TESTED` or `DEVICE_REQUIRED`.
> - Human evaluation for TTS requires direct listener scoring. Unreviewed items are marked `NOT_TESTED`.
> - Evidence Levels: `SOURCE_ONLY` (corpus and harness verified in source/unit tests), `HOST_TESTED` (executed on host runtime), `DEVICE_TESTED` (executed on physical device), `HUMAN_REVIEWED` (human evaluation completed).

---

## 1. 10-Language Accuracy & Intelligibility Evidence Table

| Language | Reference Review | STT Runtime Load | STT Utterances Tested | Corpus WER | Critical Exact Match % | TTS Runtime Load | TTS Sentences Tested | TTS Intelligibility % | Naturalness | Pronunciation | Evidence Level |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **hi** (Hindi) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **en** (English) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **bn** (Bengali) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **gu** (Gujarati) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **mr** (Marathi) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **kn** (Kannada) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **ml** (Malayalam) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **ta** (Tamil) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **te** (Telugu) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |
| **or** (Odia) | REVIEW_REQUIRED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | DEVICE_REQUIRED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | SOURCE_ONLY |

---

## 2. Benchmark Corpus Specifications

Every language package contains a verified JSON benchmark corpus located in `app/src/main/assets/benchmark/<lang>_benchmark.json`:

- **Total Utterances Per Language:** 24 utterances (16 general conversation/emergency/numbers/instructions + 8 critical sentences = 4 semantic opposite pairs per language).
- **Total Corpus Size:** 240 STT benchmark utterances across all 10 languages (80 critical sentences = 40 semantic opposite pairs total).
- **Reference Review:** All non-human-verified corpora are explicitly marked `REVIEW_REQUIRED`.
- **Categories Covered:**
  1. Greetings & Salutations
  2. Numbers & Counts
  3. Place Names & Coordinates
  4. Emergency Phrases
  5. General Conversation
  6. Field Operations & Instructions
  7. Tactical Commands
  8. Critical Semantic Opposites (4 pairs per language, e.g., "Evacuate immediately" vs "Do not evacuate", "Road is open" vs "Road is blocked", "Patient is conscious" vs "Patient is unconscious", "Fire is controlled" vs "Fire is not controlled").

### Critical Semantic Opposites
The dedicated critical-phrase subset evaluates high-risk substitutions where a single misrecognized word completely inverts meaning (8 critical sentences = 4 opposite pairs per language, 80 critical sentences = 40 pairs total). Tracked metric: `CRITICAL_PHRASE_EXACT_MATCH_RATE`.

---

## 3. Metric Calculations

### Corpus Word Error Rate (WER)
Calculated via Levenshtein edit distance at word level across the entire corpus:
$$\text{Corpus WER} = \frac{\sum \text{Substitutions} + \sum \text{Deletions} + \sum \text{Insertions}}{\sum \text{Reference Words}}$$
*Note:* Percentages are never averaged across sentences to compute corpus WER; edit operations are pooled across all reference tokens.

### Real-Time Factor (RTF)
$$\text{RTF}_{\text{TTS}} = \frac{\text{Synthesis Compute Time (ms)}}{\text{Generated Audio Duration (ms)}}$$
$$\text{RTF}_{\text{STT}} = \frac{\text{Processing Time (ms)}}{\text{Audio Duration (ms)}}$$

### STT Latency
Measured from audio endpointing (`finalizeUtterance()`) to final recognized string return:
$$\text{Endpoint-to-Final-Text Latency} = T_{\text{return}} - T_{\text{finalize}}$$

---

## 4. TTS Human Intelligibility Test Harness

Located in `app/src/main/assets/benchmark/tts_sentences.json`, the test harness provides 10 evaluation sentences per language (100 total sentences) testing phoneme coverage, numbers, compound words, and tactical domain terminology.

The in-app **TTS Human Intelligibility Evaluation** UI allows evaluators to:
1. Select any of the 10 target languages.
2. Synthesize real test sentences using the on-device VITS model.
3. Listen to the generated PCM audio playback via device speaker.
4. Record human ratings:
   - **Intelligible:** YES / NO
   - **Naturalness:** 1 to 5 Likert scale
   - **Pronunciation:** 1 to 5 Likert scale
   - **Comments:** Free-text phoneme/accent feedback
5. Store full session evaluation JSON locally.

Technical metrics recorded during synthesis:
- Synthesis compute duration (ms)
- Generated PCM audio duration (ms)
- Sample rate (Hz)
- Non-empty PCM sample validation
- RTF
