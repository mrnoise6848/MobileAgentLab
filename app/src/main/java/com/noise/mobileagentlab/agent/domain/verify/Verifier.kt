package com.noise.mobileagentlab.agent.domain.verify

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState
import com.noise.mobileagentlab.agent.domain.normalize.UiNormalizer
import com.noise.mobileagentlab.agent.domain.port.UiObserver
import kotlinx.coroutines.delay

/**
 * Phase 8 — post-action verification loop.
 *
 * ```text
 * action executed → wait for UI update → re-read tree → compare expectation
 * ```
 *
 * The verifier polls until the expectation holds or [timeoutMs] elapses, so a
 * slow animation counts as success while a wrong outcome is reported as
 * [FailureReason.VERIFICATION_FAILED] with the last observed diff.
 *
 * This is the component that makes "the API returned true" insufficient.
 */
class Verifier(
    private val observer: UiObserver,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_MS,
) {

    suspend fun verify(
        expectation: Expectation,
        before: UiSnapshot?,
        skipDelay: Boolean = false,
    ): VerificationResult {
        val beforeState = before?.let { UiNormalizer.normalize(it) } ?: CompactUiState.empty()
        val startedAt = System.currentTimeMillis()
        var attempts = 0
        var lastState = beforeState
        var lastDetail = "no observation yet"

        while (true) {
            attempts++
            val snapshot = observer.observe()
            if (snapshot == null) {
                return VerificationResult(
                    passed = false,
                    expectation = expectation,
                    detail = "could not observe UI after action",
                    attempts = attempts,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    failureReason = FailureReason.SERVICE_UNAVAILABLE,
                    before = beforeState,
                    after = beforeState,
                    diff = StateDiff.EMPTY,
                )
            }

            val state = UiNormalizer.normalize(snapshot)
            val outcome = expectation.evaluate(beforeState, state)
            lastState = state
            lastDetail = outcome.detail

            if (outcome.passed) {
                return VerificationResult(
                    passed = true,
                    expectation = expectation,
                    detail = outcome.detail,
                    attempts = attempts,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    failureReason = null,
                    before = beforeState,
                    after = state,
                    diff = StateDiff.compute(beforeState, state),
                )
            }

            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed >= timeoutMs) break
            if (skipDelay) break
            delay(pollIntervalMs)
        }

        return VerificationResult(
            passed = false,
            expectation = expectation,
            detail = lastDetail,
            attempts = attempts,
            elapsedMs = System.currentTimeMillis() - startedAt,
            failureReason = FailureReason.VERIFICATION_FAILED,
            before = beforeState,
            after = lastState,
            diff = StateDiff.compute(beforeState, lastState),
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1_500L
        const val DEFAULT_POLL_MS = 120L
    }
}
