# Android Compatibility Audit

## Baseline
- **minSdk:** 26
- **targetSdk:** 34
- **compileSdk:** 34
- **Compose Version:** 2024.06.00 BOM (Compiler 1.5.14)
- **Kotlin Version:** 1.9.24
- **AGP Version:** 8.5.2
- **Gradle Version:** 8.7

## Dependency Minimum API Audit

### Jetpack Compose
- **Dependency:** ndroidx.compose:compose-bom
- **Version:** 2024.06.00
- **Required by:** UI Layer
- **Minimum API if known:** 21
- **Evidence/source:** Official AndroidX Compose Documentation.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A (Supports down to API 21)

### AndroidX Lifecycle / ViewModels
- **Dependency:** ndroidx.lifecycle:lifecycle-runtime-ktx
- **Version:** 2.8.4
- **Required by:** Architecture Components
- **Minimum API if known:** 21
- **Evidence/source:** AndroidX release notes.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A

### Coroutines
- **Dependency:** org.jetbrains.kotlinx:kotlinx-coroutines-android
- **Version:** 1.8.1
- **Required by:** Concurrency Engine
- **Minimum API if known:** 21
- **Evidence/source:** KotlinX GitHub documentation.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A

### ONNX Runtime Android
- **Dependency:** com.microsoft.onnxruntime:onnxruntime-android
- **Version:** 1.17.1
- **Required by:** Translation Engine (MT)
- **Minimum API if known:** 21
- **Evidence/source:** ONNX Runtime official releases.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A

### Sherpa-onnx Native Runtime
- **Dependency:** sherpa-onnx.aar
- **Version:** Local (based on official release)
- **Required by:** STT / TTS Pipeline
- **Minimum API if known:** 21
- **Evidence/source:** Kaldi / Sherpa-onnx GitHub release notes.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A

### Bluetooth APIs
- **Dependency:** Android Framework (BluetoothAdapter, BluetoothSocket)
- **Version:** Platform
- **Required by:** RFCOMM Peer-to-Peer Transport
- **Minimum API if known:** 5 (Classic Bluetooth socket APIs)
- **Evidence/source:** Android Developer Documentation.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A

### Cryptography APIs
- **Dependency:** java.security, javax.crypto (JCA)
- **Version:** Platform
- **Required by:** SecureSessionManager (ECDH, AES-GCM, HKDF-SHA256)
- **Minimum API if known:** 10+ (AES-GCM), 1 (ECDH P-256)
- **Evidence/source:** Android Developer Security documentation. X25519 is API 31+, but the codebase uses standard NIST P-256 (secp256r1) which is supported ubiquitously back to API 1.
- **Potential blocker:** No
- **Fallback/workaround possible:** N/A

### Permissions Handling
- **Dependency:** Platform
- **Version:** Platform
- **Required by:** ConnectScreen.kt Bluetooth discovery
- **Minimum API if known:** API 31 introduced new BLUETOOTH_CONNECT, BLUETOOTH_SCAN. Code uses Build.VERSION.SDK_INT >= Build.VERSION_CODES.S to fallback safely to legacy BLUETOOTH and ACCESS_FINE_LOCATION on older APIs.
- **Evidence/source:** Source audit of ConnectScreen.kt and AndroidManifest.xml.
- **Potential blocker:** No
- **Fallback/workaround possible:** Yes, fallback is natively implemented.

## Source API Audit
- Build.VERSION.SDK_INT: Used correctly in ConnectScreen.kt to gate Bluetooth permissions, and in DiagnosticsScreen.kt to display metrics.
- BLUETOOTH_CONNECT / BLUETOOTH_SCAN: Masked by SDK checks and defined properly in Manifest with maxSdkVersion=30 applied to legacy permissions.
- AudioRecord / AudioTrack: Standard PCM 16kHz configurations utilized which have been standard since Android 1.0.

## Conclusion
The official minSdk floor is currently 26. Based purely on dependency and framework constraints, the application *could* hypothetically support down to API 21, but API 26 covers >95% of active global devices while avoiding legacy audio bugs, providing stable Memory-Mapped File support for ONNX, and ensuring consistent MediaCodec behaviors later.

We will **NOT** lower minSdk blindly below 26.
