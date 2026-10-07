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

    /** The element matching [label] must expose text containing [text]. */
    data class ElementTextContains(val label: String, val text: String) : Expectation {
        override val describe: String get() = "ELEMENT_TEXT_CONTAINS \"$label\" contains \"$text\""

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome {
            if (label.isBlank()) {
                return ExpectationOutcome(false, "no target label declared for text check")
            }
            val element = after.findElements(label).firstOrNull()
                ?: return ExpectationOutcome(false, "no element matching \"$label\"")
            val value = element.value.ifBlank { element.label }
            return if (value.contains(text, ignoreCase = true)) {
                ExpectationOutcome(true, "\"${element.label}\" contains \"$text\"")
            } else {
                ExpectationOutcome(false, "\"$label\" holds \"${value.take(40)}\" but expected \"$text\"")
            }
        }
    }

    /**
     * The toggle matching [label] must be in the requested state.
     * Accepts the platform `checked` flag or the accessible state description
     * ("On"/"Off"), so it survives implementations that only surface text.
     */
    data class ElementChecked(val label: String, val checked: Boolean) : Expectation {
        override val describe: String get() = "ELEMENT_CHECKED \"$label\" = $checked"

        override fun evaluate(before: CompactUiState, after: CompactUiState): ExpectationOutcome {
            val element = after.findElements(label).firstOrNull()
                ?: return ExpectationOutcome(false, "no element matching \"$label\"")
            val actual = when {
                element.checked != null -> element.checked
                element.state.isNotBlank() -> when {
                    element.state.equals("on", ignoreCase = true) -> true
                    element.state.equals("off", ignoreCase = true) -> false
                    else -> null
                }

                else -> null
            }
            return when {
                actual == null -> ExpectationOutcome(false, "\"$label\" exposes no checked state")
                actual == checked -> ExpectationOutcome(true, "\"$label\" state=$actual")
                else -> ExpectationOutcome(false, "\"$label\" state=$actual, expected $checked")
            }
        }
    }
}

/**
 * Phase 9 — deterministic default expectation for an action.
 *
 * Used whenever the planner does not declare an explicit outcome. It is still
 * a real check (never "assume success"): a click must change something, typed
 * text must appear in its field, and so on.
 */
object Expectations {

    fun forAction(action: com.noise.mobileagentlab.agent.domain.action.AgentAction, targetLabel: String?): Expectation =
        when (action) {
            is com.noise.mobileagentlab.agent.domain.action.AgentAction.TypeText ->
                if (targetLabel.isNullOrBlank()) Expectation.TreeChanged(1)
                else Expectation.ElementTextContains(targetLabel, action.text)

            is com.noise.mobileagentlab.agent.domain.action.AgentAction.Scroll ->
                Expectation.TreeChanged(1)

            com.noise.mobileagentlab.agent.domain.action.AgentAction.Back ->
                Expectation.TreeChanged(1)

            com.noise.mobileagentlab.agent.domain.action.AgentAction.Home ->
                Expectation.TreeChanged(1)

            is com.noise.mobileagentlab.agent.domain.action.AgentAction.Click ->
                Expectation.TreeChanged(1)

            is com.noise.mobileagentlab.agent.domain.action.AgentAction.LongClick ->
                Expectation.TreeChanged(1)
        }
}
