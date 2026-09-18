# ISRO Final Demo Script

## A. FULL HARDWARE PATH (If two physical devices are available)
1. Show both phones in Airplane mode. Activate Bluetooth on both.
2. Open iTantra on both devices.
3. Show provisioning screen: download/verify Hindi and English packages.
4. Go to connection screen: Pair Phone A and Phone B.
5. Show SAS confirmation on both screens to demonstrate MITM protection.
6. Push-to-Talk on Phone A: Speak Hindi.
7. Show transcription on Phone A, show packet transit.
8. Show UI on Phone B rendering the received text. (Note: Due to MT limits, demonstrate Same-Language first, e.g., Hindi to Hindi).
9. Send an EMERGENCY text from Phone B, show overriding alert on Phone A.
10. Explicitly mention the MT limitations to the evaluator.

## B. SAFE CURRENT DEMO PATH (Current Evidence)
1. Launch app on an emulator/device. Show UI architecture (Compose, Dark mode).
2. Demonstrate Push-to-Talk interactions and the continuous VAD integration.
3. Open the Provisioning / Diagnostics screen to show the Language Pack Architecture (SHA-256 verification of Sherpa-ONNX files).
4. Show the Semantic Packet structure documentation to explain the 16KB limit and extreme low bandwidth requirements.
5. Walk through the Secure Protocol codebase (AES-GCM, ECDH, SAS). Explain why it is robust for tactical use.
6. Trigger the Emergency semantic code locally.
7. Transparently state:
   - "Machine translation is currently blocked pending integration of a lightweight offline NMT model."
   - "Physical two-device Bluetooth validation is pending external hardware verification."
8. Conclude by highlighting that the source code securely enforces role determinism and strict offline capabilities.
