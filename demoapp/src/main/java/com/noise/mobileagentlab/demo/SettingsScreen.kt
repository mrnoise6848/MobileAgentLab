/**
 * SettingsScreen.kt — SETTINGS screen of the Brew Lab demo app.
 *
 * Three switch-style toggle rows in a fixed order. Each row is a single
 * Modifier.toggleable element with a visible label and EXPLICIT accessibility
 * semantics: contentDescription equals the label and stateDescription is
 * "On"/"Off". Toggle state is owned by the caller (app-level screen state).
 */
package com.noise.mobileagentlab.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * SETTINGS screen. UI contract:
 *  - title text "Settings"
 *  - exactly three toggle rows, in order: "Dark mode", "Offline mode", "Auto-play"
 *  - each row: Role.Switch, visible label, explicit semantics
 *    contentDescription = "<label>" and stateDescription = "On" / "Off"
 */
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    offlineMode: Boolean,
    autoPlay: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )
        ToggleRow(
            label = "Dark mode",
            checked = darkMode,
            onCheckedChange = onDarkModeChange,
        )
        ToggleRow(
            label = "Offline mode",
            checked = offlineMode,
            onCheckedChange = onOfflineModeChange,
        )
        ToggleRow(
            label = "Auto-play",
            checked = autoPlay,
            onCheckedChange = onAutoPlayChange,
        )
    }
}

/** A single labeled switch row with explicit contentDescription + stateDescription. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            }
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
