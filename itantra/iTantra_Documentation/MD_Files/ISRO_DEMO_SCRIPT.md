# ISRO DEMO SCRIPT

## Pre-requisites
- **Environment**: Airplane mode ON (to prove offline capability).
- **Setup**: Two Android devices equipped with the iTantra APK, paired over Wi-Fi Direct.
- **Constraints**: Due to missing physical neural weight files, this demo illustrates the secure transmission architecture rather than live acoustic recognition.

## Step-by-Step Flow

### 1. Offline Application Launch
**Action**: Launch iTantra on both Device A and Device B while in Airplane mode.
**Talking Point**: "The app initializes entirely offline. There are no cloud fallbacks enabled or permitted."

### 2. Language Selection & Readiness
**Action**: Open the diagnostics or language settings menu. Show the 10 languages (Hindi, English, Bengali, etc.). 
**Talking Point**: "The system natively maps all 10 scheduled languages. Currently, the status indicates `MODEL MISSING`, correctly reflecting the absence of local inference weights to prevent false starts."

### 3. Secure Peer Transmission
**Action**: Select an emergency semantic payload (e.g., `MEDICAL_EMERGENCY`) on Device A.
**Talking Point**: "Because we cannot capture live acoustic audio without the STT model, we trigger a predefined semantic packet. This mimics the exact flow that follows a successful speech transcription."

### 4. Semantic Packet Size vs Raw PCM
**Action**: Display the Diagnostics view on Device A.
**Talking Point**: "Notice the semantic packet size is only ~50 bytes. This represents a 99.9% bandwidth reduction over raw 16 kHz PCM audio (which requires ~256 kbps). This extreme reduction is what guarantees delivery over deeply degraded RFCOMM or Wi-Fi edge connections."

### 5. Receiver Action & Security
**Action**: View Device B as it receives the packet.
**Talking Point**: "The packet arrived instantly. It was encrypted via AES-256-GCM and verified with a MAC. A monotonic nonce prevented replay attacks. If the MAC or nonce were invalid, it would drop silently without ever waking the UI."

### 6. Distinguishing ACK vs HUMAN_ACK
**Action**: Observe the delivery checkmarks on Device A.
**Talking Point**: "We see the first checkmark indicating a transport-level ACK. When the recipient physically acknowledges the critical message on Device B, a second checkmark (HUMAN_ACK) appears, ensuring closed-loop communication."

### 7. End of Demo
**Action**: Display final session state and memory usage in Diagnostics.
**Talking Point**: "We have demonstrated the zero-trust secure, extreme-low-bitrate semantic transport layer that forms the backbone of iTantra."
