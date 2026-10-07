package com.noise.mobileagentlab.agent.domain.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFieldDetectorTest {

    @Test
    fun `password labels are sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("Password"))
        assertTrue(SensitiveFieldDetector.isSensitive("Enter passwd"))
        assertTrue(SensitiveFieldDetector.isSensitive("passcode"))
    }

    @Test
    fun `otp labels are sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("One-time code"))
        assertTrue(SensitiveFieldDetector.isSensitive("verification code"))
        assertTrue(SensitiveFieldDetector.isSensitive("2FA code"))
    }

    @Test
    fun `PIN as a whole word is sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("Enter your PIN"))
        assertTrue(SensitiveFieldDetector.isSensitive("confirm pin"))
        assertTrue(SensitiveFieldDetector.isSensitive("PIN:"))
    }

    @Test
    fun `PIN inside a view id resource name is sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("", viewId = "com.example:id/pin_field"))
        assertTrue(SensitiveFieldDetector.isSensitive("", viewId = "com.example:id/security_pin"))
    }

    @Test
    fun `payment labels are sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("Card number"))
        assertTrue(SensitiveFieldDetector.isSensitive("billing address"))
        assertTrue(SensitiveFieldDetector.isSensitive("expiry date"))
    }

    @Test
    fun `password flag is always sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("anything", isPassword = true))
    }

    @Test
    fun `shopping and shipping are NOT sensitive`() {
        // Regression: "pin" used to be a substring term, so "shopping"/"shipping"
        // (both contain "pin") were falsely flagged and blocked as targets.
        assertFalse(SensitiveFieldDetector.isSensitive("Shopping cart"))
        assertFalse(SensitiveFieldDetector.isSensitive("Shipping address"))
        assertFalse(SensitiveFieldDetector.isSensitive("Search coffee"))
        assertFalse(SensitiveFieldDetector.isSensitive("Spinning loader"))
    }

    @Test
    fun `plain labels are not sensitive`() {
        assertFalse(SensitiveFieldDetector.isSensitive("Settings"))
        assertFalse(SensitiveFieldDetector.isSensitive("Offline mode"))
        assertFalse(SensitiveFieldDetector.isSensitive(""))
    }

    @Test
    fun `security surfaces are sensitive`() {
        assertTrue(SensitiveFieldDetector.isSensitive("Screen lock"))
        assertTrue(SensitiveFieldDetector.isSensitive("Factory reset"))
        assertTrue(SensitiveFieldDetector.isSensitive("Delete account"))
    }

    @Test
    fun `long digit runs look like secrets`() {
        assertTrue(SensitiveFieldDetector.looksLikeSecret("12345678"))
        assertTrue(SensitiveFieldDetector.looksLikeSecret("4111 1111 1111 1111"))
        assertFalse(SensitiveFieldDetector.looksLikeSecret("coffee"))
        assertFalse(SensitiveFieldDetector.looksLikeSecret("2 cups"))
        assertFalse(SensitiveFieldDetector.looksLikeSecret("   "))
    }
}
