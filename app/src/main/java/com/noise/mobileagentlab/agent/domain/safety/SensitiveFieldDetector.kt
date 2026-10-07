package com.noise.mobileagentlab.agent.domain.safety

/**
 * Phase 6/14 groundwork — local, deterministic sensitive-field detection.
 *
 * Runs on EVERY observation, before any action is considered. It is part of the
 * safety boundary, not a prompt hint: the planner never gets to override it.
 *
 * Heuristics are intentionally broad. A false positive only blocks a demo action;
 * a false negative could expose a secret, so we err on the side of blocking.
 */
object SensitiveFieldDetector {

    /** Labels/descriptions/view-ids that mark a field as off-limits. */
    private val SENSITIVE_TERMS = listOf(
        "password", "passwd", "passcode",
        "otp", "one time code", "one-time code", "verification code", "verify code",
        "sms code", "auth code", "2fa", "two factor", "two-factor",
        "pin", "security code", "cvv", "cvc", "card number", "cardnumber",
        "credit card", "debit card", "payment", "billing", "iban", "swift",
        "expiry", "exp date", "cvc2",
        "secret", "recovery code", "backup code", "private key",
        "account security", "security question", "mother maiden",
        "ssn", "social security", "tax id",
    )

    /** Labels that describe account-security surfaces (never toggled by the agent). */
    private val SECURITY_SURFACES = listOf(
        "two-step", "2-step", "two step verification", "screen lock",
        "device admin", "grant admin", "factory reset", "erase all",
        "change password", "reset password", "delete account",
    )

    private val PIN_LIKE = Regex("\\bpin\\b", RegexOption.IGNORE_CASE)

    /**
     * @param label combined text + content description + state description
     * @param viewId platform view id resource name, if any
     * @param isPassword platform password-field flag
     */
    fun isSensitive(label: String, viewId: String? = null, isPassword: Boolean = false): Boolean {
        if (isPassword) return true
        val haystack = buildString {
            append(label)
            if (!viewId.isNullOrBlank()) {
                append(' ')
                append(viewId.substringAfterLast('/'))
            }
        }.lowercase()

        if (haystack.isBlank()) return false
        if (SENSITIVE_TERMS.any { haystack.contains(it) }) return true
        // "PIN" as a whole word only, to avoid matching "shopping"/"shipping".
        if (PIN_LIKE.containsMatchIn(haystack)) return true
        return SECURITY_SURFACES.any { haystack.contains(it) }
    }

    /** True when a piece of text looks like it could be a secret (used by redaction). */
    fun looksLikeSecret(value: String): Boolean {
        if (value.isBlank()) return false
        if (isSensitive(value)) return true
        // long digit runs (cards/OTP)
        if (Regex("\\d{4,}").containsMatchIn(value)) return true
        return false
    }
}
