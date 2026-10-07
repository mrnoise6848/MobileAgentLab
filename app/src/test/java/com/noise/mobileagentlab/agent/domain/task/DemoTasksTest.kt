package com.noise.mobileagentlab.agent.domain.task

import com.noise.mobileagentlab.agent.domain.model.Goal
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTasksTest {

    @Test
    fun `runnable tasks are the six safe showcase flows`() {
        assertEquals(
            listOf(
                "open_settings",
                "search_coffee",
                "open_first_result",
                "go_back",
                "toggle_setting",
                "scroll_and_open",
            ),
            DemoTasks.runnable.map { it.id },
        )
    }

    @Test
    fun `evaluation suite adds exactly the three fault tasks`() {
        assertEquals(9, DemoTasks.evaluation.size)
        assertEquals(DemoTasks.runnable, DemoTasks.evaluation.take(6))
        assertEquals(
            listOf(
                "fault_missing_target",
                "fault_stale_target",
                "fault_step_limit",
            ),
            DemoTasks.evaluation.drop(6).map { it.id },
        )
    }

    @Test
    fun `task ids are unique`() {
        val ids = DemoTasks.evaluation.map { it.id }
        assertEquals(ids, ids.distinct())
    }

    @Test
    fun `byId resolves suite tasks and rejects unknown ids`() {
        assertNotNull(DemoTasks.byId("open_settings"))
        assertNotNull(DemoTasks.byId("fault_step_limit"))
        assertNull(DemoTasks.byId("does_not_exist"))
    }

    @Test
    fun `fault tasks are the only fault tests`() {
        DemoTasks.runnable.forEach { assertFalse(it.isFaultTest) }
        DemoTasks.evaluation.filter { it.isFaultTest }.forEach { task ->
            assertTrue(task.id.startsWith("fault_"))
            assertTrue(task.expectedFailures.isNotEmpty())
        }
        assertEquals(3, DemoTasks.evaluation.count { it.isFaultTest })
    }

    @Test
    fun `fault step limit expects the step limit reason`() {
        assertEquals(6, DemoTasks.faultStepLimit.maxSteps)
        assertEquals(
            setOf(com.noise.mobileagentlab.agent.domain.model.FailureReason.STEP_LIMIT_EXCEEDED),
            DemoTasks.faultStepLimit.expectedFailures,
        )
    }

    @Test
    fun `every task targets the demo package and has goals`() {
        DemoTasks.evaluation.forEach { task ->
            assertEquals(SafetyPolicy.DEMO_TARGET_PACKAGE, task.targetPackage)
            assertTrue(task.goals.isNotEmpty())
            assertTrue(task.title.isNotBlank())
            assertTrue(task.description.isNotBlank())
            assertTrue(task.maxSteps > 0)
        }
    }

    @Test
    fun `go_back uses the allowed global back action`() {
        assertTrue(DemoTasks.goBack.goals.contains(Goal.PressBack))
    }

    @Test
    fun `no task goal mentions sensitive content`() {
        val forbidden = listOf("password", "otp", "credit card", "pin code", "cvv")
        DemoTasks.evaluation
            .flatMap { it.goals }
            .map { it.describe.lowercase() }
            .forEach { describe ->
                forbidden.forEach { term -> assertFalse("$describe must not contain $term", describe.contains(term)) }
            }
    }

    @Test
    fun `byId returns the same instances as direct references`() {
        assertEquals(DemoTasks.openSettings, DemoTasks.byId("open_settings"))
        assertEquals(DemoTasks.searchCoffee, DemoTasks.byId("search_coffee"))
        assertEquals(DemoTasks.faultMissingTarget, DemoTasks.byId("fault_missing_target"))
    }

    @Test
    fun `task implements the planned task contract`() {
        val task: PlannedTask = DemoTasks.toggleSetting
        assertEquals(2, task.goals.size)
        assertFalse(task.isFaultTest)
    }
}
