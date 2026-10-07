package com.noise.mobileagentlab.agent.domain.model

/**
 * Phase 7 — structured outcome of a platform action.
 *
 * `success = true` means "the platform reported the action was dispatched",
 * nothing more. Only [com.noise.mobileagentlab.agent.domain.verify.Verifier]
 * decides whether the intended UI change actually happened.
 */
data class ExecutionResult(
    val success: Boolean,
    val reason: FailureReason? = null,
    val detail: String = "",
    val resolvedNodeId: String? = null,
    val usedFallbackTarget: Boolean = false,
    val elapsedMs: Long = 0L,
) {
    companion object {
        fun ok(
            detail: String,
            resolvedNodeId: String?,
            usedFallbackTarget: Boolean,
            elapsedMs: Long,
        ) = ExecutionResult(
            success = true,
            detail = detail,
            resolvedNodeId = resolvedNodeId,
            usedFallbackTarget = usedFallbackTarget,
            elapsedMs = elapsedMs,
        )

        fun failure(
            reason: FailureReason,
            detail: String,
            elapsedMs: Long = 0L,
            resolvedNodeId: String? = null,
        ) = ExecutionResult(
            success = false,
            reason = reason,
            detail = detail,
            elapsedMs = elapsedMs,
            resolvedNodeId = resolvedNodeId,
        )
    }
}
