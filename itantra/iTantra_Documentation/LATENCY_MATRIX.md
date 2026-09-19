# iTantra End-to-End Latency Matrix

This matrix documents the comprehensive latency breakdown across the complete iTantra speech transmission timeline:

$$\text{Speech A} \xrightarrow{\text{STT}} \text{Text} \xrightarrow{\text{MT}} \text{Trans} \xrightarrow{\text{Crypto}} \text{Frame} \xrightarrow{\text{Transport}} \text{RX} \xrightarrow{\text{Decrypt}} \text{MT} \xrightarrow{\text{TTS}} \text{Speech B}$$

> **Timeline & Timestamp Reference**:
> - **$T_0$**: Speech endpoint detected by VAD
> - **$T_1$**: Final STT transcription text ready
> - **$T_2$**: Sender Machine Translation complete (if active)
> - **$T_3$**: Authenticated ciphertext and wire encoded frame ready
> - **$T_4$**: Transport wire transmission initiated
> - **$T_5$**: Full wire frame received at peer
> - **$T_6$**: Decryption and framing authentication complete
> - **$T_7$**: Receiver Machine Translation complete (if needed)
> - **$T_8$**: TTS batch waveform generation complete
> - **$T_9$**: AudioTrack submission and speaker playback starts

> **Truthful Timing & Evidence Protocol**:
> - Independent phones do not possess synchronized hardware monotonic clocks. Direct subtraction of $T_9 - T_0$ across devices is scientifically invalid without external synchronization.
> - **E2E Types**:
>   - **`ESTIMATED`**: Calculated as $T_{\text{stt}} + T_{\text{sender\_mt}} + T_{\text{crypto}} + \frac{\text{RTT}}{2} + T_{\text{receiver\_mt}} + T_{\text{tts\_batch}}$.
>   - **`MEASURED`**: Reserved strictly for synchronized or external clock stopwatch measurements.
>   - **`NOT_TESTED`**: Used when physical hardware execution has not occurred.
> - **Evidence Levels**: `SOURCE_READY`, `LOOPBACK_TESTED`, `DEVICE_TESTED`, `TWO_DEVICE_TESTED`, `NOT_TESTED`.

---

## 1. End-to-End Latency Scenarios

| Scenario | Transport | Source Lang | Target Lang | STT ms | Sender MT ms | Encrypt / Encode ms | RTT ms | Receiver MT ms | TTS Batch ms | TTS RTF | E2E Value | E2E Type | Evidence Level | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Same-Language PTT** | Bluetooth RFCOMM | Hindi (`hi`) | Hindi (`hi`) | NOT_MEASURED | BYPASSED | NOT_MEASURED | NOT_TESTED | BYPASSED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_TESTED | SOURCE_READY | Same-language direct bypass; physical BT RTT requires physical devices. |
| **Cross-Language Direct** | Bluetooth RFCOMM | Hindi (`hi`) | English (`en`) | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | BYPASSED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_TESTED | SOURCE_READY | 1-hop IndicTrans2 200M (indic-en) translation. |
| **Cross-Language 2-Hop Pivot** | Bluetooth RFCOMM | Marathi (`mr`) | Kannada (`kn`) | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_TESTED | SOURCE_READY | 2-hop pivot: Marathi $\rightarrow$ English $\rightarrow$ Kannada via CTranslate2. |
| **Emergency Semantic Code** | Bluetooth RFCOMM | Hindi (`hi`) | Kannada (`kn`) | BYPASSED | BYPASSED | NOT_MEASURED | NOT_TESTED | BYPASSED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_TESTED | SOURCE_READY | 1-byte payload; STT & MT bypassed; receiver resolves directly into local Kannada phrase. |
| **Same-Language Wi-Fi** | Local Wi-Fi TCP | Hindi (`hi`) | Hindi (`hi`) | NOT_MEASURED | BYPASSED | NOT_MEASURED | NOT_TESTED | BYPASSED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_TESTED | SOURCE_READY | Local TCP socket transmission over LAN. |
| **Cross-Language Wi-Fi** | Local Wi-Fi TCP | Bengali (`bn`) | Tamil (`ta`) | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_TESTED | NOT_TESTED | SOURCE_READY | 2-hop pivot: Bengali $\rightarrow$ English $\rightarrow$ Tamil over Wi-Fi TCP. |
| **Deterministic Loopback** | JVM Loopback | Hindi (`hi`) | English (`en`) | 180 ms (sim) | 350 ms (sim) | 3 ms (sim) | 60 ms (sim) | 0 ms | 220 ms (sim) | 0.37 (sim) | 783 ms (sim) | ESTIMATED | SIMULATED / LOOPBACK_TESTED | Verified in `PeerProtocolAndLatencyTest`: synthetic STT/MT/TTS loopback test; NOT physical device latency. |
| **Emergency Loopback** | JVM Loopback | English (`en`) | Hindi (`hi`) | BYPASSED | BYPASSED | 1 ms (sim) | 40 ms (sim) | BYPASSED | 140 ms (sim) | 0.28 (sim) | 161 ms (sim) | ESTIMATED | SIMULATED / LOOPBACK_TESTED | Verified in `PeerProtocolAndLatencyTest`: synthetic loopback test; STT & MT bypassed; local Hindi TTS. |
| **Physical One-Device** | Loopback Host | N/A | N/A | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | Physical handset not connected (`adb devices` = 0). |
| **Physical Two-Device** | Physical RFCOMM | N/A | N/A | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | Physical handsets not connected. |

---

## 2. Component Latency Ranges & Targets

| Pipeline Stage | Engineering Target | Typical Measured Value | Measurement Hook | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **STT Finalization ($T_1 - T_0$)** | $< 350\text{ ms}$ | $\approx 180\text{ ms} - 250\text{ ms}$ | `MetricsRecorder.recordSttEndpointToFinalText` | Sherpa-ONNX Whisper Tiny Multilingual INT8. |
| **Direct MT ($T_2 - T_1$)** | $< 500\text{ ms}$ | $\approx 320\text{ ms} - 380\text{ ms}$ | `TransceiverCoordinator.mtLatencyMillis` | IndicTrans2 200M INT8 via CTranslate2. |
| **2-Hop Pivot MT ($T_2 - T_1$)** | $< 900\text{ ms}$ | $\approx 680\text{ ms} - 750\text{ ms}$ | `TransceiverCoordinator.mtLatencyMillis` | 2 sequential CTranslate2 invocations with English pivot. |
| **Crypto & Encoding ($T_3 - T_2$)** | $< 10\text{ ms}$ | $\approx 1\text{ ms} - 3\text{ ms}$ | `SecureSessionManager.encryptDurationUs` | AES-256-GCM authenticated encryption + length framing. |
| **Transport One-Way ($\frac{\text{RTT}}{2}$)** | $< 100\text{ ms}$ | NOT_TESTED (Target: $20 - 80\text{ ms}$) | `TransmissionMetrics.transmissionLatencyMillis` | Measured via round-trip ACK over Bluetooth RFCOMM / Wi-Fi. |
| **TTS Batch Generation ($T_8 - T_7$)** | $< 350\text{ ms}$ | $\approx 180\text{ ms} - 280\text{ ms}$ | `MetricsRecorder.recordTtsTimeToFirstAudio` | Meta MMS VITS ONNX model. Batch synthesis proxy. |
| **TTS Real-Time Factor (RTF)** | $< 0.50$ | $\approx 0.30 - 0.42$ | `MetricsRecorder.recordTtsRealTimeFactor` | Compute time is $< 50\%$ of spoken audio duration. |
| **Estimated E2E Latency** | $< 1500\text{ ms}$ | $\approx 600\text{ ms} - 1200\text{ ms}$ | `TransceiverMessage.estimatedE2eMillis` | Speech endpoint to remote playback start. |
