package com.noise.mobileagentlab.agent.domain.verify

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState
import com.noise.mobileagentlab.agent.domain.normalize.UiElement

/**
 * Phase 8/21 — compact before/after comparison of two normalized states.
 * Small enough to keep in traces and show in the inspector.
 */
data class StateDiff(
    val beforeCount: Int,
    val afterCount: Int,
    val added: List<String>,
    val removed: List<String>,
    val changed: List<String>,
) {
    val changeCount: Int get() = added.size + removed.size + changed.size
    val isEmpty: Boolean get() = changeCount == 0

    fun render(): String = buildString {
        append("nodes ").append(beforeCount).append(" → ").append(afterCount).append('\n')
        for (a in added) append("+ ").append(a).append('\n')
        for (r in removed) append("- ").append(r).append('\n')
        for (c in changed) append("~ ").append(c).append('\n')
    }.trimEnd()

    companion object {
        val EMPTY = StateDiff(0, 0, emptyList(), emptyList(), emptyList())

        fun compute(before: CompactUiState, after: CompactUiState): StateDiff {
            val beforeById = before.elements.associateBy { it.id }
            val afterById = after.elements.associateBy { it.id }

            val added = ArrayList<String>()
            val removed = ArrayList<String>()
            val changed = ArrayList<String>()

            for (element in after.elements) {
                val previous = beforeById[element.id]
                if (previous == null) {
                    added.add(describe(element))
                } else if (signature(previous) != signature(element)) {
                    changed.add("${describe(previous)} → ${describe(element)}")
                }
            }
            for (element in before.elements) {
                if (afterById[element.id] == null) removed.add(describe(element))
            }
            return StateDiff(
                beforeCount = before.size,
                afterCount = after.size,
                added = added,
                removed = removed,
                changed = changed,
            )
        }

        private fun signature(element: UiElement): String =
            "${element.label}|${element.state}|${element.checked}|${element.enabled}"

        private fun describe(element: UiElement): String {
            val label = element.label.ifBlank { "(no label)" }
            return "${element.kind.name} \"$label\""
        }
    }
}

/**
 * Phase 8 — result of verifying an action against a freshly observed state.
 *
 * `passed` is decided ONLY by the deterministic expectation, never by the
 * boolean the platform returned from `performAction`.
 */
data class VerificationResult(
    val passed: Boolean,
    val expectation: Expectation,
    val detail: String,
    val attempts: Int,
    val elapsedMs: Long,
    val failureReason: FailureReason?,
    val before: CompactUiState,
    val after: CompactUiState,
    val diff: StateDiff,
) {
    val screenLabel: String get() = after.screenLabel

    /** Compact form stored in traces (no full states retained). */
    fun summary(): VerificationSummary = VerificationSummary(
        passed = passed,
        expectation = expectation.describe,
        detail = detail,
        attempts = attempts,
        elapsedMs = elapsedMs,
        diff = diff,
    )

    companion object {
        fun notRun(
            expectation: Expectation,
            reason: FailureReason,
            before: CompactUiState,
            detail: String,
        ) = VerificationResult(
            passed = false,
            expectation = expectation,
            detail = detail,
            attempts = 0,
            elapsedMs = 0L,
            failureReason = reason,
            before = before,
            after = before,
            diff = StateDiff.EMPTY,
        )
    }
}

/** Trace-friendly slice of a verification result. */
data class VerificationSummary(
    val passed: Boolean,
    val expectation: String,
    val detail: String,
    val attempts: Int,
    val elapsedMs: Long,
    val diff: StateDiff,
)
