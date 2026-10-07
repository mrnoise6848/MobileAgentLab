package com.noise.mobileagentlab.agent.domain.planner

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.action.ScrollDirection
import com.noise.mobileagentlab.agent.domain.model.ElementKind
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.Goal
import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState
import com.noise.mobileagentlab.agent.domain.normalize.UiElement
import com.noise.mobileagentlab.agent.domain.verify.Expectation
import com.noise.mobileagentlab.agent.domain.verify.Expectations

/**
 * Phase 10 — deterministic, local planner.
 *
 * It does not replay a script blindly: on every step it re-reads the CURRENT
 * normalized state and matches the current goal against what is really on
 * screen. That is what makes recovery possible — after a UI change the same
 * goal simply resolves against the new state (or reports the target missing).
 *
 * It is also the reference implementation for the planner contract used by the
 * evaluation harness, so metrics stay reproducible without network access.
 */
class GoalPlanner : AgentPlanner {

    override val id: String = ID
    override val displayName: String = "Local goal planner (deterministic)"

    override suspend fun plan(request: PlanningRequest): PlannerOutcome {
        val goals = request.task.goals
        val index = request.nextGoalIndex
        if (index >= goals.size) {
            return PlannerOutcome.Complete("all ${goals.size} goal(s) executed")
        }

        val goal = goals[index]
        val state = request.state

        return when (goal) {
            is Goal.Click -> {
                val target = selectTarget(state, goal.label, goal.requireKind)
                    ?: return missing(index, goal)
                val action = AgentAction.Click(target.id, target.label, target.bounds)
                PlannerOutcome.Proposal(
                    action = action,
                    expectation = goal.then ?: Expectations.forAction(action, target.label),
                    reason = "goal ${index + 1}: ${goal.describe}",
                    goalIndex = index,
                )
            }

            is Goal.TypeInto -> {
                val target = state.elements
                    .filter { it.enabled && !it.sensitive && it.capabilities.editable }
                    .filter { matchesLabel(it.label, goal.label) }
                    .maxByOrNull { score(it.label, goal.label, ElementKind.INPUT) }
                    ?: return missing(index, goal)
                val action = AgentAction.TypeText(
                    targetId = target.id,
                    text = goal.text,
                    clearFirst = true,
                    expectedLabel = target.label,
                )
                PlannerOutcome.Proposal(
                    action = action,
                    expectation = goal.then ?: Expectations.forAction(action, target.label),
                    reason = "goal ${index + 1}: ${goal.describe}",
                    goalIndex = index,
                )
            }

            is Goal.ScrollUntil -> {
                val found = selectTarget(state, goal.label, null)
                if (found != null) {
                    val action = AgentAction.Click(found.id, found.label, found.bounds)
                    PlannerOutcome.Proposal(
                        action = action,
                        expectation = goal.then ?: Expectations.forAction(action, found.label),
                        reason = "goal ${index + 1}: found \"${goal.label}\", clicking",
                        goalIndex = index,
                    )
                } else {
                    val scrolls = request.history.count {
                        it.goalIndex == index && it.action.startsWith(SCROLL_PREFIX)
                    }
                    if (scrolls >= goal.maxScrolls) {
                        PlannerOutcome.Failed(
                            reason = FailureReason.TARGET_NOT_FOUND,
                            detail = "goal ${index + 1}: \"${goal.label}\" not found after $scrolls scroll(s)",
                        )
                    } else {
                        PlannerOutcome.Proposal(
                            action = AgentAction.Scroll(
                                direction = ScrollDirection.FORWARD,
                                targetId = null,
                                labelHint = goal.label,
                            ),
                            expectation = Expectation.TreeChanged(1),
                            reason = "goal ${index + 1}: searching for \"${goal.label}\"",
                            goalIndex = index,
                            completesGoal = false,
                        )
                    }
                }
            }

            is Goal.ScrollForever -> PlannerOutcome.Proposal(
                action = AgentAction.Scroll(
                    direction = ScrollDirection.FORWARD,
                    targetId = null,
                    labelHint = goal.labelHint,
                ),
                expectation = Expectation.TreeChanged(1),
                reason = "goal ${index + 1}: ${goal.describe}",
                goalIndex = index,
                completesGoal = false,
            )

            Goal.PressBack -> {
                val action = AgentAction.Back
                PlannerOutcome.Proposal(
                    action = action,
                    expectation = Expectations.forAction(action, null),
                    reason = "goal ${index + 1}: ${goal.describe}",
                    goalIndex = index,
                )
            }
        }
    }

    private fun missing(index: Int, goal: Goal): PlannerOutcome.Failed =
        PlannerOutcome.Failed(
            reason = FailureReason.TARGET_NOT_FOUND,
            detail = "goal ${index + 1}: no on-screen element satisfies \"${goal.describe}\"",
        )

    private fun selectTarget(
        state: CompactUiState,
        label: String,
        requireKind: ElementKind?,
    ): UiElement? = state.elements
        .filter { it.enabled && !it.sensitive && it.capabilities.clickable }
        .filter { requireKind == null || it.kind == requireKind }
        .filter { matchesLabel(it.label, label) }
        .maxByOrNull { score(it.label, label, requireKind) }

    private fun matchesLabel(actual: String, wanted: String): Boolean =
        actual.equals(wanted, ignoreCase = true) || actual.contains(wanted, ignoreCase = true)

    private fun score(actual: String, wanted: String, requireKind: ElementKind?): Int {
        var value = 0
        if (actual.equals(wanted, ignoreCase = true)) value += 4
        if (requireKind != null) value += 2
        return value
    }

    companion object {
        const val ID = "goal"
        private const val SCROLL_PREFIX = "SCROLL"
    }
}
