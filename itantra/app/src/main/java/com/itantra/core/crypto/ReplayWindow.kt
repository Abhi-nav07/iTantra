package com.itantra.core.crypto

/**
 * Sliding-window replay protection for AES-GCM packet counters.
 *
 * Accepts counters within [highest - windowSize + 1, highest] that have not been seen before,
 * and any counter strictly greater than highest. Counters older than (highest - windowSize) are
 * unconditionally rejected.
 *
 * Security Architecture:
 * - [checkAcceptable] performs a pre-auth check without mutating window state, so unauthenticated
 *   packets cannot advance the window or corrupt bitmap state.
 * - [commit] marks the counter as seen and advances the window only AFTER successful AEAD authentication.
 * - [accept] performs atomic check-and-commit (convenience / test helper).
 *
 * Thread-safety: All public methods are @Synchronized.
 * No Android dependencies - fully unit-testable on JVM.
 */
class ReplayWindow(val windowSize: Int = 64) {

    init {
        require(windowSize in 1..256) { "Window size must be in [1, 256]" }
    }

    @Volatile private var highest: Long = -1L
    private val seen = LongArray((windowSize + 63) / 64)

    @Synchronized fun reset() {
        highest = -1L
        seen.fill(0L)
    }

    /**
     * Pre-check: returns true if [counter] is acceptable without mutating window state.
     * MUST be called before AEAD authentication so unauthenticated packets cannot corrupt state.
     */
    @Synchronized
    fun checkAcceptable(counter: Long): Boolean {
        if (counter <= 0L && highest >= 0L) return false
        return when {
            counter > highest -> true
            counter <= highest - windowSize -> false  // too old
            else -> !getBit(counter)                  // not duplicate
        }
    }

    /**
     * Commits [counter] into the window and advances the window if needed.
     * MUST only be called AFTER successful AEAD authentication.
     */
    @Synchronized
    fun commit(counter: Long) {
        if (counter > highest) {
            if (highest >= 0L) {
                val advance = (counter - highest).coerceAtMost(windowSize.toLong())
                for (i in 1..advance) clearBit(highest + i)
            }
            highest = counter
            setBit(counter)
        } else if (counter > highest - windowSize) {
            setBit(counter)
        }
    }

    /**
     * Atomic check and commit (for convenience and backward compatibility).
     */
    @Synchronized
    fun accept(counter: Long): Boolean {
        if (!checkAcceptable(counter)) return false
        commit(counter)
        return true
    }

    @Synchronized fun highestAccepted(): Long = highest

    private fun bitIndex(counter: Long) = (counter % windowSize).toInt()
    private fun getBit(counter: Long): Boolean {
        val idx = bitIndex(counter)
        return (seen[idx / 64] shr (idx % 64)) and 1L == 1L
    }
    private fun setBit(counter: Long) {
        val idx = bitIndex(counter); seen[idx / 64] = seen[idx / 64] or (1L shl (idx % 64))
    }
    private fun clearBit(counter: Long) {
        val idx = bitIndex(counter); seen[idx / 64] = seen[idx / 64] and (1L shl (idx % 64)).inv()
    }
}
