package com.noise.mobileagentlab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noise.mobileagentlab.agent.LabController
import com.noise.mobileagentlab.agent.domain.evaluation.EvaluationHarness
import com.noise.mobileagentlab.agent.domain.evaluation.EvaluationReport
import com.noise.mobileagentlab.agent.domain.evaluation.EvaluationState
import com.noise.mobileagentlab.agent.domain.evaluation.EvaluationTaskResult
import com.noise.mobileagentlab.agent.domain.task.DemoTasks
import kotlin.math.roundToInt

/**
 * Phase 18 — evaluation harness UI.
 * Runs the fixed safe suite and renders a report computed from real runs only.
 */
@Composable
fun EvaluationScreen(controller: LabController, modifier: Modifier = Modifier) {
    val state by controller.evaluation.collectAsState()
    val runs by controller.traceStore.runs.collectAsState()

    // Report preference: live/finished evaluation state, else derive from traces.
    val report: EvaluationReport? = when (val s = state) {
        is EvaluationState.Finished -> s.report
        is EvaluationState.Running -> s.report
        EvaluationState.Idle ->
            EvaluationHarness.buildReport(runs).takeIf { it.results.isNotEmpty() }
    }
    val isRunning = state is EvaluationState.Running

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Evaluation", style = MaterialTheme.typography.titleMedium)
                if (isRunning) {
                    OutlinedButton(onClick = { controller.stopEvaluation() }) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = { controller.startEvaluation() }) {
                        Text("Run suite (${DemoTasks.evaluation.size})")
                    }
                }
            }
            val s = state
            if (s is EvaluationState.Running) {
                Text(
                    "Task ${s.completed + 1}/${s.total}: ${s.currentTaskTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        if (report == null || report.results.isEmpty()) {
            item {
                Text(
                    "No evaluation yet. Tap \"Run suite\" to execute all " +
                        "${DemoTasks.evaluation.size} safe tasks (including controlled " +
                        "failure cases) and record a report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { ReportCard(report) }

            item {
                Text("Per-task results", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
            }

            items(report.results, key = { it.taskId }) { result ->
                TaskResultRow(result)
            }
        }
    }
}

@Composable
private fun ReportCard(report: EvaluationReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetricLine("Tasks", "${report.suiteSize} (${report.executed} executed)")
            MetricLine("Successful", "${report.successful}")
            MetricLine("Success Rate", percent(report.successRate))
            MetricLine("Average Steps", report.avgSteps.format(1))
            MetricLine("Average Retries", report.avgRetries.format(1))
            MetricLine("Average Run", "${report.avgDurationMs.roundToInt()} ms")
            MetricLine("Verification Failures", "${report.totalVerificationFailures}")
            Text(
                "planner ${report.plannerId}" + (if (report.isComplete) "" else " · suite still running"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskResultRow(result: EvaluationTaskResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(result.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    result.verdict,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (result.matchedExpectation) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "steps ${result.steps}/${result.maxSteps} · retries ${result.retries} · " +
                    "${result.durationMs} ms · verification failures " +
                    "${result.verificationFailures}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            result.failureReason?.let {
                Text(
                    "${it.name} — ${it.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

private fun Double.format(digits: Int): String =
    ((this * 10).toInt() / 10.0).toString().take(digits + 2)
