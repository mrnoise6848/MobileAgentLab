/**
 * StressScreen.kt — STRESS screen of the Brew Lab demo app.
 *
 * Deliberately hostile UI for controlled accessibility-failure demos:
 *  - "Vanishing target": present on entry, removed from composition (and thus
 *    from the accessibility tree) 400 ms after the screen becomes visible.
 *  - "Locked target": a permanently disabled, non-clickable button.
 *  - a long list of rows for repeated scrolling.
 */
package com.noise.mobileagentlab.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Number of scroll rows in the stress list. */
private const val STRESS_ROW_COUNT = 60

/** Milliseconds after which the "Vanishing target" removes itself. */
private const val VANISH_DELAY_MS = 400L

/**
 * STRESS screen. UI contract:
 *  - title text "Stress test"
 *  - button "Vanishing target" disappears (not composed -> not in the
 *    accessibility tree) 400 ms after the screen becomes visible
 *  - button "Locked target" is disabled and not clickable
 *  - LazyColumn with rows "Row 1" .. "Row 60"
 */
@Composable
fun StressScreen(modifier: Modifier = Modifier) {
    var showVanishingTarget by remember { mutableStateOf(true) }

    // Remove the vanishing target from the UI (and accessibility tree) after 400 ms.
    LaunchedEffect(Unit) {
        delay(VANISH_DELAY_MS)
        showVanishingTarget = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Stress test",
            style = MaterialTheme.typography.headlineMedium,
        )
        if (showVanishingTarget) {
            Button(
                onClick = { /* Intentionally no-op: the target only exists to vanish. */ },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Vanishing target")
            }
        }
        Button(
            onClick = { /* Never reachable: the button is disabled. */ },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Locked target")
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(STRESS_ROW_COUNT) { index ->
                Text(
                    text = "Row ${index + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
