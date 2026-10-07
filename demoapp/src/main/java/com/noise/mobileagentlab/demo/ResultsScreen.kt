/**
 * ResultsScreen.kt — RESULTS screen of the Brew Lab demo app.
 *
 * Renders the filtered coffee catalog as a scrollable list. Filtering is
 * deterministic: a blank query shows "No results"; otherwise items whose
 * name or description contains the query (ignoring case) are shown in
 * catalog order. Each row's name is itself the clickable element so its
 * accessibility text is exactly the catalog item name; the supporting text
 * (always containing the word "coffee") sits right below it.
 */
package com.noise.mobileagentlab.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * RESULTS screen. UI contract:
 *  - title text "Results"
 *  - blank query or no matches -> text "No results"
 *  - otherwise all matching catalog items, in fixed catalog order
 *    (query "coffee" matches all 14: "Espresso" first, "Turkish Coffee" last)
 *  - clicking a row pushes DETAILS for that item
 */
@Composable
fun ResultsScreen(
    query: String,
    onItemClicked: (CoffeeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = if (query.isBlank()) {
        emptyList()
    } else {
        coffeeCatalog.filter { item ->
            item.name.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )
        if (results.isEmpty()) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(results, key = { it.name }) { item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // The clickable element: its accessibility text is exactly the item name.
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClicked(item) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                        // Supporting text: always contains the word "coffee".
                        // Kept non-clickable so the only clickable nodes in a row
                        // are the ones whose text is exactly the catalog name.
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
