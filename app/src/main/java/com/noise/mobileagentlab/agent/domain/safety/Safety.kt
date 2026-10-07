package com.noise.mobileagentlab.agent.domain.safety

import com.noise.mobileagentlab.agent.domain.action.ActionType
import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.UiNode
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot

/** Result of local validation: only [Allowed] actions may reach the executor. */
sealed interface ValidationOutcome {
    data class Allowed(val targetId: String?) : ValidationOutcome
    data class Rejected(val reason: FailureReason, val detail: String) : ValidationOutcome
}

/**
 * Phase 6/14 — the authoritative safe execution boundary.
 *
 * Implemented locally in the agent process. The model prompt is NOT the policy:
 * even a perfectly crafted (or adversarial) planner output must pass these
 * checks before anything touches the device.
 *
 * @param allowedPackages the only packages the agent may ever act in
 * @param allowedActions  the only action types it may ever perform
 * @param maxTextLength   cap for typed text (keeps traces and inputs bounded)
 * @param blockSensitive  refuse targets classified as password/OTP/PIN/payment
 *                        and refuse text that looks like a secret
 */
data class SafetyPolicy(
    val allowedPackages: Set<String>,
    val allowedActions: Set<ActionType>,
    val maxTextLength: Int = 64,
    val blockSensitive: Boolean = true,
    val enforceForeground: Boolean = true,
) {
    /**
     * Phase 14 — the application boundary, as one function used by BOTH the
     * validator and the orchestrator (single source of truth).
     *
     * @return `null` when the observation is inside the boundary, otherwise the
     *         failure reason to record.
     */
    fun boundaryViolation(observedPackage: String?, foregroundPackage: String?): FailureReason? {
        if (observedPackage == null || observedPackage !in allowedPackages) {
            return FailureReason.PACKAGE_NOT_ALLOWED
        }
        if (enforceForeground && foregroundPackage != null && foregroundPackage !in allowedPackages) {
            return FailureReason.PACKAGE_NOT_ALLOWED
        }
        return null
    }

    fun allows(actionType: ActionType): Boolean = actionType in allowedActions

    companion object {
        const val DEMO_TARGET_PACKAGE = "com.noise.mobileagentlab.demo"

        fun default(targetPackage: String = DEMO_TARGET_PACKAGE): SafetyPolicy = SafetyPolicy(
            allowedPackages = setOf(targetPackage),
            // HOME is intentionally absent: leaving the target app is not part
            // of the showcase's execution boundary.
            allowedActions = setOf(
                ActionType.CLICK,
                ActionType.LONG_CLICK,
                ActionType.TYPE_TEXT,
                ActionType.SCROLL,
                ActionType.BACK,
            ),
        )
    }
}

class ActionValidator(val policy: SafetyPolicy) {

    fun validate(
        action: AgentAction,
        snapshot: UiSnapshot,
        foregroundPackage: String?,
    ): ValidationOutcome {
        // 1. action type
        if (!policy.allows(action.type)) {
            return ValidationOutcome.Rejected(
                FailureReason.ACTION_NOT_ALLOWED,
                "${action.type.name} is not in the allowlist",
            )
        }

        // 2. application scope — the snapshot package is the authority, the
        //    foreground window is an extra guard when known (Phase 14 boundary).
        val boundary = policy.boundaryViolation(snapshot.packageName, foregroundPackage)
        if (boundary != null) {
            return ValidationOutcome.Rejected(
                boundary,
                "observed=${snapshot.packageName ?: "unknown"} " +
                    "foreground=${foregroundPackage ?: "unknown"} is outside scope",
            )
        }

        // 3. global actions need no target
        if (action.targetId == null) return ValidationOutcome.Allowed(null)

        // 4. target existence + capabilities + sensitivity
        val target: UiNode = snapshot.node(action.targetId)
            ?: return ValidationOutcome.Rejected(
                FailureReason.TARGET_NOT_FOUND,
                "node=${action.targetId} missing from snapshot #${snapshot.sequence}",
            )

        if (target.packageName !in policy.allowedPackages) {
            return ValidationOutcome.Rejected(
                FailureReason.PACKAGE_NOT_ALLOWED,
                "node package=${target.packageName} is outside scope",
            )
        }
        if (!target.capabilities.enabled) {
            return ValidationOutcome.Rejected(
                FailureReason.TARGET_DISABLED,
                "node=${action.targetId} is disabled",
            )
        }
        if (policy.blockSensitive && target.sensitive) {
            return ValidationOutcome.Rejected(
                FailureReason.SENSITIVE_TARGET_BLOCKED,
                "node=${action.targetId} looks like a sensitive field",
            )
        }

        return when (action) {
            is AgentAction.Click ->
                requireCapability(target, action.targetId, needClick = true)

            is AgentAction.LongClick ->
                requireCapability(target, action.targetId, needLongClick = true)

            is AgentAction.TypeText -> validateTypeText(action, target)

            is AgentAction.Scroll ->
                if (target.capabilities.scrollable) ValidationOutcome.Allowed(target.id)
                else ValidationOutcome.Rejected(
                    FailureReason.TARGET_NOT_ACTIONABLE,
                    "node=${action.targetId} is not scrollable",
                )

            AgentAction.Back, AgentAction.Home -> ValidationOutcome.Allowed(null)
        }
    }

    private fun requireCapability(
        target: UiNode,
        targetId: String,
        needClick: Boolean = false,
        needLongClick: Boolean = false,
    ): ValidationOutcome = when {
        needClick && !target.capabilities.clickable -> ValidationOutcome.Rejected(
            FailureReason.TARGET_NOT_ACTIONABLE,
            "node=$targetId is not clickable",
        )

        needLongClick && !target.capabilities.longClickable -> ValidationOutcome.Rejected(
            FailureReason.TARGET_NOT_ACTIONABLE,
            "node=$targetId is not long-clickable",
        )

        else -> ValidationOutcome.Allowed(target.id)
    }

    private fun validateTypeText(action: AgentAction.TypeText, target: UiNode): ValidationOutcome {
        if (!target.capabilities.editable) {
            return ValidationOutcome.Rejected(
                FailureReason.TARGET_NOT_ACTIONABLE,
                "node=${action.targetId} is not editable",
            )
        }
        if (action.text.isBlank()) {
            return ValidationOutcome.Rejected(
                FailureReason.INVALID_ACTION,
                "empty text",
            )
        }
        if (action.text.length > policy.maxTextLength) {
            return ValidationOutcome.Rejected(
                FailureReason.INVALID_ACTION,
                "text exceeds ${policy.maxTextLength} chars",
            )
        }
        if (policy.blockSensitive && SensitiveFieldDetector.looksLikeSecret(action.text)) {
            return ValidationOutcome.Rejected(
                FailureReason.SENSITIVE_TARGET_BLOCKED,
                "refusing to type text that looks like a secret",
            )
        }
        return ValidationOutcome.Allowed(target.id)
    }
}
