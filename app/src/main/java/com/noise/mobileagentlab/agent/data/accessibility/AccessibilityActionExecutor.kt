package com.noise.mobileagentlab.agent.data.accessibility

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityService
import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.action.ScrollDirection
import com.noise.mobileagentlab.agent.domain.model.ExecutionResult
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.NodeBounds
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.port.ActionExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 7 — executes validated actions with the Accessibility APIs.
 *
 * Failure discipline:
 *  - never assumes success: `performAction` returning false is a failure
 *  - re-resolves the target on a fresh tree (stale ids are detected, and a
 *    label-based fallback is attempted exactly once)
 *  - unexpected exceptions become structured [ExecutionResult] failures
 *  - runs on the main thread, which owns the window's node tree
 */
class AccessibilityActionExecutor : ActionExecutor {

    override suspend fun execute(action: AgentAction, snapshot: UiSnapshot): ExecutionResult =
        withContext(Dispatchers.Main) {
            val startedAt = SystemClock.elapsedRealtime()

            if (!AccessibilityBridge.isAvailable()) {
                return@withContext ExecutionResult.failure(
                    FailureReason.SERVICE_UNAVAILABLE,
                    "service not connected",
                )
            }
            val service = AccessibilityBridge.serviceOrNull()
                ?: return@withContext ExecutionResult.failure(
                    FailureReason.SERVICE_UNAVAILABLE,
                    "service instance cleared",
                )
            val root = AccessibilityBridge.rootNode()
                ?: return@withContext ExecutionResult.failure(
                    FailureReason.NO_UI_OBSERVED,
                    "no active window",
                )

            try {
                val result = perform(action, root, service, snapshot)
                result.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
            } catch (e: Exception) {
                ExecutionResult.failure(
                    FailureReason.EXECUTION_FAILED,
                    "exception: ${e.javaClass.simpleName}",
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                )
            }
        }

    private fun perform(
        action: AgentAction,
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        snapshot: UiSnapshot,
    ): ExecutionResult = when (action) {
        AgentAction.Back -> global(service, AccessibilityService.GLOBAL_ACTION_BACK, "back")
        AgentAction.Home -> global(service, AccessibilityService.GLOBAL_ACTION_HOME, "home")

        is AgentAction.Click -> nodeAction(
            action = action,
            root = root,
            snapshot = snapshot,
            accepts = { it.isClickable },
            description = "click",
        ) { node -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }

        is AgentAction.LongClick -> nodeAction(
            action = action,
            root = root,
            snapshot = snapshot,
            accepts = { it.isLongClickable },
            description = "long-click",
        ) { node -> node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }

        is AgentAction.TypeText -> typeText(action, root, snapshot)

        is AgentAction.Scroll -> scroll(action, root)
    }

    private fun global(
        service: AccessibilityService,
        globalAction: Int,
        label: String,
    ): ExecutionResult {
        val ok = runCatching { service.performGlobalAction(globalAction) }.getOrDefault(false)
        return if (ok) {
            ExecutionResult.ok("global $label", null, false, 0L)
        } else {
            ExecutionResult.failure(FailureReason.EXECUTION_FAILED, "global $label refused")
        }
    }

    private fun nodeAction(
        action: AgentAction,
        root: AccessibilityNodeInfo,
        snapshot: UiSnapshot,
        accepts: (AccessibilityNodeInfo) -> Boolean,
        description: String,
        block: (AccessibilityNodeInfo) -> Boolean,
    ): ExecutionResult {
        val located = locate(
            root = root,
            targetId = action.targetId,
            expectedLabel = expectedLabelOf(action),
            expectedBounds = expectedBoundsOf(action),
            accepts = accepts,
            snapshot = snapshot,
        )
        if (located.error != null) {
            return ExecutionResult.failure(located.error, located.errorDetail ?: description)
        }
        val node = located.node
            ?: return ExecutionResult.failure(FailureReason.TARGET_NOT_FOUND, "$description target missing")

        val ok = runCatching { block(node) }.getOrDefault(false)
        return if (ok) {
            ExecutionResult.ok("$description dispatched", located.resolvedId, located.usedFallback, 0L)
        } else {
            ExecutionResult.failure(
                FailureReason.EXECUTION_FAILED,
                "$description returned false",
                resolvedNodeId = located.resolvedId,
            )
        }
    }

    private fun typeText(
        action: AgentAction.TypeText,
        root: AccessibilityNodeInfo,
        snapshot: UiSnapshot,
    ): ExecutionResult {
        val located = locate(
            root = root,
            targetId = action.targetId,
            expectedLabel = action.expectedLabel,
            expectedBounds = null,
            accepts = { node ->
                node.actionList?.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } == true
            },
            snapshot = snapshot,
        )
        located.error?.let { return ExecutionResult.failure(it, located.errorDetail ?: "type") }
        val node = located.node
            ?: return ExecutionResult.failure(FailureReason.TARGET_NOT_FOUND, "input target missing")

        val existing = node.text?.toString().orEmpty()
        val value = if (action.clearFirst || existing.isEmpty()) action.text else existing + action.text

        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val ok = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)

        return if (ok) {
            ExecutionResult.ok("text set", located.resolvedId, located.usedFallback, 0L)
        } else {
            ExecutionResult.failure(
                FailureReason.EXECUTION_FAILED,
                "set text returned false",
                resolvedNodeId = located.resolvedId,
            )
        }
    }

    private fun scroll(action: AgentAction.Scroll, root: AccessibilityNodeInfo): ExecutionResult {
        val node = if (action.targetId != null) {
            NodeResolver.resolveById(root, action.targetId)?.takeIf { it.isScrollable }
        } else {
            null
        } ?: NodeResolver.firstScrollable(root)
        ?: return ExecutionResult.failure(
            FailureReason.TARGET_NOT_FOUND,
            "no scrollable container on screen",
        )

        val scrollAction = if (action.direction == ScrollDirection.FORWARD) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val ok = runCatching { node.performAction(scrollAction) }.getOrDefault(false)
        return if (ok) {
            ExecutionResult.ok("scrolled ${action.direction.name.lowercase()}", null, false, 0L)
        } else {
            ExecutionResult.failure(FailureReason.EXECUTION_FAILED, "scroll refused")
        }
    }

    // --- target resolution ------------------------------------------------------

    private data class Located(
        val node: AccessibilityNodeInfo? = null,
        val resolvedId: String? = null,
        val usedFallback: Boolean = false,
        val error: FailureReason? = null,
        val errorDetail: String? = null,
    )

    private fun locate(
        root: AccessibilityNodeInfo,
        targetId: String?,
        expectedLabel: String?,
        expectedBounds: NodeBounds?,
        accepts: (AccessibilityNodeInfo) -> Boolean,
        snapshot: UiSnapshot,
    ): Located {
        if (targetId != null) {
            val direct = NodeResolver.resolveById(root, targetId)
            if (direct != null && accepts(direct)) {
                val plannedLabel: String? = expectedLabel
                val actualLabel = NodeResolver.labelOf(direct)
                val labelMatches = plannedLabel.isNullOrBlank() ||
                    actualLabel.equals(plannedLabel, ignoreCase = true) ||
                    actualLabel.contains(plannedLabel, ignoreCase = true)
                val boundsMatch = expectedBounds == null ||
                    NodeResolver.boundsOf(direct).toBounds().overlaps(expectedBounds)
                if (labelMatches && boundsMatch) {
                    return Located(direct, targetId, false)
                }
            }
            // path id stale → single label-based recovery attempt
            if (!expectedLabel.isNullOrBlank()) {
                val recovered = NodeResolver.findByLabel(root, expectedLabel, accepts)
                if (recovered != null) {
                    return Located(recovered, null, true)
                }
                return Located(
                    error = if (direct == null) FailureReason.TARGET_NOT_FOUND
                    else FailureReason.UI_CHANGED_DURING_ACTION,
                    errorDetail = "planned node changed and no label match (snapshot #${snapshot.sequence})",
                )
            }
            if (direct == null) {
                return Located(error = FailureReason.TARGET_NOT_FOUND, errorDetail = "node=$targetId missing")
            }
            return Located(error = FailureReason.UI_CHANGED_DURING_ACTION, errorDetail = "node=$targetId changed")
        }

        // global action without target id (e.g. untargeted scroll) is handled elsewhere
        return Located(error = FailureReason.TARGET_NOT_FOUND, errorDetail = "no target id")
    }

    private fun expectedLabelOf(action: AgentAction): String? = when (action) {
        is AgentAction.Click -> action.expectedLabel
        is AgentAction.LongClick -> action.expectedLabel
        is AgentAction.TypeText -> action.expectedLabel
        else -> null
    }

    private fun expectedBoundsOf(action: AgentAction): NodeBounds? = when (action) {
        is AgentAction.Click -> action.expectedBounds
        else -> null
    }

    private fun android.graphics.Rect.toBounds(): NodeBounds =
        NodeBounds(left, top, right, bottom)
}
