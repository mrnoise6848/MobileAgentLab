package com.noise.mobileagentlab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noise.mobileagentlab.agent.domain.model.AgentStep
import com.noise.mobileagentlab.agent.domain.verify.StateDiff

private val AddedColor = Color(0xFF2E7D32)
private val RemovedColor = Color(0xFFC62828)
private val ChangedColor = Color(0xFFEF6C00)

/**
 * Phase 21 — compact before/after state comparison of a single step.
 *
 * ```text
 * Before        42 nodes
 * Action        CLICK Search
 * After         47 nodes
 * Verification  ✓ PASS — "Results" is visible
 *
 * Changed:
 * + BUTTON "Results"
 * - TEXT "Home"
 * ~ INPUT "Drink name" (value changed)
 * ```
 * Everything shown is recorded data — nothing is inferred for display.
 */
@Composable
fun StepDiffView(step: AgentStep, modifier: Modifier = Modifier) {
    val diff = step.diff
    val beforeNodes = diff?.beforeCount ?: step.observedNodes
    val afterNodes = diff?.afterCount ?: step.observedNodes

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DiffRow("Before", "$beforeNodes nodes")
        DiffRow("Action", step.actionLabel)
        DiffRow("After", "$afterNodes nodes")

        step.verification?.let { verification ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiffLabel("Verification")
                Text(
                    text = if (verification.passed) "✓ PASS" else "✗ FAIL",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (verification.passed) AddedColor else RemovedColor,
                )
            }
            Text(
                "expected: ${verification.expectation} — ${verification.detail} " +
                    "(${verification.attempts} attempt(s), ${verification.elapsedMs}ms)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (diff != null) {
            HorizontalDivider(Modifier.padding(top = 4.dp))
            if (diff.isEmpty) {
                Text(
                    "No structural UI change observed (${afterNodes} nodes)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Changed (${diff.changeCount}):", style = MaterialTheme.typography.labelLarge)
                diff.added.forEach { ChangeLine("+", it, AddedColor) }
                diff.removed.forEach { ChangeLine("-", it, RemovedColor) }
                diff.changed.forEach { ChangeLine("~", it, ChangedColor) }
            }
        }
    }
}

/** One-line summary for a collapsed step card, e.g. `+2 −1 ~3`. */
fun StateDiff.compactSummary(): String = buildString {
    if (added.isNotEmpty()) append("+").append(added.size).append(' ')
    if (removed.isNotEmpty()) append("−").append(removed.size).append(' ')
    if (changed.isNotEmpty()) append("~").append(changed.size)
}.trim()

@Composable
private fun DiffRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiffLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DiffLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(end = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChangeLine(sign: String, text: String, color: Color) {
    Text(
        "$sign $text",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}
