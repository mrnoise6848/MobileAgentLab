package com.noise.mobileagentlab.agent.domain.metrics

import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.testutil.testRun
import com.noise.mobileagentlab.testutil.testStep
import com.noise.mobileagentlab.testutil.verification
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsTest {

    private val run = testRun(
        status = RunStatus.FAILED,
        retries = 2,
        steps = listOf(
            testStep(
                index = 1,
                status = StepStatus.SUCCESS,
                planMs = 10,
                executeMs = 20,
                verifyMs = 30,
                verification = verification(passed = true, attempts = 3),
            ),
            testStep(
                index = 2,
                status = StepStatus.NOT_VERIFIED,
                planMs = 14,
                executeMs = 0,
                verifyMs = 40,
                verification = verification(passed = false, attempts = 1),
            ),
            testStep(
                index = 3,
                status = StepStatus.REJECTED,
                planMs = 6,
                executeMs = 0,
                verifyMs = 0,
                verification = null,
            ),
        ),
        failureReason = com.noise.mobileagentlab.agent.domain.model.FailureReason.VERIFICATION_FAILED,
    )

    @Test
    fun `run metrics are derived only from recorded steps`() {
        val metrics = MetricsAggregator.forRun(run)
        assertEquals("run-1", metrics.runId)
        assertEquals(3, metrics.stepCount)
        assertEquals(2, metrics.retries)
        assertEquals(30L, metrics.planMsTotal)
        assertEquals(20L, metrics.executeMsTotal)
        assertEquals(70L, metrics.verifyMsTotal)
        assertEquals(10L, metrics.planMsAvg)
        assertEquals(20L, metrics.executeMsAvg) // only steps with executeMs > 0
        assertEquals(35L, metrics.verifyMsAvg) // only steps with verification
        assertEquals(1, metrics.rejectedSteps)
        assertEquals("VERIFICATION_FAILED", metrics.failureReason)
    }

    @Test
    fun `verification counts are in steps not poll attempts`() {
        // Regression: attempts used to sum poll retries while failures counted
        // steps, so "passed/attempts" was meaningless (e.g. 3-1 = 2 of 3).
        val metrics = MetricsAggregator.forRun(run)
        assertEquals(2, metrics.verificationAttempts) // 2 verification steps ran
        assertEquals(1, metrics.verificationFailures) // 1 of them did not pass
        assertEquals(0.5, metrics.verificationSuccessRate, 0.0001)
    }

    @Test
    fun `rate agrees with attempts minus failures`() {
        val metrics = MetricsAggregator.forRun(run)
        val implied =
            (metrics.verificationAttempts - metrics.verificationFailures).toDouble() /
                metrics.verificationAttempts
        assertEquals(metrics.verificationSuccessRate, implied, 0.0001)
    }

    @Test
    fun `successful run reports full verification`() {
        val ok = testRun(
            status = RunStatus.SUCCEEDED,
            steps = listOf(
                testStep(index = 1, verification = verification(passed = true)),
                testStep(index = 2, verification = verification(passed = true)),
            ),
        )
        val metrics = MetricsAggregator.forRun(ok)
        assertEquals(2, metrics.verificationAttempts)
        assertEquals(0, metrics.verificationFailures)
        assertEquals(1.0, metrics.verificationSuccessRate, 0.0001)
        assertEquals(0, metrics.rejectedSteps)
        assertEquals(null, metrics.failureReason)
    }

    @Test
    fun `run without steps reports zeros not NaN`() {
        val metrics = MetricsAggregator.forRun(testRun())
        assertEquals(0L, metrics.planMsAvg)
        assertEquals(0L, metrics.executeMsAvg)
        assertEquals(0L, metrics.verifyMsAvg)
        assertEquals(0, metrics.verificationAttempts)
        assertEquals(0.0, metrics.verificationSuccessRate, 0.0001)
    }

    @Test
    fun `empty aggregate is EMPTY`() {
        assertEquals(AggregateMetrics.EMPTY, MetricsAggregator.aggregate(emptyList()))
        assertEquals(0, MetricsAggregator.aggregate(emptyList()).totalRuns)
        assertEquals(0.0, MetricsAggregator.aggregate(emptyList()).successRate, 0.0001)
    }

    @Test
    fun `aggregate sums real runs`() {
        val ok = testRun(
            runId = "r2",
            status = RunStatus.SUCCEEDED,
            steps = listOf(
                testStep(index = 1, verification = verification(passed = true)),
            ),
        )
        val aggregate = MetricsAggregator.aggregate(listOf(run, ok))
        assertEquals(2, aggregate.totalRuns)
        assertEquals(1, aggregate.successfulRuns)
        assertEquals(0.5, aggregate.successRate, 0.0001)
        assertEquals(2.0, aggregate.avgSteps, 0.0001) // (3 + 1) / 2
        assertEquals(1.0, aggregate.avgRetries, 0.0001)
        // 3 verification steps total, 2 passed
        assertEquals(3, aggregate.verificationAttempts)
        assertEquals(1, aggregate.verificationFailures)
        assertEquals(2.0 / 3.0, aggregate.verificationSuccessRate, 0.0001)
    }
}
