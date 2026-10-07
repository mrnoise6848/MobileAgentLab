package com.noise.mobileagentlab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noise.mobileagentlab.agent.LabController
import com.noise.mobileagentlab.agent.domain.model.AgentRun

/**
 * Phase 15/16 — recorded execution traces.
 * The rendered text is the exact same content stored for the run: redacted,
 * bounded and derived only from what really happened.
 */
@Composable
fun RunsScreen(controller: LabController, modifier: Modifier = Modifier) {
    val runs by controller.traceStore.runs.collectAsState()
    var expandedRunId by remember { mutableStateOf<String?>(null) }

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
                Text("Execution traces (${runs.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { controller.clearTraces() }, enabled = runs.isNotEmpty()) {
                    Text("Clear")
                }
            }
            HorizontalDivider()
        }

        if (runs.isEmpty()) {
            item {
                Text(
                    "No runs recorded yet. Completed runs appear here with their full trace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(runs.reversed(), key = { it.runId }) { run ->
            RunCard(
                run = run,
                expanded = expandedRunId == run.runId,
                traceText = controller.traceStore.render(run),
                onToggle = {
                    expandedRunId = if (expandedRunId == run.runId) null else run.runId
                },
            )
        }
    }
}

@Composable
private fun RunCard(run: AgentRun, expanded: Boolean, traceText: String, onToggle: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(run.taskTitle, style = MaterialTheme.typography.titleSmall)
                Text(
                    run.status.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (run.succeeded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "${run.stepCount}/${run.maxSteps} steps · ${run.durationMs} ms · " +
                    "retries ${run.retries} · verified ${run.verifiedStepCount}/${run.stepCount}" +
                    (if (run.isFaultTest) " · fault test" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            run.failureReason?.let {
                Text(
                    "${it.name} — ${run.failureDetail.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                HorizontalDivider()
                Text(
                    traceText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
