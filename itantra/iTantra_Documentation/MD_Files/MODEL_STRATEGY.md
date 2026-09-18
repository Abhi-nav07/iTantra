# iTantra Model Strategy

## Overview
iTantra utilizes a modular language pack system that downloads only the specific Neural STT/TTS assets a user needs to conserve base APK bandwidth and local disk space. All inference runs directly on the edge hardware.

## Current Hindi Candidate
- **Model**: `parismitaglobalsolutions/indicconformer-sherpa-onnx`
- **Lineage**: Community ONNX export derived from AI4Bharat's IndicConformer.
- **Quantization**: `int8` (chosen for optimal RAM/speed trade-offs on mobile processors).
- **Format**: `.onnx` bundled for the `sherpa-onnx` runtime ecosystem.

## Performance Profile
- **Storage Profile**: ~197MB footprint per language.
- **Memory Footprint**: ~250MB active RAM mapping during execution.
- **Execution Lifecycle**: Assets are aggressively unloaded from system RAM immediately upon switching active transceiver languages. The architecture guarantees a maximum footprint of a single language pack in active memory.

## Future Path
Subsequent regional dialect support (Bengali, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia) will follow the identical integration pathway—leveraging parallel `int8` exports deployed on the same unified `sherpa-onnx` C++ engine to drastically limit code complexity and native `.so` bloat.
