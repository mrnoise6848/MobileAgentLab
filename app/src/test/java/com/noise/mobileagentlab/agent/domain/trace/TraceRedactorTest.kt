package com.noise.mobileagentlab.agent.domain.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRedactorTest {

    @Test
    fun `blank values pass through unchanged`() {
        assertEquals("", TraceRedactor.mask(""))
        assertEquals("   ", TraceRedactor.mask("   "))
    }

    @Test
    fun `ordinary text passes through unchanged`() {
        assertEquals("coffee", TraceRedactor.mask("coffee"))
        assertEquals("Shopping cart", TraceRedactor.mask("Shopping cart"))
        assertEquals("CLICK \"Settings\"", TraceRedactor.mask("CLICK \"Settings\""))
    }

    @Test
    fun `long digit runs are redacted entirely`() {
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("12345678901234"))
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("4111 1111 1111 1111"))
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("1234 5678"))
    }

    @Test
    fun `short digit runs are masked with asterisks`() {
        // 8 digits is exactly the length cutoff: masked in place, not wholesale
        assertEquals("********", TraceRedactor.mask("12345678"))
        assertEquals("****", TraceRedactor.mask("1234"))
    }

    @Test
    fun `e-mail addresses are redacted`() {
        val masked = TraceRedactor.mask("contact user@example.com today")
        assertTrue(masked.contains(TraceRedactor.REDACTED))
        assertFalse(masked.contains("user@example.com"))
        assertTrue(masked.contains("contact"))
        assertTrue(masked.contains("today"))
    }

    @Test
    fun `sensitive fragments redact the whole value`() {
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("hunter2secret"))
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("Password: coffee"))
        // short sensitive words are still fully redacted
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("pin"))
        assertEquals(TraceRedactor.REDACTED, TraceRedactor.mask("shopping cart otp"))
    }

    @Test
    fun `redacted output never re-triggers redaction`() {
        // idempotence: masking an already-masked value is a no-op
        val once = TraceRedactor.mask("4111 1111 1111 1111")
        assertEquals(once, TraceRedactor.mask(once))
    }
}
