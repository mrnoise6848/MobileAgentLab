package com.noise.mobileagentlab.agent.domain.evaluation

import com.noise.mobileagentlab.agent.domain.model.AgentRun
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.task.DemoTasks
import kotlin.math.pow
import kotlin.math.roundToInt

/** Phase 18 — live state of the evaluation suite run. */
sealed interface EvaluationState {
    data object Idle : EvaluationState

    data class Running(
        val completed: Int,
        val total: Int,
        val currentTaskTitle: String,
        val report: EvaluationReport?,
    ) : EvaluationState

    data class Finished(val report: EvaluationReport) : EvaluationState
}

/** Phase 18 — the recorded outcome of one evaluation task. */
data class EvaluationTaskResult(
    val taskId: String,
    val title: String,
    val isFaultTest: Boolean,
    /** For fault tests: true means it failed *as expected*. */
    val matchedExpectation: Boolean,
    val succeeded: Boolean,
    val steps: Int,
    val maxSteps: Int,
    val retries: Int,
    val durationMs: Long,
    val verificationFailures: Int,
    val failureReason: FailureReason?,
) {
    val verdict: String
        get() = if (matchedExpectation) {
            if (isFaultTest) "PASS (expected failure)" else "PASS"
        } else {
            "FAIL"
        }
}

/**
 * Phase 18 — a deterministic report computed from real, recorded runs only.
 * If a suite task has no recorded run it is simply absent from [results];
 * the report never invents numbers.
 */
data class EvaluationReport(
    val results: List<EvaluationTaskResult>,
    val suiteSize: Int,
    val plannerId: String,
) {
    val executed: Int get() = results.size
    val successful: Int get() = results.count { it.matchedExpectation }
    val successRate: Double
        get() = if (results.isEmpty()) 0.0 else successful.toDouble() / results.size
    val avgSteps: Double
        get() = if (results.isEmpty()) 0.0 else results.map { it.steps }.average()
    val avgRetries: Double
        get() = if (results.isEmpty()) 0.0 else results.map { it.retries }.average()
    val avgDurationMs: Double
        get() = if (results.isEmpty()) 0.0 else results.map { it.durationMs.toDouble() }.average()
    val totalVerificationFailures: Int get() = results.sumOf { it.verificationFailures }
    val totalDurationMs: Long get() = results.sumOf { it.durationMs }

    val isComplete: Boolean get() = results.size == suiteSize
}

/**
 * Phase 18 — builds an [EvaluationReport] from recorded runs.
 *
 * Pure and deterministic: same runs in, same report out. The suite order comes
 * from [DemoTasks.evaluation]; per task the *latest* recorded run wins so a
 * re-run replaces its earlier result.
 */
object EvaluationHarness {

    fun buildReport(
        runs: List<AgentRun>,
        suiteIds: List<String> = DemoTasks.evaluation.map { it.id },
        plannerId: String = runs.lastOrNull()?.plannerId ?: "unknown",
    ): EvaluationReport {
        val latestByTask = LinkedHashMap<String, AgentRun>()
        for (run in runs) {
            if (run.taskId in suiteIds) latestByTask[run.taskId] = run
        }

        val results = suiteIds.mapNotNull { id ->
            val run = latestByTask[id] ?: return@mapNotNull null
            EvaluationTaskResult(
                taskId = run.taskId,
                title = run.taskTitle,
                isFaultTest = run.isFaultTest,
                matchedExpectation = run.matchesExpectation,
                succeeded = run.succeeded,
                steps = run.stepCount,
                maxSteps = run.maxSteps,
                retries = run.retries,
                durationMs = run.durationMs,
                verificationFailures = run.verificationFailures,
                failureReason = run.failureReason,
            )
        }

        return EvaluationReport(
            results = results,
            suiteSize = suiteIds.size,
            plannerId = plannerId,
        )
    }

    /** One-screen plain-text report (also usable in docs / logs). */
    fun render(report: EvaluationReport): String = buildString {
        appendLine("Evaluation")
        appendLine()
        appendLine("Tasks ${report.suiteSize}")
        appendLine("Executed ${report.executed}")
        appendLine("Successful ${report.successful}")
        appendLine("Success Rate ${percent(report.successRate)}")
        appendLine("Average Steps ${report.avgSteps.format(1)}")
        appendLine("Average Retries ${report.avgRetries.format(1)}")
        appendLine("Average Run ${report.avgDurationMs.roundToInt()} ms")
        appendLine("Verification Failures ${report.totalVerificationFailures}")
        appendLine()
        for (r in report.results) {
            append(r.title).append(": ").append(r.verdict)
            append(" · steps ").append(r.steps)
            append(" · retries ").append(r.retries)
            append(" · ").append(r.durationMs).append(" ms")
            append(" · verification failures ").append(r.verificationFailures)
            r.failureReason?.let { append(" · ").append(it.name) }
            appendLine()
        }
    }

    private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

    private fun Double.format(digits: Int): String {
        val factor = 10.0.pow(digits)
        return ((this * factor).roundToInt() / factor).toString()
    }
}
