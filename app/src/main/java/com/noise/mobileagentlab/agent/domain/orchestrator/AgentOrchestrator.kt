package com.noise.mobileagentlab.agent.domain.orchestrator

import com.noise.mobileagentlab.agent.domain.model.AgentRun
import com.noise.mobileagentlab.agent.domain.model.AgentRunState
import com.noise.mobileagentlab.agent.domain.model.AgentStep
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState
import com.noise.mobileagentlab.agent.domain.normalize.UiNormalizer
import com.noise.mobileagentlab.agent.domain.planner.AgentPlanner
import com.noise.mobileagentlab.agent.domain.planner.PlannerOutcome
import com.noise.mobileagentlab.agent.domain.planner.PlanningRequest
import com.noise.mobileagentlab.agent.domain.planner.StepSummary
import com.noise.mobileagentlab.agent.domain.port.ActionExecutor
import com.noise.mobileagentlab.agent.domain.port.TargetLauncher
import com.noise.mobileagentlab.agent.domain.port.UiObserver
import com.noise.mobileagentlab.agent.domain.safety.ActionValidator
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import com.noise.mobileagentlab.agent.domain.verify.Verifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Tunables of the agent loop — all bounded, no unbounded retrying. */
data class OrchestratorConfig(
    val maxRetriesPerAction: Int = 2,
    val maxPlannerFailures: Int = 2,
    val maxValidationFailures: Int = 2,
    val observeRetries: Int = 1,
    val settleQuietMs: Long = 180L,
    val settleTimeoutMs: Long = 2_500L,
)

/**
 * Phase 12 — the agent loop.
 *
 * ```text
 * observe → normalize → (complete?) → plan → validate → execute → verify → …
 * ```
 *
 * Guarantees:
 *  - hard termination: `maxSteps`, bounded planner/validation failures
 *  - nothing executes after [requestStop]
 *  - the local safety policy is consulted before every execution
 *  - every step is recorded with its timings, verification and diff
 *  - cancellation is honoured between steps
 */
class AgentOrchestrator(
    private val observer: UiObserver,
    private val executor: ActionExecutor,
    private val verifier: Verifier,
    private val validator: ActionValidator,
    private val launcher: TargetLauncher,
    private val config: OrchestratorConfig = OrchestratorConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(AgentRunState())
    val state: StateFlow<AgentRunState> = _state.asStateFlow()

    private val active = AtomicBoolean(false)
    private val runCounter = AtomicInteger(0)

    @Volatile
    private var stopRequested = false

    val isActive: Boolean get() = active.get()

    /** Cooperative stop: no further action will be executed after this call. */
    fun requestStop() {
        stopRequested = true
    }

    suspend fun run(task: PlannedTask, planner: AgentPlanner): AgentRun {
        check(active.compareAndSet(false, true)) { "an agent run is already active" }
        stopRequested = false
        return try {
            withContext(Dispatchers.Default) { execute(task, planner) }
        } finally {
            active.set(false)
        }
    }

    private suspend fun execute(task: PlannedTask, planner: AgentPlanner): AgentRun {
        val startedAt = clock()
        val runId = "run-${startedAt}-${runCounter.incrementAndGet()}"
        val steps = ArrayList<AgentStep>()
        val history = ArrayList<StepSummary>()

        var status = RunStatus.RUNNING
        var failureReason: FailureReason? = null
        var failureDetail: String? = null
        var nextGoal = 0
        var retries = 0
        var actionAttempts = 0
        var plannerFailures = 0
        var validationFailures = 0
        var stepIndex = 0
        var lastState = CompactUiState.empty(task.targetPackage)

        /**
         * Phase 13 — recovery: let the UI settle, pull a fresh snapshot and let
         * the planner reason again from the NEW state (never replay a stale plan).
         */
        suspend fun refreshForRecovery() {
            observer.awaitQuietPeriod(config.settleQuietMs, config.settleTimeoutMs)
            delay(120)
            currentCoroutineContext().ensureActive()
        }

        _state.value = AgentRunState(
            status = RunStatus.RUNNING,
            runId = runId,
            taskId = task.id,
            taskTitle = task.title,
            plannerId = planner.id,
            stepIndex = 0,
            maxSteps = task.maxSteps,
            startedAtMs = startedAt,
            isFaultTest = task.isFaultTest,
            expectedFailures = task.expectedFailures,
        )

        fun publishProgress() {
            _state.value = _state.value.copy(
                stepIndex = stepIndex,
                steps = steps.toList(),
                screenLabel = lastState.screenLabel,
                elementCount = lastState.size,
            )
        }

        fun finish(): AgentRun {
            val finishedAt = clock()
            val run = AgentRun(
                runId = runId,
                taskId = task.id,
                taskTitle = task.title,
                plannerId = planner.id,
                startedAtMs = startedAt,
                finishedAtMs = finishedAt,
                status = status,
                maxSteps = task.maxSteps,
                steps = steps.toList(),
                failureReason = failureReason,
                failureDetail = failureDetail,
                retries = retries,
                isFaultTest = task.isFaultTest,
                expectedFailures = task.expectedFailures,
            )
            _state.value = _state.value.copy(
                status = status,
                steps = steps.toList(),
                stepIndex = steps.size,
                currentAction = null,
                failureReason = failureReason,
                failureDetail = failureDetail,
                finishedAtMs = finishedAt,
            )
            return run
        }

        // --- precondition 1: the service must be connected ----------------------
        if (observer.status.value != AccessibilityStatus.Connected) {
            failureReason = FailureReason.SERVICE_UNAVAILABLE
            failureDetail = "the accessibility service is not enabled"
            status = RunStatus.FAILED
            return finish()
        }

        // --- precondition 2: the allowlisted target must come to the front ------
        val launched = launcher.bringToFront(task.targetPackage)
        observer.awaitQuietPeriod(config.settleQuietMs, config.settleTimeoutMs)
        val foregroundPkg = observer.foreground.value?.packageName
        if (!launched && (foregroundPkg == null || foregroundPkg != task.targetPackage)) {
            failureReason = FailureReason.TARGET_LEFT_FOREGROUND
            failureDetail = "could not bring ${task.targetPackage} to the foreground"
            status = RunStatus.FAILED
            return finish()
        }

        // --- main loop ----------------------------------------------------------
        loop@ while (stepIndex < task.maxSteps) {
            if (stopRequested) {
                failureReason = FailureReason.USER_STOPPED
                status = RunStatus.STOPPED
                break
            }
            currentCoroutineContext().ensureActive()
            stepIndex++
            _state.value = _state.value.copy(
                stepIndex = stepIndex,
                currentAction = null,
            )

            // 1. observe (bounded retry when the window is momentarily unavailable)
            var snapshot = observer.observe()
            var observeFailures = 0
            while (snapshot == null && observeFailures < config.observeRetries) {
                observeFailures++
                delay(250)
                currentCoroutineContext().ensureActive()
                snapshot = observer.observe()
            }
            if (snapshot == null) {
                steps.add(
                    AgentStep(
                        index = stepIndex,
                        goalIndex = nextGoal,
                        status = StepStatus.OBSERVATION_FAILED,
                        actionLabel = "OBSERVE",
                        failureReason = FailureReason.NO_UI_OBSERVED,
                        detail = "no readable window after ${observeFailures + 1} attempt(s)",
                    ),
                )
                failureReason = FailureReason.NO_UI_OBSERVED
                failureDetail = "the accessibility service could not read the screen"
                status = RunStatus.FAILED
                publishProgress()
                break
            }
            if (observer.status.value != AccessibilityStatus.Connected) {
                failureReason = FailureReason.SERVICE_UNAVAILABLE
                failureDetail = "the accessibility service was disabled during the run"
                status = RunStatus.FAILED
                publishProgress()
                break
            }

            val state = UiNormalizer.normalize(snapshot)
            lastState = state
            publishProgress()

            // 2. execution boundary: only the allowlisted package may be operated on
            val snapshotPackage = snapshot.packageName
            val boundaryViolation = validator.policy.boundaryViolation(snapshotPackage, foregroundPkg)
            if (boundaryViolation != null) {
                steps.add(
                    AgentStep(
                        index = stepIndex,
                        goalIndex = nextGoal,
                        status = StepStatus.ABORTED,
                        actionLabel = "ABORT",
                        observedNodes = snapshot.nodeCount,
                        failureReason = boundaryViolation,
                        detail = "observed package=$snapshotPackage " +
                            "foreground=${foregroundPkg ?: "unknown"} is outside the allowed scope",
                    ),
                )
                failureReason = boundaryViolation
                failureDetail = "observed package=$snapshotPackage"
                status = RunStatus.FAILED
                publishProgress()
                break
            }

            // 3. plan
            val planStartedAt = clock()
            val outcome = try {
                planner.plan(
                    PlanningRequest(
                        task = task,
                        state = state,
                        nextGoalIndex = nextGoal,
                        stepIndex = stepIndex,
                        history = history.toList(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlannerOutcome.Failed(
                    FailureReason.PLANNER_ERROR,
                    "planner threw ${e.javaClass.simpleName}",
                )
            }
            val planMs = (clock() - planStartedAt).coerceAtLeast(0L)

            val proposal = when (outcome) {
                is PlannerOutcome.Complete -> {
                    val verification = verifier.verify(task.completion, snapshot, beforeState = state)
                    steps.add(
                        AgentStep(
                            index = stepIndex,
                            goalIndex = nextGoal,
                            status = if (verification.passed) StepStatus.SUCCESS else StepStatus.NOT_VERIFIED,
                            actionLabel = "COMPLETE TASK",
                            planReason = outcome.reason,
                            planMs = planMs,
                            verifyMs = verification.elapsedMs,
                            observedNodes = snapshot.nodeCount,
                            verification = verification.summary(),
                            diff = verification.diff,
                            failureReason = verification.failureReason,
                            detail = outcome.reason,
                        ),
                    )
                    if (verification.passed) {
                        status = RunStatus.SUCCEEDED
                    } else {
                        failureReason = FailureReason.VERIFICATION_FAILED
                        failureDetail = "task completion not observed: ${verification.detail}"
                        status = RunStatus.FAILED
                    }
                    publishProgress()
                    break@loop
                }

                is PlannerOutcome.Failed -> {
                    steps.add(
                        AgentStep(
                            index = stepIndex,
                            goalIndex = nextGoal,
                            status = StepStatus.PLANNING_FAILED,
                            actionLabel = "PLAN",
                            planReason = outcome.detail,
                            planMs = planMs,
                            observedNodes = snapshot.nodeCount,
                            failureReason = outcome.reason,
                            detail = outcome.detail,
                        ),
                    )
                    plannerFailures++
                    publishProgress()
                    if (plannerFailures > config.maxPlannerFailures) {
                        failureReason = outcome.reason
                        failureDetail = outcome.detail
                        status = RunStatus.FAILED
                        break@loop
                    }
                    continue@loop
                }

                is PlannerOutcome.Proposal -> outcome
            }

            // 4. validate against the LOCAL safety policy
            val validation = validator.validate(
                action = proposal.action,
                snapshot = snapshot,
                foregroundPackage = foregroundPkg ?: snapshotPackage,
            )
            if (validation is com.noise.mobileagentlab.agent.domain.safety.ValidationOutcome.Rejected) {
                val label = "${validation.reason.name}: ${validation.detail}"
                steps.add(
                    AgentStep(
                        index = stepIndex,
                        goalIndex = proposal.goalIndex,
                        status = StepStatus.REJECTED,
                        actionLabel = proposal.action.traceLabel,
                        action = proposal.action,
                        expectation = proposal.expectation,
                        planReason = proposal.reason,
                        planMs = planMs,
                        observedNodes = snapshot.nodeCount,
                        validationDetail = label,
                        failureReason = validation.reason,
                        detail = validation.detail,
                    ),
                )
                validationFailures++
                publishProgress()
                if (validationFailures > config.maxValidationFailures) {
                    failureReason = validation.reason
                    failureDetail = validation.detail
                    status = RunStatus.FAILED
                    break@loop
                }
                continue@loop
            }

            // 5. stop is checked again right before touching the device
            if (stopRequested) {
                failureReason = FailureReason.USER_STOPPED
                status = RunStatus.STOPPED
                break
            }
            _state.value = _state.value.copy(currentAction = proposal.action.traceLabel)

            val execution = executor.execute(proposal.action, snapshot)
            if (!execution.success) {
                steps.add(
                    AgentStep(
                        index = stepIndex,
                        goalIndex = proposal.goalIndex,
                        status = StepStatus.EXECUTION_FAILED,
                        actionLabel = proposal.action.traceLabel,
                        action = proposal.action,
                        expectation = proposal.expectation,
                        planReason = proposal.reason,
                        planMs = planMs,
                        executeMs = execution.elapsedMs,
                        observedNodes = snapshot.nodeCount,
                        executionDetail = execution.detail,
                        failureReason = execution.reason ?: FailureReason.EXECUTION_FAILED,
                        detail = execution.detail,
                    ),
                )
                retries++
                actionAttempts++
                publishProgress()
                if (actionAttempts > config.maxRetriesPerAction) {
                    failureReason = execution.reason ?: FailureReason.EXECUTION_FAILED
                    failureDetail = "${execution.detail} (after $actionAttempts attempt(s))"
                    status = RunStatus.FAILED
                    break@loop
                }
                refreshForRecovery()
                continue@loop
            }

            // 6. verify — the API returning true is NOT enough
            val verification = verifier.verify(proposal.expectation, snapshot, beforeState = state)
            val stepStatus = if (verification.passed) StepStatus.SUCCESS else StepStatus.NOT_VERIFIED
            steps.add(
                AgentStep(
                    index = stepIndex,
                    goalIndex = proposal.goalIndex,
                    status = stepStatus,
                    actionLabel = proposal.action.traceLabel,
                    action = proposal.action,
                    expectation = proposal.expectation,
                    planReason = proposal.reason,
                    planMs = planMs,
                    executeMs = execution.elapsedMs,
                    verifyMs = verification.elapsedMs,
                    observedNodes = snapshot.nodeCount,
                    executionDetail = execution.detail,
                    verification = verification.summary(),
                    diff = verification.diff,
                    failureReason = verification.failureReason,
                    detail = verification.detail,
                ),
            )
            history.add(
                StepSummary(
                    stepIndex = stepIndex,
                    goalIndex = proposal.goalIndex,
                    action = proposal.action.traceLabel,
                    success = verification.passed,
                    detail = execution.detail,
                    verification = verification.detail,
                ),
            )
            lastState = verification.after
            publishProgress()

            if (!verification.passed) {
                // Phase 13 — verification failed: recover instead of assuming
                // the action worked. Refresh the state and re-plan the same goal
                // from scratch, bounded by maxRetriesPerAction.
                retries++
                actionAttempts++
                if (actionAttempts <= config.maxRetriesPerAction && !stopRequested) {
                    publishProgress()
                    refreshForRecovery()
                    continue@loop
                }
                failureReason = FailureReason.VERIFICATION_FAILED
                failureDetail = "${verification.detail} (after $actionAttempts attempt(s))"
                status = RunStatus.FAILED
                break@loop
            }

            actionAttempts = 0

            if (proposal.completesGoal) {
                nextGoal = maxOf(nextGoal, proposal.goalIndex + 1)
            }

            // task completion is checked after every verified step
            if (task.completion.evaluate(verification.before, verification.after).passed) {
                status = RunStatus.SUCCEEDED
                break@loop
            }
        }

        if (status == RunStatus.RUNNING) {
            failureReason = FailureReason.STEP_LIMIT_EXCEEDED
            failureDetail = "reached maxSteps=${task.maxSteps}"
            status = RunStatus.FAILED
        }

        return finish()
    }
}
