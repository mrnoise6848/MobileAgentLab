package com.noise.mobileagentlab.agent.domain.trace

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.testutil.testRun
import com.noise.mobileagentlab.testutil.testStep
import com.noise.mobileagentlab.testutil.verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceStoreTest {

    @Test
    fun `starts empty`() {
        val store = TraceStore()
        assertTrue(store.runs.value.isEmpty())
    }

    @Test
    fun `records runs in order`() {
        val store = TraceStore()
        store.record(testRun(runId = "r1"))
        store.record(testRun(runId = "r2"))
        assertEquals(listOf("r1", "r2"), store.runs.value.map { it.runId })
    }

    @Test
    fun `retention is bounded by maxRuns dropping the oldest`() {
        val store = TraceStore(maxRuns = 3)
        repeat(5) { store.record(testRun(runId = "r$it")) }
        assertEquals(listOf("r2", "r3", "r4"), store.runs.value.map { it.runId })
        assertEquals(3, store.runs.value.size)
    }

    @Test
    fun `clear empties the store`() {
        val store = TraceStore()
        store.record(testRun())
        store.clear()
        assertTrue(store.runs.value.isEmpty())
        store.record(testRun(runId = "r2"))
        assertEquals(1, store.runs.value.size)
    }

    @Test
    fun `render shows header, status and every step`() {
        val store = TraceStore()
        val run = testRun(
            runId = "run-7",
            status = RunStatus.FAILED,
            failureReason = FailureReason.VERIFICATION_FAILED,
            failureDetail = "expected NODE_VISIBLE \"Results\"",
            steps = listOf(
                testStep(
                    index = 1,
                    status = StepStatus.SUCCESS,
                    verification = verification(passed = true),
                ).copy(expectation = com.noise.mobileagentlab.agent.domain.verify.Expectation.NodeVisible("Results")),
                testStep(index = 2, status = StepStatus.NOT_VERIFIED),
            ),
        )
        val rendered = store.render(run)
        assertTrue(rendered.contains("Run run-7"))
        assertTrue(rendered.contains("Task: Open Settings (open_settings)"))
        assertTrue(rendered.contains("Planner: goal"))
        assertTrue(rendered.contains("Status: FAILED | steps 2/15"))
        assertTrue(rendered.contains("Failure: VERIFICATION_FAILED"))
        assertTrue(rendered.contains("Step 1 [SUCCESS]"))
        assertTrue(rendered.contains("Step 2 [NOT_VERIFIED]"))
        assertTrue(rendered.contains("expected: "))
        assertTrue(rendered.contains("verification: PASS"))
        assertTrue(rendered.contains("nodes 0 → 0"))
    }

    @Test
    fun `render redacts secret-looking content`() {
        val store = TraceStore()
        val run = testRun(
            status = RunStatus.FAILED,
            failureReason = FailureReason.EXECUTION_FAILED,
            failureDetail = "rejected value 12345678901234",
            steps = listOf(
                testStep(
                    index = 1,
                    actionLabel = "TYPE \"hunter2secret\"",
                ).copy(planReason = "reach pin with 4111111111111111"),
            ),
        )
        val rendered = store.render(run)
        assertTrue(rendered.contains(TraceRedactor.REDACTED))
        assertFalse(rendered.contains("12345678901234"))
        assertFalse(rendered.contains("hunter2secret"))
        assertFalse(rendered.contains("4111111111111111"))
    }

    @Test
    fun `render of a run without failure omits the failure line`() {
        val store = TraceStore()
        val rendered = store.render(testRun(status = RunStatus.SUCCEEDED))
        assertFalse(rendered.contains("Failure:"))
        assertTrue(rendered.contains("Status: SUCCEEDED"))
    }

    @Test
    fun `render omits retry line when there were no retries`() {
        val store = TraceStore()
        val rendered = store.render(testRun(steps = listOf(testStep(index = 1))))
        assertFalse(rendered.contains("· retries"))
        val withRetries = store.render(
            testRun(steps = listOf(testStep(index = 1).let { it.copy(retries = 2) })),
        )
        assertTrue(withRetries.contains("· retries 2"))
    }

    @Test
    fun `concurrent records do not corrupt the bound`() {
        val store = TraceStore(maxRuns = 10)
        val threads = (0 until 4).map { t ->
            Thread {
                repeat(25) { i -> store.record(testRun(runId = "t$t-$i")) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(10, store.runs.value.size)
        assertEquals(10, store.runs.value.map { it.runId }.distinct().size)
    }

    @Test
    fun `default retention is 20 runs`() {
        val store = TraceStore()
        repeat(25) { store.record(testRun(runId = "r$it")) }
        assertEquals(TraceStore.DEFAULT_MAX_RUNS, store.runs.value.size)
        assertEquals(20, store.runs.value.size)
    }
}
