package com.noise.mobileagentlab.agent.domain.trace

import com.noise.mobileagentlab.agent.domain.safety.SensitiveFieldDetector

/**
 * Phase 15/24 — redaction applied to everything that is persisted or rendered
 * from a trace.
 *
 * The demo target has no secrets by construction, but the trace layer still
 * refuses to persist anything that looks like one: digit runs (OTP/card/PIN),
 * e-mail addresses, and any fragment classified as sensitive.
 */
object TraceRedactor {

    private val LONG_DIGIT_RUN = Regex("\\d{4,}")
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+")

    const val REDACTED = "[redacted]"

    fun mask(value: String): String {
        if (value.isBlank()) return value
        if (SensitiveFieldDetector.looksLikeSecret(value) && value.length > 8) {
            // long enough to carry a credential-like value
            return REDACTED
        }
        var out = EMAIL.replace(value) { REDACTED }
        out = LONG_DIGIT_RUN.replace(out) { match -> "*".repeat(match.value.length) }
        if (SensitiveFieldDetector.isSensitive(out)) return REDACTED
        return out
    }
}
