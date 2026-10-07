/**
 * HomeScreen.kt — HOME screen of the Brew Lab demo app.
 *
 * Exposes the landing title plus the three navigation buttons that push
 * SEARCH, SETTINGS, and STRESS onto the back stack. Every button's merged
 * accessibility text is exactly its visible label.
 */
package com.noise.mobileagentlab.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * HOME screen. UI contract:
 *  - title text "Brew Lab"
 *  - buttons "Search coffee" -> SEARCH, "Settings" -> SETTINGS, "Stress test" -> STRESS
 */
@Composable
fun HomeScreen(
    onSearchCoffee: () -> Unit,
    onSettings: () -> Unit,
    onStressTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Brew Lab",
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(
            onClick = onSearchCoffee,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Search coffee")
        }
        Button(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Settings")
        }
        Button(
            onClick = onStressTest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Stress test")
        }
    }
}
