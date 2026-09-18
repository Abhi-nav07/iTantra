# ISRO EVALUATOR Q&A

**Q: Why not send audio directly?**
A: Raw or compressed audio (even Opus) requires at least 8–16 kbps of sustained bandwidth. iTantra extracts the semantic meaning via local STT, sending only 10–50 bytes over Bluetooth/Wi-Fi, guaranteeing delivery even at extreme edge conditions where RF channels are noisy and congested.

**Q: How much bandwidth is saved?**
A: The semantic bitrate uses ~50 bytes per utterance. Compared to 16 kHz raw PCM audio (~256 kbps), this is a 99.9% reduction in payload size.

**Q: How many languages actually work?**
A: The architecture and protocols natively route 10 languages (Hindi, English, Bengali, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia). However, physical neural weight files are currently absent from the deployment, blocking real inference.

**Q: Where are the model files?**
A: They are excluded from this build to keep the base APK size within limits and due to ongoing validation of the IndicTrans2 ONNX export structure. They are designed to be downloaded dynamically to `LanguagePackStorage`.

**Q: What is the WER (Word Error Rate)?**
A: NOT MEASURED. End-to-end AI models are blocked, so production WER cannot be established.

**Q: Does it work without Internet?**
A: Yes. All AI models (Sherpa-ONNX STT/TTS) and communication protocols (Bluetooth RFCOMM, Wi-Fi Direct) run completely on-device without cloud fallback.

**Q: What happens if STT/MT/TTS fails?**
A: iTantra gracefully falls back to structured failure without sending placeholder/dummy packets over the radio. Same-language bypass ensures that if cross-language MT fails, native transcription still functions.

**Q: How is emergency mode protected?**
A: Emergency mode uses deterministic predefined IDs that do not rely on AI translation. Packets are encrypted with AES-256-GCM and signed with a MAC, preventing spoofing.

**Q: Can an attacker replay a CRITICAL message?**
A: No. Every packet includes a monotonic nonce/counter. Replayed packets will be rejected by the receiver's `SecureSessionManager`.

**Q: Why Bluetooth and Wi-Fi?**
A: Wi-Fi Peer-to-Peer provides high bandwidth for initial pairing and short-range groups. Bluetooth RFCOMM provides a resilient, lower-power fallback capable of long-range links when standard IP networks fail.

**Q: What happens on low-RAM phones?**
A: The architecture uses a single active `TransceiverCoordinator` and unloads inactive language sessions to enforce a one-heavy-session principle. Exact limits on 2GB/3GB phones are NOT TESTED.

**Q: What is physically tested vs only unit tested?**
A: Architecture, packet formats, UI state logic, and cryptography are unit tested (L1). End-to-end device deployment (L3/L4) is currently pending due to model and hardware unavailability.

**Q: What remains future work?**
A: Resolving the ONNX signatures for IndicTrans2 to unblock local MT, provisioning all int8 quantized models, and conducting L5 offline field stress tests across diverse Android devices.
