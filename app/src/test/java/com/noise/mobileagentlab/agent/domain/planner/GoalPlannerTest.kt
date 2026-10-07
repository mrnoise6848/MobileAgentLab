package com.noise.mobileagentlab.agent.domain.planner

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.Goal
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.verify.Expectation
import com.noise.mobileagentlab.testutil.demoState
import com.noise.mobileagentlab.testutil.uiNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalPlannerTest {

    private val planner = GoalPlanner()

    private fun task(vararg goals: Goal, completion: Expectation = Expectation.NodeVisible("Done")) =
        PlannedTask(
            id = "t",
            title = "T",
            description = "d",
            goals = goals.toList(),
            completion = completion,
        )

    private suspend fun plan(
        task: PlannedTask,
        state: com.noise.mobileagentlab.agent.domain.normalize.CompactUiState,
        nextGoalIndex: Int = 0,
        history: List<StepSummary> = emptyList(),
    ): PlannerOutcome = planner.plan(
        PlanningRequest(
            task = task,
            state = state,
            nextGoalIndex = nextGoalIndex,
            stepIndex = history.size + 1,
            history = history,
        ),
    )

    @Test
    fun `click goal produces a click proposal with the goal's expectation`() = runBlocking {
        val state = demoState(
            uiNode(id = "n1", label = "Settings", clickable = true),
        )
        val goal = Goal.Click("Settings", then = Expectation.NodeVisible("Offline mode"))
        val outcome = plan(task(goal), state) as PlannerOutcome.Proposal

        val action = outcome.action as AgentAction.Click
        assertEquals("n1", action.targetId)
        assertEquals("Settings", action.expectedLabel)
        assertEquals(Expectation.NodeVisible("Offline mode"), outcome.expectation)
        assertEquals(0, outcome.goalIndex)
        assertTrue(outcome.completesGoal)
    }

    @Test
    fun `click goal fails with TARGET_NOT_FOUND when nothing matches`() = runBlocking {
        val state = demoState(uiNode(id = "n1", label = "Settings", clickable = true))
        val outcome = plan(task(Goal.Click("Ghost")), state)
        assertTrue(outcome is PlannerOutcome.Failed)
        assertEquals(
            FailureReason.TARGET_NOT_FOUND,
            (outcome as PlannerOutcome.Failed).reason,
        )
    }

    @Test
    fun `sensitive and disabled targets are never selected`() = runBlocking {
        val state = demoState(
            uiNode(id = "n1", label = "Delete account", clickable = true, sensitive = true),
            uiNode(id = "n2", label = "Delete account", clickable = true, enabled = false),
        )
        val outcome = plan(task(Goal.Click("Delete account")), state)
        assertTrue(outcome is PlannerOutcome.Failed)
    }

    @Test
    fun `type goal produces a TypeText proposal with default text expectation`() = runBlocking {
        val state = demoState(
            uiNode(id = "n2", label = "Drink name", editable = true),
        )
        val outcome = plan(task(Goal.TypeInto("Drink name", "coffee")), state)
            as PlannerOutcome.Proposal
        val action = outcome.action as AgentAction.TypeText
        assertEquals("coffee", action.text)
        assertTrue(action.clearFirst)
        assertEquals(
            Expectation.ElementTextContains("Drink name", "coffee"),
            outcome.expectation,
        )
    }

    @Test
    fun `type goal fails when no editable field matches`() = runBlocking {
        val state = demoState(uiNode(id = "n1", label = "Settings", clickable = true))
        assertTrue(plan(task(Goal.TypeInto("Drink name", "coffee")), state) is PlannerOutcome.Failed)
    }

    @Test
    fun `scroll until scrolls without advancing the goal`() = runBlocking {
        val state = demoState(uiNode(id = "n0", label = "Row 1"))
        val goal = Goal.ScrollUntil("Turkish Coffee", maxScrolls = 3)
        val outcome = plan(task(goal), state) as PlannerOutcome.Proposal
        assertTrue(outcome.action is AgentAction.Scroll)
        assertFalse(outcome.completesGoal)
        assertEquals(0, outcome.goalIndex)
    }

    @Test
    fun `scroll until clicks the target once it appears`() = runBlocking {
        val state = demoState(
            uiNode(id = "n9", label = "Turkish Coffee", clickable = true),
        )
        val outcome = plan(task(Goal.ScrollUntil("Turkish Coffee")), state)
            as PlannerOutcome.Proposal
        assertTrue(outcome.action is AgentAction.Click)
        assertTrue(outcome.completesGoal)
    }

    @Test
    fun `scroll until fails after maxScrolls attempts`() = runBlocking {
        val state = demoState(uiNode(id = "n0", label = "Row 1"))
        val history = (0 until 3).map { i ->
            StepSummary(
                stepIndex = i + 1,
                goalIndex = 0,
                action = "SCROLL FORWARD \"Turkish Coffee\"",
                success = true,
                detail = "scrolled",
                verification = "1 change(s)",
            )
        }
        val outcome = plan(task(Goal.ScrollUntil("Turkish Coffee", maxScrolls = 3)), state, history = history)
        assertTrue(outcome is PlannerOutcome.Failed)
        assertEquals(
            FailureReason.TARGET_NOT_FOUND,
            (outcome as PlannerOutcome.Failed).reason,
        )
    }

    @Test
    fun `scroll forever never completes its goal`() = runBlocking {
        val state = demoState(uiNode(id = "n0", label = "Row 1"))
        val outcome = plan(task(Goal.ScrollForever("Free Unicorn Latte")), state)
            as PlannerOutcome.Proposal
        assertTrue(outcome.action is AgentAction.Scroll)
        assertFalse(outcome.completesGoal)
    }

    @Test
    fun `press back produces the global back action`() = runBlocking {
        val state = demoState(uiNode(id = "n0", label = "Brew Lab"))
        val outcome = plan(task(Goal.PressBack), state) as PlannerOutcome.Proposal
        assertEquals(AgentAction.Back, outcome.action)
        assertTrue(outcome.completesGoal)
    }

    @Test
    fun `running past the last goal reports completion`() = runBlocking {
        val state = demoState(uiNode(id = "n0", label = "Brew Lab"))
        val outcome = plan(task(Goal.Click("Settings")), state, nextGoalIndex = 1)
        assertTrue(outcome is PlannerOutcome.Complete)
    }

    @Test
    fun `exact label match wins over containment`() = runBlocking {
        val state = demoState(
            uiNode(id = "n1", label = "Search coffee", clickable = true),
            uiNode(id = "n2", label = "Search", clickable = true),
        )
        val outcome = plan(task(Goal.Click("Search")), state) as PlannerOutcome.Proposal
        assertEquals("n2", (outcome.action as AgentAction.Click).targetId)
    }

    @Test
    fun `planner id is stable`() {
        assertEquals(GoalPlanner.ID, planner.id)
        assertEquals("goal", planner.id)
    }
}
