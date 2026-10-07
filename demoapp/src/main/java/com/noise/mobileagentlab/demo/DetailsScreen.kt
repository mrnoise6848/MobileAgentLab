/**
 * DetailsScreen.kt — DETAILS screen of the Brew Lab demo app.
 *
 * Shows one catalog item: the title is exactly the item name, the body is
 * "Details for <item name>", and a toggleable favorite control exposes an
 * explicit accessibility stateDescription of "On"/"Off".
 */
package com.noise.mobileagentlab.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * DETAILS screen. UI contract:
 *  - title text is exactly the item name (e.g. "Espresso")
 *  - body text "Details for <item name>"
 *  - toggleable favorite control labeled "Favorite" (Role.Switch) whose
 *    explicit semantics stateDescription is "On" or "Off"
 */
@Composable
fun DetailsScreen(
    item: CoffeeItem,
    modifier: Modifier = Modifier,
) {
    var favorite by remember(item.name) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Details for ${item.name}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = if (favorite) "On" else "Off" }
                .toggleable(
                    value = favorite,
                    role = Role.Switch,
                    onValueChange = { favorite = it },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Favorite",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
