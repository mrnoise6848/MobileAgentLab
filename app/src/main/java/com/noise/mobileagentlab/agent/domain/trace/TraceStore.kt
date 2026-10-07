package com.noise.mobileagentlab.agent.domain.trace

import com.noise.mobileagentlab.agent.domain.model.AgentRun
import com.noise.mobileagentlab.agent.domain.model.AgentStep
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 15 — bounded, in-memory execution trace store.
 *
 * Retention is deliberately capped (Phase 23): traces are demo artifacts, not a
 * database. Only finished runs are kept, and every rendered line passes through
 * [TraceRedactor].
 */
class TraceStore(
    private val maxRuns: Int = DEFAULT_MAX_RUNS,
) {
    private val lock = Any()
    private val storedRuns = ArrayDeque<AgentRun>()
    private val _runs = MutableStateFlow<List<AgentRun>>(emptyList())
    val runs: StateFlow<List<AgentRun>> = _runs.asStateFlow()

    fun record(run: AgentRun) {
        synchronized(lock) {
            storedRuns.addLast(run)
            while (storedRuns.size > maxRuns) storedRuns.removeFirst()
            _runs.value = storedRuns.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            storedRuns.clear()
            _runs.value = emptyList()
        }
    }

    /** Human-readable trace, e.g. for the inspector's "trace" view. */
    fun render(run: AgentRun): String = buildString {
        append("Run ").append(run.runId).append('\n')
        append("Task: ").append(run.taskTitle).append(" (").append(run.taskId).append(")\n")
        append("Planner: ").append(run.plannerId).append('\n')
        append("Status: ").append(run.status.name)
        append(" | steps ").append(run.stepCount).append('/').append(run.maxSteps)
        append(" | duration ").append(run.durationMs).append(" ms")
        append(" | retries ").append(run.retries)
        append(" | verified ").append(run.verifiedStepCount).append('/').append(run.stepCount)
        append('\n')
        run.failureReason?.let {
            append("Failure: ").append(it.name).append(" — ")
            append(TraceRedactor.mask(run.failureDetail ?: it.message)).append('\n')
        }
        append('\n')
        for (step in run.steps) {
            append(renderStep(step)).append('\n')
        }
    }

    private fun renderStep(step: AgentStep): String = buildString {
        append("Step ").append(step.index)
        append(" [").append(step.status.name).append("] ")
        append(TraceRedactor.mask(step.actionLabel))
        append(" · goal ").append(step.goalIndex + 1)
        append(" · plan ").append(step.planMs).append("ms")
        append(" · exec ").append(step.executeMs).append("ms")
        append(" · verify ").append(step.verifyMs).append("ms")
        if (step.retries > 0) append(" · retries ").append(step.retries)
        append(" · observed ").append(step.observedNodes).append(" nodes")
        append('\n')
        append("    reason: ").append(TraceRedactor.mask(step.planReason)).append('\n')
        if (step.expectation != null) {
            append("    expected: ").append(step.expectation.describe).append('\n')
        }
        step.verification?.let {
            append("    verification: ")
            append(if (it.passed) "PASS" else "FAIL")
            append(" (").append(it.attempts).append(" attempt(s), ")
            append(it.elapsedMs).append("ms) — ")
            append(TraceRedactor.mask(it.detail)).append('\n')
            append("    ").append(it.diff.render().replace("\n", "\n    ")).append('\n')
        }
        step.failureReason?.let {
            append("    failure: ").append(it.name).append(" — ")
            append(TraceRedactor.mask(step.detail)).append('\n')
        }
    }

    companion object {
        const val DEFAULT_MAX_RUNS = 20
    }
}
