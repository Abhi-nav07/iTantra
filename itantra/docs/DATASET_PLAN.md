# Dataset Plan

## Architecture & Collection Strategy
DO NOT collect or train datasets inside Antigravity/development environments. All audio recordings must be manually recorded on physical target devices (Android phones) in realistic operational environments to capture true acoustic characteristics (mic array artifacts, ambient noise, encoding loss).

## Structure
The real dataset repository will be structured as follows:

```
dataset/
├── audio/
│   ├── general/
│   ├── emergency/
│   ├── medical/
│   ├── disaster/
│   ├── road_navigation/
│   ├── numbers/
│   ├── coordinates/
│   ├── locations/
│   └── technical/
└── metadata.csv
```

## Metadata Schema
Every `.wav` or `.pcm` file must have an entry in `metadata.csv` capturing:
- **audio_path**: Relative path to the audio file
- **transcription**: Raw, unnormalized transcript of the speech
- **language**: Language Code (e.g., `hi`, `en`)
- **speaker_ID**: Unique identifier for the human speaker
- **recording_environment**: Brief description (e.g., "indoors_quiet", "outdoors_windy", "vehicle_moving")
- **device_microphone**: Specific hardware used (e.g., "Pixel 7 Pro bottom mic", "Samsung S22 Ultra")
- **category**: Domain tag matching the folder structure (e.g., "emergency", "numbers")

## Data Split Rule
- **TRAIN**: 80% of data.
- **VALIDATION**: 10% of data (used during epoch tuning).
- **TEST**: 10% of data (withheld completely from training/validation).
- **CRITICAL RULE**: TEST recordings/speakers MUST NEVER be used during training. The split should ideally be **speaker-disjoint** (i.e. speakers in the TEST set do not appear in the TRAIN set) to prove generalization to unseen voices.

## Immediate User Tasks (Baseline)
For the immediate 6B-1 baseline validation, the tester must provide 10–20 manually recorded utterances encompassing the diverse categories above (e.g. "मुख्य सड़क बंद है।"). 
*Do not generate artificial audio via TTS to serve as human baseline data.*
