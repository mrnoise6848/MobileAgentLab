package com.noise.mobileagentlab.agent.domain.safety

import com.noise.mobileagentlab.agent.domain.action.ActionType
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {

    private val policy = SafetyPolicy.default()

    @Test
    fun `default policy allows the five showcase actions`() {
        assertTrue(policy.allows(ActionType.CLICK))
        assertTrue(policy.allows(ActionType.LONG_CLICK))
        assertTrue(policy.allows(ActionType.TYPE_TEXT))
        assertTrue(policy.allows(ActionType.SCROLL))
        assertTrue(policy.allows(ActionType.BACK))
    }

    @Test
    fun `HOME is never allowed by the default policy`() {
        assertFalse(policy.allows(ActionType.HOME))
    }

    @Test
    fun `default policy scopes to the demo target package only`() {
        assertEquals(setOf(SafetyPolicy.DEMO_TARGET_PACKAGE), policy.allowedPackages)
    }

    @Test
    fun `inside boundary passes`() {
        assertNull(
            policy.boundaryViolation(
                SafetyPolicy.DEMO_TARGET_PACKAGE,
                SafetyPolicy.DEMO_TARGET_PACKAGE,
            ),
        )
    }

    @Test
    fun `unknown observed package violates the boundary`() {
        assertEquals(
            FailureReason.PACKAGE_NOT_ALLOWED,
            policy.boundaryViolation("com.other.app", SafetyPolicy.DEMO_TARGET_PACKAGE),
        )
    }

    @Test
    fun `null observed package violates the boundary`() {
        assertEquals(
            FailureReason.PACKAGE_NOT_ALLOWED,
            policy.boundaryViolation(null, SafetyPolicy.DEMO_TARGET_PACKAGE),
        )
    }

    @Test
    fun `foreign foreground violates the boundary even with a valid observation`() {
        assertEquals(
            FailureReason.PACKAGE_NOT_ALLOWED,
            policy.boundaryViolation(SafetyPolicy.DEMO_TARGET_PACKAGE, "com.other.app"),
        )
    }

    @Test
    fun `null foreground is tolerated when the observation is valid`() {
        assertNull(
            policy.boundaryViolation(SafetyPolicy.DEMO_TARGET_PACKAGE, null),
        )
    }

    @Test
    fun `enforceForeground false ignores a foreign foreground`() {
        val relaxed = policy.copy(enforceForeground = false)
        assertNull(
            relaxed.boundaryViolation(SafetyPolicy.DEMO_TARGET_PACKAGE, "com.other.app"),
        )
    }
}
