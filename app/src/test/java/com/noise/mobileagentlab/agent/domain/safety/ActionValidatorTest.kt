package com.noise.mobileagentlab.agent.domain.safety

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.action.ScrollDirection
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.testutil.demoSnapshot
import com.noise.mobileagentlab.testutil.uiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    private val validator = ActionValidator(SafetyPolicy.default())
    private val pkg = SafetyPolicy.DEMO_TARGET_PACKAGE

    private val snapshot = demoSnapshot(
        uiNode(id = "n0", label = "Brew Lab"),
        uiNode(id = "n1", label = "Settings", clickable = true),
        uiNode(id = "n2", label = "Drink name", editable = true),
        uiNode(id = "n3", label = "Results", scrollable = true),
        uiNode(id = "n4", label = "Plain", clickable = false),
        uiNode(id = "n5", label = "Disabled", clickable = true, enabled = false),
        uiNode(id = "n6", label = "Password", editable = true, sensitive = true),
        uiNode(id = "n7", label = "Foreign", clickable = true, packageName = "com.other.app"),
    )

    private fun validate(action: AgentAction, fg: String? = pkg): ValidationOutcome =
        validator.validate(action, snapshot, fg)

    private fun assertRejected(action: AgentAction, reason: FailureReason, fg: String? = pkg) {
        val outcome = validate(action, fg)
        assertTrue("expected rejection, got $outcome", outcome is ValidationOutcome.Rejected)
        assertEquals(reason, (outcome as ValidationOutcome.Rejected).reason)
    }

    @Test
    fun `click on an allowed clickable node passes`() {
        assertEquals(ValidationOutcome.Allowed, validate(AgentAction.Click("n1", "Settings")))
    }

    @Test
    fun `HOME is rejected by the action allowlist`() {
        assertRejected(AgentAction.Home, FailureReason.ACTION_NOT_ALLOWED)
    }

    @Test
    fun `back passes as a global action`() {
        assertEquals(ValidationOutcome.Allowed, validate(AgentAction.Back))
    }

    @Test
    fun `foreign observed package is rejected`() {
        val foreign = demoSnapshot(
            uiNode(id = "n1", label = "Settings", clickable = true),
            pkg = "com.other.app",
        )
        val outcome = validator.validate(
            AgentAction.Click("n1", "Settings"),
            foreign,
            pkg,
        )
        assertTrue(outcome is ValidationOutcome.Rejected)
        assertEquals(
            FailureReason.PACKAGE_NOT_ALLOWED,
            (outcome as ValidationOutcome.Rejected).reason,
        )
    }

    @Test
    fun `foreign foreground is rejected`() {
        assertRejected(
            AgentAction.Click("n1", "Settings"),
            FailureReason.PACKAGE_NOT_ALLOWED,
            fg = "com.other.app",
        )
    }

    @Test
    fun `missing target is rejected`() {
        assertRejected(AgentAction.Click("n99", "Ghost"), FailureReason.TARGET_NOT_FOUND)
    }

    @Test
    fun `disabled target is rejected`() {
        assertRejected(AgentAction.Click("n5", "Disabled"), FailureReason.TARGET_DISABLED)
    }

    @Test
    fun `sensitive target is rejected`() {
        assertRejected(
            AgentAction.TypeText("n6", "hunter2"),
            FailureReason.SENSITIVE_TARGET_BLOCKED,
        )
    }

    @Test
    fun `target from another package is rejected`() {
        assertRejected(AgentAction.Click("n7", "Foreign"), FailureReason.PACKAGE_NOT_ALLOWED)
    }

    @Test
    fun `click on a non-clickable node is rejected`() {
        assertRejected(AgentAction.Click("n4", "Plain"), FailureReason.TARGET_NOT_ACTIONABLE)
    }

    @Test
    fun `type text into an editable node passes`() {
        assertEquals(
            ValidationOutcome.Allowed,
            validate(AgentAction.TypeText("n2", "coffee")),
        )
    }

    @Test
    fun `type blank text is rejected`() {
        assertRejected(
            AgentAction.TypeText("n2", "   "),
            FailureReason.INVALID_ACTION,
        )
    }

    @Test
    fun `type overly long text is rejected`() {
        assertRejected(
            AgentAction.TypeText("n2", "x".repeat(65)),
            FailureReason.INVALID_ACTION,
        )
    }

    @Test
    fun `type secret-looking text is rejected`() {
        assertRejected(
            AgentAction.TypeText("n2", "123456789012"),
            FailureReason.SENSITIVE_TARGET_BLOCKED,
        )
    }

    @Test
    fun `type into a non-editable node is rejected`() {
        assertRejected(
            AgentAction.TypeText("n1", "coffee"),
            FailureReason.TARGET_NOT_ACTIONABLE,
        )
    }

    @Test
    fun `scroll without a target passes as a global action`() {
        assertEquals(
            ValidationOutcome.Allowed,
            validate(AgentAction.Scroll(ScrollDirection.FORWARD)),
        )
    }

    @Test
    fun `scroll on a scrollable target passes`() {
        assertEquals(
            ValidationOutcome.Allowed,
            validate(AgentAction.Scroll(ScrollDirection.FORWARD, targetId = "n3")),
        )
    }

    @Test
    fun `scroll on a non-scrollable target is rejected`() {
        assertRejected(
            AgentAction.Scroll(ScrollDirection.FORWARD, targetId = "n1"),
            FailureReason.TARGET_NOT_ACTIONABLE,
        )
    }
}
