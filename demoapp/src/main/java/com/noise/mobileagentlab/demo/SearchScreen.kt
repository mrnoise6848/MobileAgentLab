/**
 * SearchScreen.kt — SEARCH screen of the Brew Lab demo app.
 *
 * Hosts the single query input ("Drink name", explicitly labeled for
 * accessibility) and the "Search" button that pops SEARCH and pushes RESULTS
 * so the back stack becomes [HOME, RESULTS].
 */
package com.noise.mobileagentlab.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * SEARCH screen. UI contract:
 *  - title text "Search"
 *  - editable text input with explicit semantics contentDescription "Drink name"
 *    and a visible label "Drink name"
 *  - button "Search" -> validates the query (blank queries are allowed and
 *    render as "No results" on RESULTS) and navigates to RESULTS
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Drink name" },
            label = { Text(text = "Drink name") },
            singleLine = true,
        )
        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Search")
        }
    }
}
