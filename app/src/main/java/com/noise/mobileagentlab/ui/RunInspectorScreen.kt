package com.noise.mobileagentlab.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noise.mobileagentlab.agent.LabController
import com.noise.mobileagentlab.agent.domain.model.AgentRunState
import com.noise.mobileagentlab.agent.domain.model.AgentStep
import com.noise.mobileagentlab.agent.domain.model.RunStatus
import com.noise.mobileagentlab.agent.domain.model.StepStatus
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import com.noise.mobileagentlab.agent.domain.task.DemoTasks

/**
 * Phase 16 — live run inspector: task/planner selection, start/stop, step stream.
 * Every value shown is real data produced by the running orchestrator.
 */
@Composable
fun RunInspectorScreen(controller: LabController, modifier: Modifier = Modifier) {
    val runState by controller.runState.collectAsState()
    val serviceStatus by controller.observer.status.collectAsState()
    val selectedTaskId by controller.selectedTaskId.collectAsState()
    val selectedPlannerId by controller.selectedPlannerId.collectAsState()
    val error by controller.error.collectAsState()
    val context = LocalContext.current
    var expandedStep by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            ServiceCard(
                status = serviceStatus,
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }

        item {
            ConfigCard(
                tasks = controller.availableTasks,
                selectedTaskId = selectedTaskId,
                onTaskSelected = controller::selectTask,
                planners = controller.plannerOptions,
                selectedPlannerId = selectedPlannerId,
                onPlannerSelected = controller::selectPlanner,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { controller.startRun() },
                    enabled = !runState.isRunning && serviceStatus == AccessibilityStatus.Connected,
                ) {
                    Text("Start run")
                }
                OutlinedButton(
                    onClick = { controller.stopRun() },
                    enabled = runState.isRunning,
                ) {
                    Text("Stop")
                }
                Text(
                    text = if (serviceStatus == AccessibilityStatus.Connected) "service connected"
                    else "service disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (serviceStatus == AccessibilityStatus.Connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                        DismissButton("Dismiss", controller::clearError)
                    }
                }
            }
        }

        item { StatusCard(runState) }

        item {
            Text("Steps", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
        }

        if (runState.steps.isEmpty()) {
            item {
                Text(
                    "No steps yet — press Start to run the selected task.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(runState.steps, key = { it.index }) { step ->
            StepCard(
                step = step,
                expanded = expandedStep == step.index,
                onToggle = { expandedStep = if (expandedStep == step.index) null else step.index },
            )
        }
    }
}

@Composable
private fun ServiceCard(status: AccessibilityStatus, onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Accessibility service", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (status) {
                        AccessibilityStatus.Connected -> "CONNECTED"
                        AccessibilityStatus.Disabled -> "DISABLED"
                        AccessibilityStatus.Destroyed -> "STOPPED"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (status == AccessibilityStatus.Connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                "The service must be enabled manually in system settings; the app never " +
                    "enables it by itself.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onOpenSettings) { Text("Open accessibility settings") }
        }
    }
}

@Composable
private fun ConfigCard(
    tasks: List<com.noise.mobileagentlab.agent.domain.model.PlannedTask>,
    selectedTaskId: String,
    onTaskSelected: (String) -> Unit,
    planners: List<Pair<String, String>>,
    selectedPlannerId: String,
    onPlannerSelected: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Task", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.take(4).forEach { task ->
                    FilterChip(
                        selected = task.id == selectedTaskId,
                        onClick = { onTaskSelected(task.id) },
                        label = { Text(task.title) },
                    )
                }
            }
            val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
            selectedTask?.let {
                Text(it.description, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Planner", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                planners.forEach { (id, label) ->
                    FilterChip(
                        selected = id == selectedPlannerId,
                        onClick = { onPlannerSelected(id) },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Demo tasks: ${DemoTasks.runnable.size} safe tasks · evaluation suite also " +
                    "contains ${DemoTasks.evaluation.size - DemoTasks.runnable.size} controlled " +
                    "failure tasks",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusCard(state: AgentRunState) {
    val container = when (state.status) {
        RunStatus.SUCCEEDED -> MaterialTheme.colorScheme.primaryContainer
        RunStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        RunStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
        RunStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        RunStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(state.taskTitle.ifBlank { "No task" }, style = MaterialTheme.typography.titleSmall)
                Text(state.status.name, style = MaterialTheme.typography.labelLarge)
            }
            if (state.status != RunStatus.IDLE) {
                Text(
                    "Step ${state.stepIndex} / ${state.maxSteps} · planner=${state.plannerId}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "screen=\"${state.screenLabel}\" · ${state.elementCount} elements",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.currentAction?.let {
                    Text("→ $it", style = MaterialTheme.typography.bodyMedium)
                }
                state.failureReason?.let { reason ->
                    Text(
                        "FAILED: ${reason.name} — ${state.failureDetail.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: AgentStep, expanded: Boolean, onToggle: () -> Unit) {
    val glyph = when (step.status) {
        StepStatus.SUCCESS -> "✓"
        StepStatus.NOT_VERIFIED,
        StepStatus.EXECUTION_FAILED,
        StepStatus.PLANNING_FAILED,
        StepStatus.OBSERVATION_FAILED,
        -> "✕"

        StepStatus.REJECTED -> "⊘"
        StepStatus.ABORTED -> "!"
    }
    val glyphColor = when (step.status) {
        StepStatus.SUCCESS -> Color(0xFF2E7D32)
        StepStatus.REJECTED -> Color(0xFFEF6C00)
        StepStatus.ABORTED -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(glyph, color = glyphColor, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Step ${step.index} · ${step.actionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "goal ${step.goalIndex + 1} · plan ${step.planMs}ms · exec ${step.executeMs}ms · " +
                    "verify ${step.verifyMs}ms · ${step.observedNodes} nodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                val diff = step.diff
                val failure = step.failureReason
                Text(
                    buildString {
                        if (step.planReason.isNotBlank()) append("reason: ${step.planReason}\n")
                        step.expectation?.let { append("expected: ${it.describe}\n") }
                        if (step.validationDetail.isNotBlank()) {
                            append("validation: ${step.validationDetail}\n")
                        }
                        if (step.executionDetail.isNotBlank()) {
                            append("execution: ${step.executionDetail}\n")
                        }
                        step.verification?.let {
                            append(
                                "verification: ${if (it.passed) "PASS" else "FAIL"} " +
                                    "(${it.attempts} attempt(s), ${it.elapsedMs}ms) ${it.detail}\n",
                            )
                        }
                        if (diff != null && !diff.isEmpty()) {
                            append("state diff:\n")
                            append(diff.render())
                        }
                        if (failure != null) {
                            append("failure: ${failure.name} ${step.detail}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DismissButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}
