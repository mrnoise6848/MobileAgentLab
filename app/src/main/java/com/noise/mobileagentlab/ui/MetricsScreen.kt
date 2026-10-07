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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noise.mobileagentlab.agent.LabController
import com.noise.mobileagentlab.agent.domain.metrics.AggregateMetrics
import com.noise.mobileagentlab.agent.domain.metrics.MetricsAggregator
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Phase 17 — run metrics.
 * Everything on this screen is computed from recorded runs; an empty store
 * shows zeros rather than invented numbers.
 */
@Composable
fun MetricsScreen(controller: LabController, modifier: Modifier = Modifier) {
    val runs by controller.traceStore.runs.collectAsState()
    val aggregate = MetricsAggregator.aggregate(runs)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Run metrics", style = MaterialTheme.typography.titleMedium)
            Text(
                "Source: ${runs.size} recorded run(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
        }

        item { AggregateCard(aggregate, runCount = runs.size) }

        item {
            Text("Per-run breakdown", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
        }

        if (runs.isEmpty()) {
            item {
                Text(
                    "No runs yet — metrics appear after the first completed run.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(runs.reversed(), key = { it.runId }) { run ->
            val metrics = MetricsAggregator.forRun(run)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(metrics.taskTitle, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (metrics.succeeded) "SUCCESS" else metrics.status.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (metrics.succeeded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "steps ${metrics.stepCount}/${metrics.maxSteps} · retries ${metrics.retries} · " +
                            "duration ${metrics.durationMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "plan ${metrics.planMsAvg} ms/step · exec ${metrics.executeMsAvg} ms/step · " +
                            "verify ${metrics.verifyMsAvg} ms/step",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "verification ${percent(metrics.verificationSuccessRate)} " +
                            "(${metrics.verificationAttempts - metrics.verificationFailures}" +
                            "/${metrics.verificationAttempts} attempts) · " +
                            "rejected ${metrics.rejectedSteps}" +
                            (metrics.failureReason?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun AggregateCard(aggregate: AggregateMetrics, runCount: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (runCount == 0) {
                Text(
                    "No data yet (0 runs)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            MetricRow("Success rate", "${percent(aggregate.successRate)} (${aggregate.successfulRuns}/${aggregate.totalRuns})")
            MetricRow("Average steps", aggregate.avgSteps.format(1))
            MetricRow("Average retries", aggregate.avgRetries.format(1))
            MetricRow("Average planning latency", "${aggregate.avgPlanLatencyMs.roundToInt()} ms")
            MetricRow("Average action latency", "${aggregate.avgActionLatencyMs.roundToInt()} ms")
            MetricRow("Average verification latency", "${aggregate.avgVerifyLatencyMs.roundToInt()} ms")
            MetricRow("Average run duration", "${aggregate.avgRunDurationMs.roundToInt()} ms")
            MetricRow(
                "Verification success",
                "${percent(aggregate.verificationSuccessRate)} " +
                    "(${aggregate.verificationAttempts - aggregate.verificationFailures}" +
                    "/${aggregate.verificationAttempts})",
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

private fun Double.format(digits: Int): String {
    val factor = 10.0.pow(digits)
    return ((this * factor).roundToInt() / factor).toString()
}
