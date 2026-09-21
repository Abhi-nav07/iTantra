package com.itantra.regression

import com.itantra.core.crypto.ReplayWindow
import org.junit.Assert.*
import org.junit.Test

class ReplayWindowTest {

    @Test
    fun testFirstPacketAndMonotonicCountersAccepted() {
        val window = ReplayWindow(64)
        for (i in 1L..100L) {
            assertTrue("Counter $i should be accepted", window.accept(i))
            assertEquals(i, window.highestAccepted())
        }
    }

    @Test
    fun testDuplicateCounterRejected() {
        val window = ReplayWindow(64)
        assertTrue(window.accept(10L))
        assertFalse("Duplicate counter 10 must be rejected", window.accept(10L))
    }

    @Test
    fun testOutOfOrderWithinWindowAccepted() {
        val window = ReplayWindow(64)
        assertTrue(window.accept(50L))
        assertTrue(window.accept(40L))
        assertTrue(window.accept(45L))
        assertFalse("Duplicate of out-of-order must be rejected", window.accept(40L))
        assertFalse("Duplicate of out-of-order must be rejected", window.accept(45L))
    }

    @Test
    fun testStaleCounterOutsideWindowRejected() {
        val window = ReplayWindow(64)
        assertTrue(window.accept(100L))
        assertTrue(window.accept(37L))
        assertFalse("Counter 36 is outside 64-window and must be rejected", window.accept(36L))
        assertFalse("Counter 1 is outside 64-window and must be rejected", window.accept(1L))
    }

    @Test
    fun testWindowAdvancement() {
        val window = ReplayWindow(64)
        assertTrue(window.accept(10L))
        assertTrue(window.accept(200L))
        assertEquals(200L, window.highestAccepted())
        assertFalse("Old counter before jump must be rejected", window.accept(10L))
        assertFalse("Counter outside new window [137, 200] must be rejected", window.accept(136L))
        assertTrue("Counter inside new window must be accepted", window.accept(150L))
    }

    @Test
    fun testPreAuthCheckDoesNotMutateState() {
        val window = ReplayWindow(64)
        assertTrue(window.accept(10L))

        // Pre-auth check on counter 50 (valid, but NOT committed)
        assertTrue(window.checkAcceptable(50L))
        assertEquals("Highest must remain 10 before commit", 10L, window.highestAccepted())

        // Pre-auth check on duplicate 10 (invalid)
        assertFalse(window.checkAcceptable(10L))

        // Pre-auth check on stale 5 (valid within window of 10)
        assertTrue(window.checkAcceptable(5L))

        // Commit 50
        window.commit(50L)
        assertEquals(50L, window.highestAccepted())
        assertFalse("Counter 50 is now duplicate", window.checkAcceptable(50L))
    }

    @Test
    fun testResetClearsWindow() {
        val window = ReplayWindow(64)
        assertTrue(window.accept(50L))
        window.reset()
        assertEquals(-1L, window.highestAccepted())
        assertTrue("After reset, counter 50 can be accepted in new session", window.accept(50L))
    }
}
