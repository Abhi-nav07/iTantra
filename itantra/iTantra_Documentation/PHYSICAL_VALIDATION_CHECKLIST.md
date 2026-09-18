# Physical Validation Checklist

### Environment Setup
- [ ] Install `app-debug.apk` on Phone A
- [ ] Install `app-debug.apk` on Phone B
- [ ] Grant Microphone, Bluetooth, Nearby Devices permissions on both.

### Offline Model Validation
- [ ] Provision models via UI onto Phone A and Phone B (or push via adb).
- [ ] Verify `Hindi` language pack becomes READY.
- [ ] Verify `English` language pack becomes READY.
- [ ] Turn ON Airplane Mode, turn ON Bluetooth (Wi-Fi OFF, Cellular OFF).
- [ ] Force close the app, open it again (Offline Restart).
- [ ] Speak Hindi into Phone A, verify local transcription STT success.
- [ ] Speak English into Phone A, verify local transcription STT success.
- [ ] Trigger text-to-speech for Hindi and English, verify TTS playback.

### Two-Phone Transport & Security
- [ ] Initiate Bluetooth scan from Phone A.
- [ ] Pair and connect Phone A to Phone B.
- [ ] Observe Handshake sequence in logs/UI.
- [ ] Verify matching SAS (Short Authentication String) on both screens.
- [ ] Approve SAS on both devices.
- [ ] Send text A -> B. Verify immediate arrival.
- [ ] Verify `ACK` metric changes to delivered status.
- [ ] Acknowledge manually, verify `HUMAN_ACK` propagation B -> A.
- [ ] Send text B -> A. Verify immediate arrival.
- [ ] Send an `EMERGENCY` priority message, verify UI overrides and audio alerts.
- [ ] Disconnect Bluetooth from OS settings, verify UI reflects disconnection.
- [ ] Re-connect, verify successful secure session resumption (Reconnect).

### Alternative Transport
- [ ] Turn OFF Bluetooth, connect both to the same local Wi-Fi router (no internet).
- [ ] Repeat Pair and Send A -> B over Wi-Fi TCP.

### Metrics
- [ ] Record Memory usage (RAM) during STT inference.
- [ ] Record end-to-end Latency (A-Speak to B-Play).
