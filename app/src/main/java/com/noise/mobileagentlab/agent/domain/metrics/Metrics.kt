package com.noise.mobileagentlab.agent.domain.metrics

import com.noise.mobileagentlab.agent.domain.model.AgentRun
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus

/** Phase 17 — metrics of a single run, derived only from recorded steps. */
data class RunMetrics(
    val runId: String,
    val taskId: String,
    val taskTitle: String,
    val status: RunStatus,
    val succeeded: Boolean,
    val stepCount: Int,
    val maxSteps: Int,
    val retries: Int,
    val durationMs: Long,
    val planMsTotal: Long,
    val executeMsTotal: Long,
    val verifyMsTotal: Long,
    val planMsAvg: Long,
    val executeMsAvg: Long,
    val verifyMsAvg: Long,
    val verificationAttempts: Int,
    val verificationFailures: Int,
    val verificationSuccessRate: Double,
    val rejectedSteps: Int,
    val failureReason: String?,
)

/** Phase 17 — aggregate over real runs. Empty input yields zeros, never fakes. */
data class AggregateMetrics(
    val totalRuns: Int = 0,
    val successfulRuns: Int = 0,
    val successRate: Double = 0.0,
    val avgSteps: Double = 0.0,
    val avgRetries: Double = 0.0,
    val avgPlanLatencyMs: Double = 0.0,
    val avgActionLatencyMs: Double = 0.0,
    val avgVerifyLatencyMs: Double = 0.0,
    val avgRunDurationMs: Double = 0.0,
    val verificationAttempts: Int = 0,
    val verificationFailures: Int = 0,
    val verificationSuccessRate: Double = 0.0,
) {
    companion object {
        val EMPTY = AggregateMetrics()
    }
}

object MetricsAggregator {

    fun forRun(run: AgentRun): RunMetrics {
        val steps = run.steps
        val withVerification = steps.filter { it.verification != null }
        val verified = withVerification.count { it.verification?.passed == true }

        val planTotal = steps.sumOf { it.planMs }
        val executeTotal = steps.sumOf { it.executeMs }
        val verifyTotal = steps.sumOf { it.verifyMs }
        val actionCount = steps.count { it.executeMs > 0 }

        return RunMetrics(
            runId = run.runId,
            taskId = run.taskId,
            taskTitle = run.taskTitle,
            status = run.status,
            succeeded = run.succeeded,
            stepCount = run.stepCount,
            maxSteps = run.maxSteps,
            retries = run.retries,
            durationMs = run.durationMs,
            planMsTotal = planTotal,
            executeMsTotal = executeTotal,
            verifyMsTotal = verifyTotal,
            planMsAvg = avg(planTotal, steps.size),
            executeMsAvg = avg(executeTotal, actionCount),
            verifyMsAvg = avg(verifyTotal, withVerification.size),
            verificationAttempts = withVerification.sumOf { it.verification?.attempts ?: 0 },
            verificationFailures = withVerification.size - verified,
            verificationSuccessRate = rate(verified, withVerification.size),
            rejectedSteps = steps.count { it.status == StepStatus.REJECTED },
            failureReason = run.failureReason?.name,
        )
    }

    fun aggregate(runs: List<AgentRun>): AggregateMetrics {
        if (runs.isEmpty()) return AggregateMetrics.EMPTY
        val metrics = runs.map { forRun(it) }
        val attempts = metrics.sumOf { it.verificationAttempts }
        val failures = metrics.sumOf { it.verificationFailures }

        return AggregateMetrics(
            totalRuns = runs.size,
            successfulRuns = metrics.count { it.succeeded },
            successRate = rate(metrics.count { it.succeeded }, runs.size),
            avgSteps = metrics.map { it.stepCount }.average(),
            avgRetries = metrics.map { it.retries }.average(),
            avgPlanLatencyMs = metrics.map { it.planMsAvg.toDouble() }.average(),
            avgActionLatencyMs = metrics.map { it.executeMsAvg.toDouble() }.average(),
            avgVerifyLatencyMs = metrics.map { it.verifyMsAvg.toDouble() }.average(),
            avgRunDurationMs = metrics.map { it.durationMs.toDouble() }.average(),
            verificationAttempts = attempts,
            verificationFailures = failures,
            verificationSuccessRate = rate(attempts - failures, attempts),
        )
    }

    private fun avg(total: Long, count: Int): Long = if (count <= 0) 0L else total / count

    private fun rate(part: Int, whole: Int): Double = if (whole <= 0) 0.0 else part.toDouble() / whole
}
