package com.noise.mobileagentlab.agent.domain.model

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.verify.Expectation
import com.noise.mobileagentlab.agent.domain.verify.StateDiff
import com.noise.mobileagentlab.agent.domain.verify.VerificationSummary

enum class RunStatus { IDLE, RUNNING, SUCCEEDED, FAILED, STOPPED }

enum class StepStatus {
    /** executed and verified */
    SUCCESS,

    /** executed but the expected UI change did not happen */
    NOT_VERIFIED,

    /** rejected by the local safety policy */
    REJECTED,

    /** the platform refused or failed to dispatch the action */
    EXECUTION_FAILED,

    /** the planner could not produce a valid action */
    PLANNING_FAILED,

    /** the screen could not be observed */
    OBSERVATION_FAILED,

    /** stopped before acting (user stop / termination) */
    ABORTED,
}

/**
 * Phase 12/15 — one recorded step of a run.
 * Contains only plain data: safe to keep in traces, metrics and the UI.
 */
data class AgentStep(
    val index: Int,
    val goalIndex: Int,
    val status: StepStatus,
    val actionLabel: String,
    val action: AgentAction? = null,
    val expectation: Expectation? = null,
    val planReason: String = "",
    val planMs: Long = 0L,
    val executeMs: Long = 0L,
    val verifyMs: Long = 0L,
    val retries: Int = 0,
    val observedNodes: Int = 0,
    val validationDetail: String = "",
    val executionDetail: String = "",
    val verification: VerificationSummary? = null,
    val diff: StateDiff? = null,
    val failureReason: FailureReason? = null,
    val detail: String = "",
) {
    val verified: Boolean get() = verification?.passed == true
}

/** A finished run — the unit metrics and evaluation are computed from. */
data class AgentRun(
    val runId: String,
    val taskId: String,
    val taskTitle: String,
    val plannerId: String,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val status: RunStatus,
    val maxSteps: Int,
    val steps: List<AgentStep>,
    val failureReason: FailureReason?,
    val failureDetail: String?,
    val retries: Int,
    val isFaultTest: Boolean = false,
    val expectedFailures: Set<FailureReason> = emptySet(),
) {
    val succeeded: Boolean get() = status == RunStatus.SUCCEEDED
    val stepCount: Int get() = steps.size
    val durationMs: Long get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)
    val verifiedStepCount: Int get() = steps.count { it.verified }
    val verificationFailures: Int get() = steps.count { it.status == StepStatus.NOT_VERIFIED }

    /** Evaluation view: a controlled-failure task passes when it failed as expected. */
    val matchesExpectation: Boolean
        get() = if (isFaultTest) {
            failureReason != null && failureReason in expectedFailures
        } else {
            succeeded
        }
}

/** Live state pushed to the Run Inspector UI. */
data class AgentRunState(
    val status: RunStatus = RunStatus.IDLE,
    val runId: String? = null,
    val taskId: String? = null,
    val taskTitle: String = "",
    val plannerId: String = "",
    val stepIndex: Int = 0,
    val maxSteps: Int = 0,
    val steps: List<AgentStep> = emptyList(),
    val currentAction: String? = null,
    val failureReason: FailureReason? = null,
    val failureDetail: String? = null,
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long = 0L,
    val screenLabel: String = "",
    val elementCount: Int = 0,
    /** Phase 19 — controlled failure task: the failure is the expected result. */
    val isFaultTest: Boolean = false,
    val expectedFailures: Set<FailureReason> = emptySet(),
) {
    val isRunning: Boolean get() = status == RunStatus.RUNNING

    /** For fault tests: the run failed with one of the expected reasons. */
    val failedAsExpected: Boolean
        get() = isFaultTest && failureReason != null && failureReason in expectedFailures
}
