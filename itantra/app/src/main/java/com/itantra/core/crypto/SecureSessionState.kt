package com.itantra.core.crypto

enum class SecureSessionState {
    NO_SESSION,
    HANDSHAKING,
    WAITING_USER_VERIFICATION,
    SECURE_VERIFIED,
    FAILED
}
