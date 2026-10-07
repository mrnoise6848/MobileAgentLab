package com.noise.mobileagentlab.agent.domain.model

import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import com.noise.mobileagentlab.agent.domain.verify.Expectation

/**
 * Phase 10/18 — demo task definitions.
 *
 * A task is a list of goals plus a completion expectation. Goals are the
 * planner's contract: each goal yields exactly one *terminal* action (scrolling
 * that only searches for a target does not advance the goal index).
 */
sealed interface Goal {
    val describe: String

    /** Click the element whose label matches (exact match preferred). */
    data class Click(
        val label: String,
        val then: Expectation? = null,
        val requireKind: ElementKind? = null,
    ) : Goal {
        override val describe: String get() = "click \"$label\""
    }

    /** Type [text] into the editable element labeled [label]. */
    data class TypeInto(
        val label: String,
        val text: String,
        val then: Expectation? = null,
    ) : Goal {
        override val describe: String get() = "type \"$text\" into \"$label\""
    }

    /**
     * Scroll until [label] appears (at most [maxScrolls] scrolls), then click it.
     * A single goal: scrolling steps do not advance the goal index, the final
     * click does.
     */
    data class ScrollUntil(
        val label: String,
        val maxScrolls: Int = 4,
        val then: Expectation? = null,
    ) : Goal {
        override val describe: String get() = "scroll to \"$label\" and click it"
    }

    /** Pure scrolling that never terminates a goal (used by fault tasks). */
    data class ScrollForever(val labelHint: String) : Goal {
        override val describe: String get() = "scroll looking for \"$labelHint\""
    }

    data object PressBack : Goal {
        override val describe: String get() = "press back"
    }
}

data class PlannedTask(
    val id: String,
    val title: String,
    val description: String,
    val goals: List<Goal>,
    val completion: Expectation,
    val targetPackage: String = SafetyPolicy.DEMO_TARGET_PACKAGE,
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    /**
     * Phase 19 — fault-injection tasks: the run MUST fail, and only with one of
     * these reasons. The evaluation harness treats that as a PASS.
     */
    val expectedFailures: Set<FailureReason> = emptySet(),
) {
    val isFaultTest: Boolean get() = expectedFailures.isNotEmpty()

    companion object {
        const val DEFAULT_MAX_STEPS = 15
    }
}
