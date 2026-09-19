# ISRO Evaluator Q&A

**Q: Why text instead of audio over the network?**
A: Audio streaming requires high sustained bandwidth and falls apart in degraded RFCOMM environments. By running STT locally and transmitting semantic text packets, typical semantic messages are designed to remain small; actual wire size is measured from encodedFrame.size, drastically increasing the reliability of tactical edge transmissions.

**Q: Why is the bitrate so low?**
A: To guarantee delivery in dense jungles, disaster zones, or degraded Bluetooth conditions where packet loss is high. Small payloads allow our re-transmission logic to work without stalling the entire socket.

**Q: What happens if there is no internet (offline)?**
A: Runtime inference and peer communication are fully offline after required models have been provisioned. Models may be downloaded or sideloaded beforehand. All models (STT, TTS, MT) are loaded locally on the Android device from provisioned model files. Nothing is sent to the cloud.

**Q: Why use a synthetic receiver voice (TTS)?**
A: Because we transmit semantic text, the receiving device must synthesize the voice locally so the operator doesn't need to look at the screen in high-stress situations.

**Q: What languages are supported?**
A: All 10 ISRO mandate languages are fully provisioned: Hindi, English, Bengali, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, and Odia. Each has STT (shared Whisper Tiny), TTS (per-language MMS/VITS), and MT (shared IndicTrans2 CT2 via English pivot for cross-language pairs).

**Q: How does Machine Translation work?**
A: The `CTranslate2TranslationEngine` performs native C++ inference via JNI using IndicTrans2 200M Distilled models with SentencePiece tokenization. It supports indic→English, English→indic, and indic↔indic (via English pivot) for all 10 languages using exact FLORES language tags.

**Q: Why IndicTrans2?**
A: It provides state-of-the-art accuracy for the 10 Indic languages required by the SIH problem statement, and has efficient distilled variants suitable for edge deployment via CTranslate2.

**Q: What security is implemented?**
A: Ephemeral ECDH (P-256) key exchange, HKDF for key derivation, and AES-256-GCM for packet encryption and authentication.

**Q: What is SAS?**
A: Short Authentication String. A 6-digit code derived from the handshake transcript that both operators manually verify on screen to prevent Man-In-The-Middle (MITM) attacks during Bluetooth pairing.

**Q: Why ACK vs HUMAN_ACK?**
A: `ACK` is automated by the transport layer to confirm receipt. `HUMAN_ACK` is an explicit packet sent when the receiving operator actually engages with the message, providing absolute tactical certainty.

**Q: How does the emergency feature work?**
A: Setting the `CRITICAL` flag bypasses normal UI flows, forces loud audio alerts on the receiver, triggers `EMERGENCY_CODE` semantic actions, and persists SOS state via `EmergencyPersistenceStore` even across app restarts. Emergency alerts bypass MT and use the receiver's active language directly.

**Q: What happens if STT fails?**
A: The user can type the message manually, or try again. The app will never transmit fake transcripts.

**Q: What happens if MT fails?**
A: The app gracefully falls back, transferring the native transcription and informing the receiver that cross-language translation failed.

**Q: How many bytes per message?**
A: Typical semantic messages are designed to remain small; actual wire size is measured from encodedFrame.size (composed of a 32-byte header, ciphertext payload, 4-byte CRC, and 4-byte frame length prefix), strictly clamped below 16KB max payload limits.

**Q: Bluetooth vs Wi-Fi?**
A: Bluetooth RFCOMM is the primary point-to-point link for peer-to-peer communication (NOT a mesh protocol). Local Wi-Fi TCP is available as a higher-bandwidth, longer-range alternative when a local LAN exists (even without internet).

**Q: What is physically tested?**
A: In this automated final phase, physical Android devices were NOT TESTED. Unit tests, source verification, and local simulated loopbacks verify the architecture. The benchmark and performance harnesses are built and ready for physical execution.

**Q: What remains?**
A: Two-phone physical testing, real WER measurements in noise, physical latency benchmarks, and real device performance profiling (PSS, CPU, battery, thermal).

**Q: How does background operation work?**
A: `OperationalForegroundService` keeps continuous listening active when the app is backgrounded or the screen is locked, using `FOREGROUND_SERVICE_MICROPHONE` permission.

**Q: Why is this useful to ISRO/disaster response?**
A: It provides highly reliable, offline, secure communication across language barriers without cellular infrastructure, which is essential for coordinated rescue ops.
