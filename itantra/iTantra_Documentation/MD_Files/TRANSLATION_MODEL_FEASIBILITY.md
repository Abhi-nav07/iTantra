# Module 7A: Offline Translation Model Feasibility

## Objective
Evaluate the feasibility of running machine translation models (specifically IndicTrans2 or similar lightweight ONNX models) entirely offline on low-end Android hardware, keeping in mind the existing memory pressure from STT and TTS models.

## Current Memory Constraints
- **STT (Sherpa-ONNX)**: ~40MB to 150MB RAM depending on the quantized Transducer/Conformer model.
- **TTS (Sherpa-ONNX/VITS)**: ~30MB to 100MB RAM.
- **System Overhead**: ~50MB.
- **Total Existing Pipeline**: ~120MB to 300MB.
- **Target Low-End Device RAM**: 2GB to 3GB total system RAM (leaving ~500MB max for the app).

## Translation Model Requirements
1. **Size**: The translation model must fit within the remaining ~200MB budget.
2. **Format**: Must be ONNX compatible to leverage existing C++ inference libraries without introducing heavy Python/PyTorch dependencies.
3. **Quantization**: INT8 quantization is mandatory for mobile deployment.

## IndicTrans2 Feasibility
IndicTrans2 provides state-of-the-art accuracy for Indian languages.
- **Original Model**: The baseline IndicTrans2 model is >1GB, which is infeasible for active RAM alongside STT/TTS on low-end hardware.
- **Distilled/Quantized Variants**: To run on mobile, we must use aggressively pruned, distilled, and INT8 quantized ONNX variants of IndicTrans2 (or fallback to an older, lighter model like OPUS-MT for specific language pairs).

### Pivot vs Direct Routing
IndicTrans2 typically comes in direction-specific models (e.g., Indic-to-English, English-to-Indic, Indic-to-Indic).
- **Direct Indic-Indic**: Most accurate, requires one model. If a highly quantized Indic-Indic model can fit in <150MB, this is ideal.
- **Pivot (Indic -> English -> Indic)**: Requires two translation passes and loading two models (or swapping them). This significantly increases latency and memory churn.

**Recommendation for iTantra**: We must prioritize **Direct Indic-Indic** models for offline use cases due to latency constraints (half-duplex PTT requires fast turnaround) and memory constraints (loading two models simultaneously is risky).

## Fallback Strategy
If the translation model fails to load or inference times out (>2000ms), the transceiver will bypass the translation layer and output the original text. Same-language nodes will always bypass this layer entirely.
