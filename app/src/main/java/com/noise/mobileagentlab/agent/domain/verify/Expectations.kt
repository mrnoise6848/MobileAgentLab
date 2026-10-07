package com.noise.mobileagentlab.agent.domain.verify

import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState

/** Outcome of evaluating one expectation against a fresh state. */
data class ExpectationOutcome(val passed: Boolean, val detail: String)

/**
 * Phase 8/9 — deterministic post-action expectations.
 *
 * Pure functions over normalized states: no LLM, no heuristics that could
 * drift. The planner attaches an expectation to every action; the verifier
 * evaluates it against a freshly observed screen.
 */
sealed interface Expectation {
    val describe: String

    fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome

    /** An element whose label matches (exact match preferred, else containment). */
    data class NodeVisible(val label: String) : Expectation {
        override val describe: String get() = "NODE_VISIBLE \"$label\""

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome {
            val matches = after.findElements(label)
            return if (matches.isNotEmpty()) {
                ExpectationOutcome(true, "found ${matches.size} match(es) for \"$label\"")
            } else {
                ExpectationOutcome(false, "no element matching \"$label\" (screen=\"${after.screenLabel}\")")
            }
        }
    }

    data class NodeNotVisible(val label: String) : Expectation {
        override val describe: String get() = "NODE_NOT_VISIBLE \"$label\""

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome {
            val matches = after.findElements(label)
            return if (matches.isEmpty()) {
                ExpectationOutcome(true, "\"$label\" absent")
            } else {
                ExpectationOutcome(false, "\"$label\" still present (${matches.size}x)")
            }
        }
    }

    /** The normalized state must have changed at least [minChanged] elements. */
    data class TreeChanged(val minChanged: Int = 1) : Expectation {
        override val describe: String get() = "TREE_CHANGED>=$minChanged"

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome {
            val diff = StateDiff.compute(before, after)
            return if (diff.changeCount >= minChanged) {
                ExpectationOutcome(true, "${diff.changeCount} change(s)")
            } else {
                ExpectationOutcome(false, "only ${diff.changeCount} change(s), needed $minChanged")
            }
        }
    }

    data class PackageIs(val packageName: String) : Expectation {
        override val describe: String get() = "PACKAGE_IS $packageName"

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome =
            if (after.packageName == packageName) {
                ExpectationOutcome(true, "package=$packageName")
            } else {
                ExpectationOutcome(false, "package=${after.packageName ?: "unknown"} != $packageName")
            }
    }

    /**
     * Explicit "nothing to check". Only usable for actions the policy treats as
     * side-effect free; the orchestrator still records the resulting diff.
     */
    data object Unspecified : Expectation {
        override val describe: String get() = "UNSPECIFIED"

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome =
            ExpectationOutcome(true, "no expectation declared")
    }
}
