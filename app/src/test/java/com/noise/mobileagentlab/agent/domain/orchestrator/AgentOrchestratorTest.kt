package com.noise.mobileagentlab.agent.domain.orchestrator

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.model.ExecutionResult
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.Goal
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.agent.domain.planner.GoalPlanner
import com.noise.mobileagentlab.agent.domain.planner.PlannerOutcome
import com.noise.mobileagentlab.agent.domain.safety.ActionValidator
import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import com.noise.mobileagentlab.agent.domain.task.DemoTasks
import com.noise.mobileagentlab.agent.domain.verify.Expectation
import com.noise.mobileagentlab.agent.domain.verify.Verifier
import com.noise.mobileagentlab.testutil.FakeExecutor
import com.noise.mobileagentlab.testutil.FakeLauncher
import com.noise.mobileagentlab.testutil.FakeUiObserver
import com.noise.mobileagentlab.testutil.LambdaPlanner
import com.noise.mobileagentlab.testutil.demoSnapshot
import com.noise.mobileagentlab.testutil.uiNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for the agent loop — the guarantees Phase 12 promises:
 * bounded termination, nothing after stop, policy before execution, and
 * verification instead of trusting the platform's return value.
 */
class AgentOrchestratorTest {

    // --- fixtures ---------------------------------------------------------

    private val home = demoSnapshot(
        uiNode(id = "n0", label = "Brew Lab"),
        uiNode(id = "n1", label = "Settings", clickable = true),
    )
    private val settings = demoSnapshot(
        uiNode(id = "n0", label = "Offline mode", checkable = true),
        uiNode(id = "n1", label = "Auto-play", clickable = true),
    )

    private fun task(
        goals: List<Goal>,
        completion: Expectation = Expectation.NodeVisible("Never ever"),
        maxSteps: Int = 15,
    ) = PlannedTask(
        id = "t",
        title = "T",
        description = "d",
        goals = goals,
        completion = completion,
        maxSteps = maxSteps,
    )

    private fun orchestrator(
        observer: FakeUiObserver,
        executor: FakeExecutor = FakeExecutor(),
        launcher: FakeLauncher = FakeLauncher(),
        verifier: Verifier = Verifier(observer, timeoutMs = 60, pollIntervalMs = 10),
    ) = AgentOrchestrator(
        observer = observer,
        executor = executor,
        verifier = verifier,
        validator = ActionValidator(SafetyPolicy.default()),
        launcher = launcher,
    )

    // --- preconditions ----------------------------------------------------

    @Test
    fun `service not connected fails before anything runs`() = runBlocking {
        val observer = FakeUiObserver(listOf(home), initialStatus = AccessibilityStatus.Disabled)
        val executor = FakeExecutor()
        val launcher = FakeLauncher()
        val run = orchestrator(observer, executor, launcher).run(
            task(listOf(Goal.Click("Settings"))),
            GoalPlanner(),
        )
        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.SERVICE_UNAVAILABLE, run.failureReason)
        assertTrue(run.steps.isEmpty())
        assertTrue(executor.executed.isEmpty())
        assertTrue(launcher.calls.isEmpty()) // never even attempted
    }

    @Test
    fun `target that cannot reach the foreground fails safely`() = runBlocking {
        val observer = FakeUiObserver(listOf(home), initialForeground = null)
        val executor = FakeExecutor()
        val launcher = FakeLauncher(result = false)
        val run = orchestrator(observer, executor, launcher).run(
            task(listOf(Goal.Click("Settings"))),
            GoalPlanner(),
        )
        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.TARGET_LEFT_FOREGROUND, run.failureReason)
        assertTrue(executor.executed.isEmpty())
    }

    // --- the happy path ---------------------------------------------------

    @Test
    fun `verified success path records one successful step`() = runBlocking {
        val observer = FakeUiObserver(listOf(home, settings))
        val executor = FakeExecutor()
        val orch = orchestrator(observer, executor)
        val run = orch.run(
            task(
                goals = listOf(Goal.Click("Settings", then = Expectation.NodeVisible("Offline mode"))),
                completion = Expectation.NodeVisible("Auto-play"),
            ),
            GoalPlanner(),
        )

        assertEquals(RunStatus.SUCCEEDED, run.status)
        assertNull(run.failureReason)
        assertEquals(1, run.stepCount)
        assertEquals(StepStatus.SUCCESS, run.steps.single().status)
        assertEquals(1, run.steps.single().verification?.attempts)
        assertEquals(0, run.retries)
        assertEquals(1, executor.executed.size)
        assertTrue(executor.executed.single() is AgentAction.Click)
        assertFalse(orch.isActive)
        assertEquals(RunStatus.SUCCEEDED, orch.state.value.status)
        assertEquals(1, orch.state.value.stepIndex)
    }

    // --- bounded termination ---------------------------------------------

    @Test
    fun `execution failures terminate after the retry budget`() = runBlocking {
        val observer = FakeUiObserver(listOf(home))
        val executor = FakeExecutor {
            ExecutionResult.failure(FailureReason.EXECUTION_FAILED, "platform refused")
        }
        val run = orchestrator(observer, executor).run(
            task(listOf(Goal.Click("Settings"))),
            GoalPlanner(),
        )

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.EXECUTION_FAILED, run.failureReason)
        assertTrue(run.failureDetail!!.contains("after 3 attempt(s)"))
        assertEquals(3, run.stepCount)
        assertEquals(3, executor.executed.size)
        assertEquals(3, run.retries)
        assertTrue(run.steps.all { it.status == StepStatus.EXECUTION_FAILED })
    }

    @Test
    fun `verification failures terminate after the retry budget`() = runBlocking {
        val observer = FakeUiObserver(listOf(home))
        val executor = FakeExecutor()
        val orch = orchestrator(
            observer,
            executor,
            verifier = Verifier(observer, timeoutMs = 20, pollIntervalMs = 5),
        )
        val planner = LambdaPlanner { _ ->
            PlannerOutcome.Proposal(
                action = AgentAction.Click("n1", "Settings"),
                expectation = Expectation.NodeVisible("Never on screen"),
                reason = "hopeful click",
                goalIndex = 0,
            )
        }

        val run = orch.run(task(listOf(Goal.Click("Settings"))), planner)

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.VERIFICATION_FAILED, run.failureReason)
        assertTrue(run.failureDetail!!.contains("after 3 attempt(s)"))
        assertEquals(3, run.stepCount)
        assertEquals(3, executor.executed.size)
        assertTrue(run.steps.all { it.status == StepStatus.NOT_VERIFIED })
        // every failed verification is recorded — nothing is silently dropped
        assertTrue(run.steps.all { it.verification != null && !it.verification!!.passed })
    }

    @Test
    fun `planner failures terminate after the failure budget`() = runBlocking {
        val observer = FakeUiObserver(listOf(home))
        val executor = FakeExecutor()
        val planner = LambdaPlanner { _ ->
            PlannerOutcome.Failed(FailureReason.TARGET_NOT_FOUND, "no usable target")
        }

        val run = orchestrator(observer, executor).run(task(listOf(Goal.Click("Settings"))), planner)

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.TARGET_NOT_FOUND, run.failureReason)
        assertEquals(3, run.stepCount)
        assertTrue(run.steps.all { it.status == StepStatus.PLANNING_FAILED })
        assertTrue(executor.executed.isEmpty()) // nothing executed while planning failed
    }

    @Test
    fun `policy rejections terminate after the rejection budget`() = runBlocking {
        val observer = FakeUiObserver(listOf(home))
        val executor = FakeExecutor()
        // HOME is representable but not allowed by the local policy
        val planner = LambdaPlanner { _ ->
            PlannerOutcome.Proposal(
                action = AgentAction.Home,
                expectation = Expectation.TreeChanged(1),
                reason = "leave the app",
                goalIndex = 0,
            )
        }

        val run = orchestrator(observer, executor).run(task(listOf(Goal.Click("Settings"))), planner)

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.ACTION_NOT_ALLOWED, run.failureReason)
        assertEquals(3, run.stepCount)
        assertTrue(run.steps.all { it.status == StepStatus.REJECTED })
        assertTrue(executor.executed.isEmpty()) // rejected actions never reach the device
    }

    @Test
    fun `unobservable screen fails with a recorded observation step`() = runBlocking {
        val observer = FakeUiObserver(listOf(null))
        val executor = FakeExecutor()
        val run = orchestrator(observer, executor).run(
            task(listOf(Goal.Click("Settings"))),
            GoalPlanner(),
        )

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.NO_UI_OBSERVED, run.failureReason)
        assertEquals(1, run.stepCount)
        assertEquals(StepStatus.OBSERVATION_FAILED, run.steps.single().status)
        assertTrue(executor.executed.isEmpty())
    }

    @Test
    fun `step limit terminates an endless search`() = runBlocking {
        val observer = FakeUiObserver(listOf(home))
        val executor = FakeExecutor()
        val planner = LambdaPlanner { _ ->
            // a step that always verifies but never completes the task
            PlannerOutcome.Proposal(
                action = AgentAction.Click("n1", "Settings"),
                expectation = Expectation.NodeVisible("Settings"),
                reason = "keep looking",
                goalIndex = 0,
                completesGoal = false,
            )
        }

        val run = orchestrator(observer, executor).run(
            task(listOf(Goal.Click("Settings")), maxSteps = 3),
            planner,
        )

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.STEP_LIMIT_EXCEEDED, run.failureReason)
        assertEquals("reached maxSteps=3", run.failureDetail)
        assertEquals(3, run.stepCount)
        assertEquals(3, executor.executed.size)
    }

    // --- stop ------------------------------------------------------------

    @Test
    fun `nothing executes after requestStop`() = runBlocking {
        val observer = FakeUiObserver(listOf(home))
        val executor = FakeExecutor()
        val orch = orchestrator(observer, executor)
        val planner = LambdaPlanner { _ ->
            orch.requestStop() // requested while planning
            PlannerOutcome.Proposal(
                action = AgentAction.Click("n1", "Settings"),
                expectation = Expectation.NodeVisible("Offline mode"),
                reason = "would click if not stopping",
                goalIndex = 0,
            )
        }

        val run = orch.run(task(listOf(Goal.Click("Settings"))), planner)

        assertEquals(RunStatus.STOPPED, run.status)
        assertEquals(FailureReason.USER_STOPPED, run.failureReason)
        assertTrue(executor.executed.isEmpty())
        assertTrue(run.steps.isEmpty()) // stopped before touching the device
        assertFalse(orch.isActive)
    }

    // --- execution boundary ----------------------------------------------

    @Test
    fun `a foreign package on screen aborts the run before planning`() = runBlocking {
        val foreign = demoSnapshot(
            uiNode(id = "n1", label = "Settings", clickable = true, packageName = "com.evil.app"),
            pkg = "com.evil.app",
        )
        val observer = FakeUiObserver(listOf(foreign))
        val executor = FakeExecutor()
        val planner = LambdaPlanner { _ ->
            error("the boundary must abort before the planner is ever consulted")
        }

        val run = orchestrator(observer, executor).run(
            task(listOf(Goal.Click("Settings"))),
            planner,
        )

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.PACKAGE_NOT_ALLOWED, run.failureReason)
        assertEquals(StepStatus.ABORTED, run.steps.single().status)
        assertTrue(executor.executed.isEmpty())
    }

    // --- controlled failure task (Phase 19) -------------------------------

    @Test
    fun `fault task fails with TARGET_NOT_FOUND and matches its expectation`() = runBlocking {
        val start = demoSnapshot(
            uiNode(id = "n0", label = "Brew Lab"),
            uiNode(id = "n1", label = "Stress test", clickable = true),
        )
        val afterStress = demoSnapshot(
            uiNode(id = "n0", label = "Locked target"),
            uiNode(id = "n1", label = "Row 1"),
        )
        // step 1 sees `start`, verification and all later steps see `afterStress`
        val observer = FakeUiObserver(listOf(start, afterStress))
        val executor = FakeExecutor()

        val run = orchestrator(observer, executor).run(DemoTasks.faultMissingTarget, GoalPlanner())

        assertEquals(RunStatus.FAILED, run.status)
        assertEquals(FailureReason.TARGET_NOT_FOUND, run.failureReason)
        assertTrue(run.isFaultTest)
        assertTrue("a fault task failing as expected IS a pass", run.matchesExpectation)
        // 1 verified step + the bounded planning failures for the ghost target
        assertEquals(4, run.stepCount)
        assertEquals(StepStatus.SUCCESS, run.steps[0].status)
        assertTrue(run.steps.drop(1).all { it.status == StepStatus.PLANNING_FAILED })
        assertEquals(1, executor.executed.size)
    }

    // --- live state -------------------------------------------------------

    @Test
    fun `run state mirrors the finished run`() = runBlocking {
        val observer = FakeUiObserver(listOf(home, settings))
        val orch = orchestrator(observer, FakeExecutor())
        assertEquals(RunStatus.IDLE, orch.state.value.status)
        assertNull(orch.state.value.runId)

        val run = orch.run(
            task(
                goals = listOf(Goal.Click("Settings", then = Expectation.NodeVisible("Offline mode"))),
                completion = Expectation.NodeVisible("Auto-play"),
            ),
            GoalPlanner(),
        )

        val state = orch.state.value
        assertEquals(run.status, state.status)
        assertEquals(run.runId, state.runId)
        assertEquals(run.taskId, state.taskId)
        assertEquals(run.stepCount, state.steps.size)
        assertEquals(run.finishedAtMs, state.finishedAtMs)
        assertEquals(false, state.isRunning)
    }
}
