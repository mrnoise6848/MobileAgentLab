package com.noise.mobileagentlab.agent

import android.content.Context
import com.noise.mobileagentlab.agent.data.accessibility.AccessibilityActionExecutor
import com.noise.mobileagentlab.agent.data.accessibility.AccessibilityUiObserver
import com.noise.mobileagentlab.agent.data.accessibility.AndroidTargetLauncher
import com.noise.mobileagentlab.agent.data.planner.LlmConfig
import com.noise.mobileagentlab.agent.data.planner.RemoteLlmPlanner
import com.noise.mobileagentlab.agent.domain.model.AgentRunState
import com.noise.mobileagentlab.agent.domain.model.PlannedTask
import com.noise.mobileagentlab.agent.domain.orchestrator.AgentOrchestrator
import com.noise.mobileagentlab.agent.domain.planner.AgentPlanner
import com.noise.mobileagentlab.agent.domain.planner.GoalPlanner
import com.noise.mobileagentlab.agent.domain.safety.ActionValidator
import com.noise.mobileagentlab.agent.domain.safety.SafetyPolicy
import com.noise.mobileagentlab.agent.domain.task.DemoTasks
import com.noise.mobileagentlab.agent.domain.trace.TraceStore
import com.noise.mobileagentlab.agent.domain.verify.Verifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Composition root of the lab: wires the pure domain components to their
 * Android implementations and exposes a single, small API to the UI.
 *
 * No DI framework: there is exactly one object graph and it is trivial to read.
 */
class LabController(context: Context) {

    private val appContext = context.applicationContext

    // --- ports & domain services ---------------------------------------------
    val observer = AccessibilityUiObserver()
    val executor = AccessibilityActionExecutor()
    val policy: SafetyPolicy = SafetyPolicy.default()
    val validator = ActionValidator(policy)
    val verifier = Verifier(observer)
    val launcher = AndroidTargetLauncher(appContext, policy.allowedPackages)
    val traceStore = TraceStore()
    val orchestrator = AgentOrchestrator(
        observer = observer,
        executor = executor,
        verifier = verifier,
        validator = validator,
        launcher = launcher,
    )

    private val localPlanner = GoalPlanner()

    // --- user choices ---------------------------------------------------------
    private val _selectedTaskId = MutableStateFlow(DemoTasks.searchCoffee.id)
    val selectedTaskId: StateFlow<String> = _selectedTaskId.asStateFlow()

    private val _selectedPlannerId = MutableStateFlow(GoalPlanner.ID)
    val selectedPlannerId: StateFlow<String> = _selectedPlannerId.asStateFlow()

    private val _llmConfig = MutableStateFlow(LlmConfig())
    val llmConfig: StateFlow<LlmConfig> = _llmConfig.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val runState: StateFlow<AgentRunState> = orchestrator.state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null

    val availableTasks: List<PlannedTask> get() = DemoTasks.runnable

    val plannerOptions: List<Pair<String, String>> = listOf(
        GoalPlanner.ID to localPlanner.displayName,
        RemoteLlmPlanner.ID to "Remote LLM planner (opt-in)",
    )

    fun selectTask(taskId: String) {
        if (DemoTasks.runnable.any { it.id == taskId }) _selectedTaskId.value = taskId
    }

    fun selectPlanner(plannerId: String) {
        if (plannerOptions.any { it.first == plannerId }) _selectedPlannerId.value = plannerId
    }

    fun updateLlmConfig(config: LlmConfig) {
        _llmConfig.value = config
    }

    private fun currentPlanner(): AgentPlanner = when (_selectedPlannerId.value) {
        RemoteLlmPlanner.ID -> RemoteLlmPlanner { _llmConfig.value }
        else -> localPlanner
    }

    // --- run control ----------------------------------------------------------

    /**
     * Starts a run only when the user explicitly asks for it, the service is
     * connected and no other run is active.
     */
    fun startRun(taskId: String = _selectedTaskId.value): Boolean {
        val task = DemoTasks.byId(taskId) ?: run {
            _error.value = "unknown task: $taskId"
            return false
        }
        if (orchestrator.isActive) {
            _error.value = "a run is already active"
            return false
        }
        _error.value = null
        runJob = scope.launch {
            try {
                val run = orchestrator.run(task, currentPlanner())
                traceStore.record(run)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "run aborted: ${e.javaClass.simpleName}"
            }
        }
        return true
    }

    /** Cooperative stop: the orchestrator will not execute anything afterwards. */
    fun stopRun() {
        orchestrator.requestStop()
    }

    fun clearError() {
        _error.value = null
    }

    fun clearTraces() {
        traceStore.clear()
    }
}
