package com.noise.mobileagentlab.agent.domain.evaluation

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.agent.domain.task.DemoTasks
import com.noise.mobileagentlab.testutil.testRun
import com.noise.mobileagentlab.testutil.testStep
import com.noise.mobileagentlab.testutil.verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationHarnessTest {

    private val suite = listOf("t1", "t2", "t3")

    @Test
    fun `empty runs give an empty complete-free report`() {
        val report = EvaluationHarness.buildReport(emptyList(), suiteIds = suite)
        assertEquals(3, report.suiteSize)
        assertEquals(0, report.executed)
        assertEquals(0, report.successful)
        assertEquals(0.0, report.successRate, 0.0001)
        assertEquals(0.0, report.avgSteps, 0.0001)
        assertFalse(report.isComplete)
        assertEquals("unknown", report.plannerId)
    }

    @Test
    fun `only suite tasks are reported`() {
        val runs = listOf(
            testRun(runId = "r1", taskId = "t1", status = RunStatus.SUCCEEDED),
            testRun(runId = "r2", taskId = "not_in_suite", status = RunStatus.SUCCEEDED),
        )
        val report = EvaluationHarness.buildReport(runs, suiteIds = suite, plannerId = "goal")
        assertEquals(1, report.executed)
        assertEquals("t1", report.results.single().taskId)
        assertEquals("PASS", report.results.single().verdict)
        assertFalse(report.isComplete)
    }

    @Test
    fun `latest run wins for a repeated task`() {
        val runs = listOf(
            testRun(runId = "r1", taskId = "t1", status = RunStatus.FAILED),
            testRun(runId = "r2", taskId = "t1", status = RunStatus.SUCCEEDED),
        )
        val report = EvaluationHarness.buildReport(runs, suiteIds = listOf("t1"))
        assertEquals(1, report.executed)
        assertTrue(report.results.single().matchedExpectation)
        assertTrue(report.results.single().succeeded)
        assertEquals(1, report.successful)
    }

    @Test
    fun `fault test passes only when it failed as expected`() {
        val expected = testRun(
            taskId = "t1",
            status = RunStatus.FAILED,
            failureReason = FailureReason.TARGET_NOT_FOUND,
            isFaultTest = true,
            expectedFailures = setOf(FailureReason.TARGET_NOT_FOUND),
        )
        val report = EvaluationHarness.buildReport(listOf(expected), suiteIds = listOf("t1"))
        val result = report.results.single()
        assertTrue(result.matchedExpectation)
        assertEquals("PASS (expected failure)", result.verdict)
        assertEquals(1, report.successful)

        // a fault test that succeeded is a FAIL (the fault never triggered)
        val unexpected = expected.copy(status = RunStatus.SUCCEEDED, failureReason = null)
        val report2 = EvaluationHarness.buildReport(listOf(unexpected), suiteIds = listOf("t1"))
        assertFalse(report2.results.single().matchedExpectation)
        assertEquals("FAIL", report2.results.single().verdict)

        // a fault test that failed with the wrong reason is a FAIL
        val wrong = expected.copy(failureReason = FailureReason.STEP_LIMIT_EXCEEDED)
        val report3 = EvaluationHarness.buildReport(listOf(wrong), suiteIds = listOf("t1"))
        assertFalse(report3.results.single().matchedExpectation)
    }

    @Test
    fun `verdict marks non-fault failures as FAIL`() {
        val failed = testRun(taskId = "t1", status = RunStatus.FAILED)
        val report = EvaluationHarness.buildReport(listOf(failed), suiteIds = listOf("t1"))
        assertEquals("FAIL", report.results.single().verdict)
        assertEquals(0, report.successful)
    }

    @Test
    fun `aggregates are computed from real results`() {
        val runs = listOf(
            testRun(
                taskId = "t1",
                status = RunStatus.SUCCEEDED,
                steps = (1..4).map { testStep(index = it, status = StepStatus.SUCCESS) },
                retries = 1,
            ),
            testRun(
                taskId = "t2",
                status = RunStatus.FAILED,
                steps = listOf(
                    testStep(
                        index = 1,
                        status = StepStatus.NOT_VERIFIED,
                        verification = verification(passed = false),
                    ),
                ),
                retries = 3,
            ),
        )
        val report = EvaluationHarness.buildReport(runs, suiteIds = suite)
        assertEquals(2, report.executed)
        assertEquals(1, report.successful)
        assertEquals(0.5, report.successRate, 0.0001)
        assertEquals(2.5, report.avgSteps, 0.0001) // (4 + 1) / 2
        assertEquals(2.0, report.avgRetries, 0.0001) // (1 + 3) / 2
        assertEquals(1, report.totalVerificationFailures)
        assertFalse(report.isComplete)
    }

    @Test
    fun `complete report is marked complete`() {
        val runs = suite.mapIndexed { i, id ->
            testRun(runId = "r$i", taskId = id, status = RunStatus.SUCCEEDED)
        }
        val report = EvaluationHarness.buildReport(runs, suiteIds = suite)
        assertTrue(report.isComplete)
        assertEquals(1.0, report.successRate, 0.0001)
    }

    @Test
    fun `default suite covers all nine demo tasks`() {
        val report = EvaluationHarness.buildReport(emptyList())
        assertEquals(DemoTasks.evaluation.size, report.suiteSize)
        assertEquals(9, report.suiteSize)
    }

    @Test
    fun `render produces a plain text report with correct formatting`() {
        val runs = listOf(
            testRun(
                taskId = "t1",
                status = RunStatus.SUCCEEDED,
                // 10 steps → avg 10.0, which used to render as "10." (truncated)
                steps = (1..10).map { testStep(index = it, status = StepStatus.SUCCESS) },
            ),
        )
        val report = EvaluationHarness.buildReport(runs, suiteIds = listOf("t1"))
        val text = EvaluationHarness.render(report)
        assertTrue(text.contains("Tasks 1"))
        assertTrue(text.contains("Executed 1"))
        assertTrue(text.contains("Successful 1"))
        assertTrue(text.contains("Success Rate 100%"))
        assertTrue(text.contains("Average Steps 10.0"))
        assertFalse(text.contains("Average Steps 10.\n"))
        assertTrue(text.contains("Open Settings: PASS"))
    }

    @Test
    fun `render formats fractional averages`() {
        val runs = listOf(
            testRun(
                taskId = "t1",
                status = RunStatus.SUCCEEDED,
                steps = (1..3).map { testStep(index = it, status = StepStatus.SUCCESS) },
            ),
            testRun(
                taskId = "t2",
                status = RunStatus.SUCCEEDED,
                steps = (1..4).map { testStep(index = it, status = StepStatus.SUCCESS) },
            ),
        )
        val report = EvaluationHarness.buildReport(runs, suiteIds = listOf("t1", "t2"))
        assertEquals(3.5, report.avgSteps, 0.0001)
        assertTrue(EvaluationHarness.render(report).contains("Average Steps 3.5"))
    }
}
