package com.noise.mobileagentlab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.noise.mobileagentlab.agent.LabController
import com.noise.mobileagentlab.ui.theme.MobileAgentLabTheme

enum class LabTab(val label: String, val icon: String) {
    RUN("Run", "▶"),
    TRACES("Traces", "≡"),
    METRICS("Metrics", "%"),
    EVAL("Eval", "✓"),
    SETTINGS("Settings", "⚙"),
}

/**
 * Phase 16 — the developer-facing shell of MobileAgent Lab.
 * Navigation is an explicit `when`; no navigation library is needed for 3+2 tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabApp(controller: LabController) {
    var tab by rememberSaveable { mutableStateOf(LabTab.RUN) }

    MobileAgentLabTheme {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("MobileAgent Lab") }) },
            bottomBar = {
                NavigationBar {
                    LabTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Text(entry.icon) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    LabTab.RUN -> RunInspectorScreen(controller)
                    LabTab.TRACES -> RunsScreen(controller)
                    LabTab.METRICS -> MetricsScreen(controller)
                    LabTab.EVAL -> Text("Evaluation (phase 18)")
                    LabTab.SETTINGS -> SettingsScreen(controller)
                }
            }
        }
    }
}
