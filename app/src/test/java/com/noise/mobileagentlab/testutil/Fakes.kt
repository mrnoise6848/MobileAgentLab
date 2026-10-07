package com.noise.mobileagentlab.testutil

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.model.ExecutionResult
import com.noise.mobileagentlab.agent.domain.model.ForegroundInfo
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.planner.AgentPlanner
import com.noise.mobileagentlab.agent.domain.planner.PlannerOutcome
import com.noise.mobileagentlab.agent.domain.planner.PlanningRequest
import com.noise.mobileagentlab.agent.domain.port.ActionExecutor
import com.noise.mobileagentlab.agent.domain.port.TargetLauncher
import com.noise.mobileagentlab.agent.domain.port.UiObserver
import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scripted [UiObserver]: serves queued snapshots in order, then repeats the
 * last one forever (so verification polling sees a stable screen). A queued
 * `null` means "observation impossible".
 */
class FakeUiObserver(
    snapshots: List<UiSnapshot?>,
    initialStatus: AccessibilityStatus = AccessibilityStatus.Connected,
    initialForeground: String? = SafetyPolicy.DEMO_TARGET_PACKAGE,
) : UiObserver {
    override val status = MutableStateFlow(initialStatus)
    override val foreground: MutableStateFlow<ForegroundInfo?> =
        MutableStateFlow(initialForeground?.let { ForegroundInfo(it, null) })

    private val queue = ArrayDeque(snapshots)
    private var last: UiSnapshot? = null

    var observeCount: Int = 0
        private set

    override suspend fun observe(): UiSnapshot? {
        observeCount++
        val next = if (queue.isEmpty()) last else queue.removeFirst()
        last = next
        return next
    }

    override suspend fun awaitQuietPeriod(quietMs: Long, timeoutMs: Long): Boolean = true
}

/** Records every executed action; result is scripted by the constructor lambda. */
class FakeExecutor(
    private val handler: (AgentAction) -> ExecutionResult = {
        ExecutionResult.ok("executed", null, usedFallbackTarget = false, elapsedMs = 1L)
    },
) : ActionExecutor {
    val executed = mutableListOf<AgentAction>()

    override suspend fun execute(action: AgentAction, snapshot: UiSnapshot): ExecutionResult {
        executed += action
        return handler(action)
    }
}

class FakeLauncher(
    private val result: Boolean = true,
) : TargetLauncher {
    override val supportedPackages = setOf(SafetyPolicy.DEMO_TARGET_PACKAGE)
    val calls = mutableListOf<String>()

    override suspend fun bringToFront(packageName: String): Boolean {
        calls += packageName
        return result && packageName in supportedPackages
    }
}

/** Planner driven by a lambda — lets tests script arbitrary decisions. */
class LambdaPlanner(
    override val id: String = "lambda",
    override val displayName: String = "Lambda",
    private val handler: suspend (PlanningRequest) -> PlannerOutcome,
) : AgentPlanner {
    override suspend fun plan(request: PlanningRequest): PlannerOutcome = handler(request)
}
