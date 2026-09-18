# ISRO Evaluator Q&A

**Q: Why text instead of audio over the network?**
A: Audio streaming requires high sustained bandwidth and falls apart in degraded RFCOMM environments. By running STT locally and transmitting semantic text packets, we reduce the payload to under 16KB, drastically increasing the reliability of tactical edge transmissions.

**Q: Why is the bitrate so low?**
A: To guarantee delivery in dense jungles, disaster zones, or degraded Bluetooth conditions where packet loss is high. Small payloads allow our error correction and re-transmission logic to work without stalling the entire socket.

**Q: What happens if there is no internet (offline)?**
A: iTantra is 100% offline. All models (STT, TTS, MT) are loaded locally on the Android device from provisioned `.onnx` files. Nothing is sent to the cloud.

**Q: Why use a synthetic receiver voice (TTS)?**
A: Because we transmit semantic text, the receiving device must synthesize the voice locally so the operator doesn't need to look at the screen in high-stress situations.

**Q: What languages are supported?**
A: The architecture supports 10 ISRO mandate languages. Currently, only Hindi and English have models verified in the provisioning pipeline. The other 8 are structurally supported but blocked until models are supplied.

**Q: Why only Hindi/English provisioned right now?**
A: We rigorously enforce a "no fake models" policy. We only claim readiness when real, functional Sherpa-ONNX Whisper/Piper models are bit-for-bit matched to the device.

**Q: Why is Machine Translation (MT) blocked?**
A: The `RealTranslationEngine` requires an offline NMT architecture (like IndicTrans2). While the Java/Kotlin integration is scaffolded, the actual lightweight ONNX/NCNN tensors for cross-language generation on edge devices are currently missing/unverified.

**Q: Why IndicTrans2?**
A: It provides state-of-the-art accuracy for the 10 Indic languages required by the SIH problem statement.

**Q: How will MT be fixed?**
A: By quantizing an IndicTrans2 (or Marian NMT) model to INT8 ONNX or CTranslate2 formats and updating the `RealTranslationEngine` to invoke it via JNI, similar to the current STT implementation.

**Q: What security is implemented?**
A: Ephemeral ECDH (P-256) key exchange, HKDF for key derivation, and AES-256-GCM for packet encryption and authentication.

**Q: What is SAS?**
A: Short Authentication String. A 6-digit code derived from the handshake transcript that both operators manually verify on screen to prevent Man-In-The-Middle (MITM) attacks during Bluetooth pairing.

**Q: Why ACK vs HUMAN_ACK?**
A: `ACK` is automated by the transport layer to confirm receipt. `HUMAN_ACK` is an explicit packet sent when the receiving operator actually engages with the message, providing absolute tactical certainty.

**Q: How does the emergency feature work?**
A: Setting the `CRITICAL` flag bypasses normal UI flows, forces loud audio alerts on the receiver, and triggers `EMERGENCY_CODE` semantic actions even if the receiver is busy.

**Q: What happens if STT fails?**
A: The user can type the message manually, or try again. The app will never transmit fake transcripts.

**Q: What happens if MT fails?**
A: The app gracefully falls back, transferring the native transcription and informing the receiver that cross-language translation failed.

**Q: How many bytes per message?**
A: Usually under 200 bytes for a text sentence, padded with a 32-byte header and 4-byte CRC, strictly clamped below 16KB max payload limits.

**Q: Bluetooth vs Wi-Fi?**
A: Bluetooth RFCOMM is the primary mesh link for peer-to-peer. Local Wi-Fi TCP is available as a higher-bandwidth, longer-range alternative when a local LAN exists (even without internet).

**Q: What is physically tested?**
A: In this automated final phase, physical Android devices were NOT TESTED. Unit tests and local mocked loopbacks verify the source architecture completely.

**Q: What remains?**
A: Two-phone physical testing, real WER measurements in noise, latency benchmarks, and integrating the final cross-language MT tensors.

**Q: How would you scale to all 10 languages?**
A: Provide the corresponding Sherpa-ONNX model sets and register their checksums in `provision_models.py`. The internal architecture already routes the 10 language IDs via `ProtocolLanguageMapper`.

**Q: Why is this useful to ISRO/disaster response?**
A: It provides highly reliable, offline, secure communication across language barriers without cellular infrastructure, which is essential for coordinated rescue ops.
