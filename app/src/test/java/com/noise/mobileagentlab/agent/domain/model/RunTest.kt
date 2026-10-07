package com.noise.mobileagentlab.agent.domain.model

import com.noise.mobileagentlab.testutil.testRun
import com.noise.mobileagentlab.testutil.testStep
import com.noise.mobileagentlab.testutil.verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTest {

    @Test
    fun `succeeded is true only for SUCCEEDED status`() {
        assertTrue(testRun(status = RunStatus.SUCCEEDED).succeeded)
        assertFalse(testRun(status = RunStatus.FAILED).succeeded)
        assertFalse(testRun(status = RunStatus.STOPPED).succeeded)
        assertFalse(testRun(status = RunStatus.RUNNING).succeeded)
    }

    @Test
    fun `duration never goes negative`() {
        val run = testRun(startedAtMs = 5_000, finishedAtMs = 1_000)
        assertEquals(0L, run.durationMs)
        assertEquals(4_000L, testRun(startedAtMs = 1_000, finishedAtMs = 5_000).durationMs)
    }

    @Test
    fun `step counts derive from the step list`() {
        val run = testRun(
            steps = listOf(
                testStep(index = 1, status = StepStatus.SUCCESS, verification = verification(true)),
                testStep(index = 2, status = StepStatus.NOT_VERIFIED, verification = verification(false)),
                testStep(index = 3, status = StepStatus.REJECTED),
            ),
        )
        assertEquals(3, run.stepCount)
        assertEquals(1, run.verifiedStepCount)
        assertEquals(1, run.verificationFailures)
    }

    @Test
    fun `normal run matches expectation only when succeeded`() {
        assertTrue(testRun(status = RunStatus.SUCCEEDED).matchesExpectation)
        assertFalse(testRun(status = RunStatus.FAILED).matchesExpectation)
        assertFalse(testRun(status = RunStatus.STOPPED).matchesExpectation)
    }

    @Test
    fun `fault run matches when it failed with an expected reason`() {
        val run = testRun(
            status = RunStatus.FAILED,
            failureReason = FailureReason.TARGET_NOT_FOUND,
            isFaultTest = true,
            expectedFailures = setOf(FailureReason.TARGET_NOT_FOUND),
        )
        assertTrue(run.matchesExpectation)
        assertTrue(run.isFaultTest)
    }

    @Test
    fun `fault run fails the expectation with the wrong reason`() {
        val run = testRun(
            status = RunStatus.FAILED,
            failureReason = FailureReason.STEP_LIMIT_EXCEEDED,
            isFaultTest = true,
            expectedFailures = setOf(FailureReason.TARGET_NOT_FOUND),
        )
        assertFalse(run.matchesExpectation)
    }

    @Test
    fun `fault run fails the expectation when it unexpectedly succeeded`() {
        val run = testRun(
            status = RunStatus.SUCCEEDED,
            isFaultTest = true,
            expectedFailures = setOf(FailureReason.TARGET_NOT_FOUND),
        )
        assertFalse(run.matchesExpectation)
    }

    @Test
    fun `run state reports failed as expected for live fault runs`() {
        val state = AgentRunState(
            status = RunStatus.FAILED,
            isFaultTest = true,
            expectedFailures = setOf(FailureReason.STEP_LIMIT_EXCEEDED),
            failureReason = FailureReason.STEP_LIMIT_EXCEEDED,
        )
        assertTrue(state.failedAsExpected)

        val wrong = state.copy(failureReason = FailureReason.NO_UI_OBSERVED)
        assertFalse(wrong.failedAsExpected)

        val notFault = state.copy(isFaultTest = false)
        assertFalse(notFault.failedAsExpected)
    }

    @Test
    fun `run state is running only for RUNNING status`() {
        assertTrue(AgentRunState(status = RunStatus.RUNNING).isRunning)
        assertFalse(AgentRunState(status = RunStatus.IDLE).isRunning)
    }

    @Test
    fun `step verified flag follows its verification`() {
        assertTrue(testStep(1, verification = verification(true)).verified)
        assertFalse(testStep(1, verification = verification(false)).verified)
        assertFalse(testStep(1, verification = null).verified)
    }
}
