package com.noise.mobileagentlab.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.noise.mobileagentlab.agent.LabController
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus

/**
 * Phase 16 — configuration: service, execution boundary and the (optional)
 * remote planner. Nothing here can widen the safety policy.
 */
@Composable
fun SettingsScreen(controller: LabController, modifier: Modifier = Modifier) {
    val status by controller.observer.status.collectAsState()
    val selectedPlannerId by controller.selectedPlannerId.collectAsState()
    val llmConfig by controller.llmConfig.collectAsState()
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Accessibility service", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Status: ${status::class.simpleName}" +
                        if (status == AccessibilityStatus.Connected) "" else
                        " — enable \"MobileAgent Lab Observer\" in system settings",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                ) {
                    Text("Open accessibility settings")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Execution boundary (local, enforced in code)", style = MaterialTheme.typography.titleSmall)
                Text("Allowed packages:", style = MaterialTheme.typography.labelLarge)
                controller.policy.allowedPackages.forEach {
                    Text("  • $it", style = MaterialTheme.typography.bodySmall)
                }
                Text("Allowed actions:", style = MaterialTheme.typography.labelLarge)
                Text(
                    "  • " + controller.policy.allowedActions.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Blocked: HOME, any package outside the allowlist, sensitive fields " +
                        "(password/OTP/PIN/payment), text that looks like a secret.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Planner", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Selected: ${controller.plannerOptions.firstOrNull { it.first == selectedPlannerId }?.second}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "The local goal planner is deterministic and needs no network. The remote " +
                        "planner is opt-in and disabled until configured below.",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                Text("Remote LLM planner (opt-in)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = llmConfig.endpointUrl,
                    onValueChange = { controller.updateLlmConfig(llmConfig.copy(endpointUrl = it)) },
                    label = { Text("Endpoint (OpenAI-compatible chat/completions)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = llmConfig.model,
                    onValueChange = { controller.updateLlmConfig(llmConfig.copy(model = it)) },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = llmConfig.apiKey,
                    onValueChange = { controller.updateLlmConfig(llmConfig.copy(apiKey = it)) },
                    label = { Text("API key (memory only)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    "Sent when enabled: task text, the compact normalized screen state and the " +
                        "last step summaries — see docs/safety.md. Nothing is sent otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reproducibility", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Install both APKs (lab + Brew Lab demo target), enable the service, " +
                            "pick a task and press Start. See README for the full flow.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
