package com.noise.mobileagentlab.agent.domain.verify

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.port.UiObserver
import com.noise.mobileagentlab.testutil.FakeUiObserver
import com.noise.mobileagentlab.testutil.demoSnapshot
import com.noise.mobileagentlab.testutil.uiNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierTest {

    private val before = demoSnapshot(
        uiNode(id = "n0", label = "Brew Lab"),
        uiNode(id = "n1", label = "Results"),
    )
    private val after = demoSnapshot(
        uiNode(id = "n0", label = "Brew Lab"),
        uiNode(id = "n2", label = "Details for Espresso"),
    )

    @Test
    fun `passes when the expectation holds on the first poll`() = runBlocking {
        val observer = FakeUiObserver(listOf(after))
        val verifier = Verifier(observer, timeoutMs = 200, pollIntervalMs = 10)
        val result = verifier.verify(Expectation.NodeVisible("Details"), before)
        assertTrue(result.passed)
        assertEquals(1, result.attempts)
        assertNull(result.failureReason)
        assertEquals(2, result.diff.changeCount)
    }

    @Test
    fun `polls until the expectation holds`() = runBlocking {
        // first poll still shows the old screen, second shows the outcome
        val observer = FakeUiObserver(listOf(before, before, after))
        val verifier = Verifier(observer, timeoutMs = 2_000, pollIntervalMs = 5)
        val result = verifier.verify(Expectation.NodeVisible("Details"), before)
        assertTrue(result.passed)
        assertEquals(3, result.attempts)
    }

    @Test
    fun `fails with VERIFICATION_FAILED after the timeout`() = runBlocking {
        val observer = FakeUiObserver(listOf(before, before))
        val verifier = Verifier(observer, timeoutMs = 30, pollIntervalMs = 5)
        val result = verifier.verify(Expectation.NodeVisible("Ghost"), before)
        assertFalse(result.passed)
        assertEquals(FailureReason.VERIFICATION_FAILED, result.failureReason)
        assertTrue(result.attempts >= 1)
        assertTrue(result.diff.isEmpty)
    }

    @Test
    fun `skipDelay performs a single-shot check`() = runBlocking {
        val observer = FakeUiObserver(listOf(before, after))
        val verifier = Verifier(observer, timeoutMs = 10_000, pollIntervalMs = 5)
        val result = verifier.verify(Expectation.NodeVisible("Ghost"), before, skipDelay = true)
        assertFalse(result.passed)
        assertEquals(1, result.attempts)
        assertEquals(FailureReason.VERIFICATION_FAILED, result.failureReason)
    }

    @Test
    fun `null observation fails with SERVICE_UNAVAILABLE`() = runBlocking {
        val observer: UiObserver = FakeUiObserver(listOf(null))
        val verifier = Verifier(observer, timeoutMs = 50, pollIntervalMs = 5)
        val result = verifier.verify(Expectation.NodeVisible("Details"), before)
        assertFalse(result.passed)
        assertEquals(FailureReason.SERVICE_UNAVAILABLE, result.failureReason)
        assertEquals(1, result.attempts)
    }

    @Test
    fun `reused beforeState avoids a second normalization`() = runBlocking {
        val observer = FakeUiObserver(listOf(after))
        val verifier = Verifier(observer, timeoutMs = 100, pollIntervalMs = 5)
        val beforeState = com.noise.mobileagentlab.agent.domain.normalize.UiNormalizer.normalize(before)
        val result = verifier.verify(
            Expectation.NodeVisible("Details"),
            before,
            beforeState = beforeState,
        )
        assertTrue(result.passed)
        assertEquals(beforeState, result.before)
    }
}
