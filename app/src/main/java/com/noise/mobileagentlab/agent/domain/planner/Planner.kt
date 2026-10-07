package com.noise.mobileagentlab.agent.domain.planner

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.normalize.CompactUiState
import com.noise.mobileagentlab.agent.domain.verify.Expectation

/** Light-weight step history handed to planners (also used for LLM context). */
data class StepSummary(
    val stepIndex: Int,
    val goalIndex: Int,
    val action: String,
    val success: Boolean,
    val detail: String,
    val verification: String,
)

/** Everything a planner may look at when choosing the next action. */
data class PlanningRequest(
    val task: PlannedTask,
    val state: CompactUiState,
    val nextGoalIndex: Int,
    val stepIndex: Int,
    val history: List<StepSummary>,
)

/** What the planner decided to do next. */
sealed interface PlannerOutcome {
    /**
     * @param goalIndex the goal this proposal belongs to
     * @param completesGoal false for intermediate steps (e.g. scrolling) that
     *        must not advance the goal cursor
     */
    data class Proposal(
        val action: AgentAction,
        val expectation: Expectation,
        val reason: String,
        val goalIndex: Int,
        val completesGoal: Boolean = true,
    ) : PlannerOutcome

    /** The task is finished (all goals executed and completion checked). */
    data class Complete(val reason: String) : PlannerOutcome

    /** The planner could not produce a valid next action. */
    data class Failed(val reason: FailureReason, val detail: String) : PlannerOutcome
}

/**
 * Phase 10 — replaceable planning abstraction.
 *
 * Contract: the planner only *proposes* a typed action + expectation. It has no
 * access to Android APIs and cannot execute anything; the orchestrator validates
 * every proposal against the local safety policy first.
 */
interface AgentPlanner {
    val id: String
    val displayName: String

    suspend fun plan(request: PlanningRequest): PlannerOutcome
}
