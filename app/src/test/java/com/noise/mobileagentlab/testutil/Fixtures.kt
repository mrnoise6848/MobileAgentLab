package com.noise.mobileagentlab.testutil

import com.noise.mobileagentlab.agent.domain.model.AgentRun
import com.noise.mobileagentlab.agent.domain.model.AgentStep
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.NodeBounds
import com.noise.mobileagentlab.agent.domain.model.NodeCapabilities
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.agent.domain.model.UiNode
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState
import com.noise.mobileagentlab.agent.domain.normalize.UiNormalizer
import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import com.noise.mobileagentlab.agent.domain.verify.StateDiff
import com.noise.mobileagentlab.agent.domain.verify.VerificationSummary

/** Builders shared by the domain unit tests (no Android, no fakes with state). */

fun uiNode(
    id: String,
    label: String = "",
    text: String = label,
    contentDescription: String = label,
    stateDescription: String = "",
    packageName: String = SafetyPolicy.DEMO_TARGET_PACKAGE,
    className: String = "android.widget.TextView",
    clickable: Boolean = false,
    longClickable: Boolean = false,
    editable: Boolean = false,
    scrollable: Boolean = false,
    checkable: Boolean = false,
    checked: Boolean = false,
    enabled: Boolean = true,
    visible: Boolean = true,
    sensitive: Boolean = false,
    viewId: String? = null,
    depth: Int = 1,
    bounds: NodeBounds = NodeBounds(0, 0, 200, 80),
): UiNode = UiNode(
    id = id,
    parentId = null,
    childIds = emptyList(),
    depth = depth,
    className = className,
    packageName = packageName,
    text = text,
    contentDescription = contentDescription,
    stateDescription = stateDescription,
    viewId = viewId,
    bounds = bounds,
    capabilities = NodeCapabilities(
        clickable = clickable,
        longClickable = longClickable,
        editable = editable,
        scrollable = scrollable,
        checkable = checkable,
        checked = checked,
        enabled = enabled,
        visible = visible,
    ),
    sensitive = sensitive,
)

fun demoSnapshot(
    vararg nodes: UiNode,
    pkg: String? = SafetyPolicy.DEMO_TARGET_PACKAGE,
    sequence: Long = 1L,
    truncated: Boolean = false,
): UiSnapshot = UiSnapshot(
    sequence = sequence,
    packageName = pkg,
    className = "android.app.Activity",
    nodes = nodes.toList(),
    truncated = truncated,
)

/** A normalized state built the same way the production pipeline builds it. */
fun demoState(vararg nodes: UiNode): CompactUiState = UiNormalizer.normalize(demoSnapshot(*nodes))

fun verification(
    passed: Boolean,
    attempts: Int = 1,
    detail: String = "checked",
    expectation: String = "NODE_VISIBLE \"x\"",
): VerificationSummary = VerificationSummary(
    passed = passed,
    expectation = expectation,
    detail = detail,
    attempts = attempts,
    elapsedMs = 5L,
    diff = StateDiff.EMPTY,
)

fun testStep(
    index: Int,
    status: StepStatus = StepStatus.SUCCESS,
    goalIndex: Int = 0,
    actionLabel: String = "CLICK \"Settings\"",
    planMs: Long = 10L,
    executeMs: Long = 10L,
    verifyMs: Long = 10L,
    verification: VerificationSummary? = null,
    failureReason: FailureReason? = null,
    detail: String = "",
): AgentStep = AgentStep(
    index = index,
    goalIndex = goalIndex,
    status = status,
    actionLabel = actionLabel,
    planMs = planMs,
    executeMs = executeMs,
    verifyMs = verifyMs,
    verification = verification,
    failureReason = failureReason,
    detail = detail,
)

fun testRun(
    runId: String = "run-1",
    taskId: String = "open_settings",
    taskTitle: String = "Open Settings",
    status: RunStatus = RunStatus.SUCCEEDED,
    steps: List<AgentStep> = emptyList(),
    maxSteps: Int = 15,
    retries: Int = 0,
    failureReason: FailureReason? = null,
    failureDetail: String? = null,
    startedAtMs: Long = 1_000L,
    finishedAtMs: Long = 2_000L,
    plannerId: String = "goal",
    isFaultTest: Boolean = false,
    expectedFailures: Set<FailureReason> = emptySet(),
): AgentRun = AgentRun(
    runId = runId,
    taskId = taskId,
    taskTitle = taskTitle,
    plannerId = plannerId,
    startedAtMs = startedAtMs,
    finishedAtMs = finishedAtMs,
    status = status,
    maxSteps = maxSteps,
    steps = steps,
    failureReason = failureReason,
    failureDetail = failureDetail,
    retries = retries,
    isFaultTest = isFaultTest,
    expectedFailures = expectedFailures,
)
