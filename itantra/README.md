# iTantra
SIH 2026 PS 173 / ISRO

What it is:
offline semantic voice transceiver prototype.

Current status:
FINAL PROTOTYPE PARTIAL

Implemented:
- Compose field UI
- PTT
- continuous Silero VAD
- semantic packets
- Bluetooth RFCOMM source implementation
- local Wi-Fi TCP source implementation
- secure session architecture
- emergency semantic codes
- 10-language STT/TTS runtime integration
- reproducible model provisioning.

Not yet verified:
- physical STT/TTS Android inference
- physical MT Android inference
- two-phone Bluetooth/Wi-Fi
- real WER
- physical latency/RAM.

Source Implemented:
- real cross-language MT (CTranslate2 Native JNI)

Architecture:
- SherpaOnnxSpeechSynthesizer for TTS
- WifiPeerTransport / local Wi-Fi TCP
- Module 15 final freeze.
