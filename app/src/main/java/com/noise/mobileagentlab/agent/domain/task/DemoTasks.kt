package com.noise.mobileagentlab.agent.domain.task

import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.model.Goal
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.verify.Expectation

/**
 * Phase 10/18 — the fixed catalog of safe demo tasks.
 *
 * Every task is non-sensitive and deterministic: no passwords, no OTP, no
 * payment, no destructive action, no account data. The same catalog drives the
 * Run Inspector and the evaluation harness, so reported numbers are reproducible.
 *
 * Labels must match the `:demoapp` (Brew Lab) UI contract exactly.
 */
object DemoTasks {

    /** Reusable fragments (identical goal prefixes across tasks). */
    private fun openSearch() = Goal.Click("Search coffee", then = Expectation.NodeVisible("Drink name"))

    private fun typeCoffee() = Goal.TypeInto(
        label = "Drink name",
        text = "coffee",
        then = Expectation.ElementTextContains("Drink name", "coffee"),
    )

    private fun runSearch() = Goal.Click("Search", then = Expectation.NodeVisible("Results"))

    val openSettings = PlannedTask(
        id = "open_settings",
        title = "Open Settings",
        description = "Open the demo app settings screen and verify it is visible",
        goals = listOf(Goal.Click("Settings", then = Expectation.NodeVisible("Offline mode"))),
        completion = Expectation.NodeVisible("Auto-play"),
    )

    val searchCoffee = PlannedTask(
        id = "search_coffee",
        title = "Search for coffee",
        description = "Open search, type \"coffee\", run the search and verify results appear",
        goals = listOf(openSearch(), typeCoffee(), runSearch()),
        completion = Expectation.NodeVisible("Espresso"),
    )

    val openFirstResult = PlannedTask(
        id = "open_first_result",
        title = "Open first result",
        description = "Search for coffee and open the first result (Espresso)",
        goals = listOf(
            openSearch(),
            typeCoffee(),
            runSearch(),
            Goal.Click("Espresso", then = Expectation.NodeVisible("Details for Espresso")),
        ),
        completion = Expectation.NodeVisible("Details for Espresso"),
    )

    val goBack = PlannedTask(
        id = "go_back",
        title = "Return to previous page",
        description = "Reach the results list, then navigate back to the home screen",
        goals = listOf(
            openSearch(),
            typeCoffee(),
            runSearch(),
            Goal.PressBack,
        ),
        completion = Expectation.NodeVisible("Brew Lab"),
    )

    val toggleSetting = PlannedTask(
        id = "toggle_setting",
        title = "Enable offline mode",
        description = "Open settings and switch \"Offline mode\" on, then verify its state",
        goals = listOf(
            Goal.Click("Settings", then = Expectation.NodeVisible("Offline mode")),
            Goal.Click("Offline mode", then = Expectation.ElementChecked("Offline mode", true)),
        ),
        completion = Expectation.ElementChecked("Offline mode", true),
    )

    val scrollAndOpen = PlannedTask(
        id = "scroll_and_open",
        title = "Scroll to a deep result",
        description = "Search for coffee, scroll down to \"Turkish Coffee\" and open it",
        goals = listOf(
            openSearch(),
            typeCoffee(),
            runSearch(),
            Goal.ScrollUntil(
                label = "Turkish Coffee",
                maxScrolls = 6,
                then = Expectation.NodeVisible("Details for Turkish Coffee"),
            ),
        ),
        completion = Expectation.NodeVisible("Details for Turkish Coffee"),
    )

    // --- Phase 19: controlled failure cases -----------------------------------

    /**
     * The planned target simply does not exist.
     * Expected: the run fails safely with `TARGET_NOT_FOUND` (never loops).
     */
    val faultMissingTarget = PlannedTask(
        id = "fault_missing_target",
        title = "Fault: missing target",
        description = "Controlled failure — the planned control does not exist on screen",
        goals = listOf(
            Goal.Click("Stress test", then = Expectation.NodeVisible("Locked target")),
            Goal.Click("Ghost control"),
        ),
        completion = Expectation.NodeVisible("Only visible after teleport"),
        expectedFailures = setOf(FailureReason.TARGET_NOT_FOUND),
    )

    /**
     * The target is on screen when observed and disappears before execution
     * (the demo app's "Vanishing target").
     * Expected: `TARGET_NOT_FOUND` (stale id / vanished) or `VERIFICATION_FAILED`.
     */
    val faultStaleTarget = PlannedTask(
        id = "fault_stale_target",
        title = "Fault: disappearing target",
        description = "Controlled failure — the target node disappears between observe and execute",
        goals = listOf(
            Goal.Click("Stress test", then = Expectation.NodeVisible("Locked target")),
            Goal.Click("Vanishing target"),
        ),
        completion = Expectation.NodeVisible("Only visible after teleport"),
        expectedFailures = setOf(
            FailureReason.TARGET_NOT_FOUND,
            FailureReason.VERIFICATION_FAILED,
        ),
    )

    /**
     * The agent scrolls an endless list looking for something that is not there.
     * Expected: the loop terminates at `maxSteps` with `STEP_LIMIT_EXCEEDED`.
     */
    val faultStepLimit = PlannedTask(
        id = "fault_step_limit",
        title = "Fault: step limit",
        description = "Controlled failure — never-ending search, must stop at maxSteps",
        goals = listOf(
            Goal.Click("Stress test", then = Expectation.NodeVisible("Row 1")),
            Goal.ScrollForever("Free Unicorn Latte"),
        ),
        completion = Expectation.NodeVisible("Free Unicorn Latte"),
        maxSteps = 6,
        expectedFailures = setOf(FailureReason.STEP_LIMIT_EXCEEDED),
    )

    /** Tasks offered in the Run Inspector, in demo order. */
    val runnable: List<PlannedTask> = listOf(
        openSettings,
        searchCoffee,
        openFirstResult,
        goBack,
        toggleSetting,
        scrollAndOpen,
    )

    /** Full suite used by the evaluation harness (includes controlled failures). */
    val evaluation: List<PlannedTask> = runnable + listOf(
        faultMissingTarget,
        faultStaleTarget,
        faultStepLimit,
    )

    fun byId(id: String): PlannedTask? = evaluation.firstOrNull { it.id == id }
}
