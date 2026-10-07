/**
 * DemoActivity.kt — single-activity host of the Brew Lab demo app.
 *
 * Owns the navigation state (a simple back stack of [Screen]s held in Compose
 * state), wires system back behavior, and dispatches each screen's callbacks.
 * Every navigation transition is explicit so an external accessibility agent
 * can predict the exact screen sequence.
 */
package com.noise.mobileagentlab.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Launcher activity: sets up the Compose content rooted at [BrewLabApp]. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                BrewLabApp()
            }
        }
    }
}

/**
 * Root composable: holds the back stack plus shared screen state (query and
 * settings toggles) and renders the screen on top of the stack.
 *
 * Back behavior (via [BackHandler]):
 *  - HOME: handler disabled -> system back finishes the activity.
 *  - SEARCH/RESULTS/SETTINGS/STRESS: back pops to HOME.
 *  - DETAILS: back pops to RESULTS.
 */
@Composable
fun BrewLabApp() {
    // Back stack, always seeded with HOME so the stack is never empty.
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    // Shared state kept across screens: the search query and the settings toggles.
    var query by rememberSaveable { mutableStateOf("") }
    var darkMode by rememberSaveable { mutableStateOf(false) }
    var offlineMode by rememberSaveable { mutableStateOf(false) }
    var autoPlay by rememberSaveable { mutableStateOf(false) }

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    // Enabled only when we can pop; on HOME the default system back (finish) runs.
    BackHandler(enabled = backStack.size > 1) {
        pop()
    }

    val current = backStack.last()

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (current) {
                Screen.Home -> HomeScreen(
                    onSearchCoffee = { push(Screen.Search) },
                    onSettings = { push(Screen.Settings) },
                    onStressTest = { push(Screen.Stress) },
                )

                Screen.Search -> SearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {
                        // Contract: pop SEARCH, push RESULTS -> stack is [HOME, RESULTS].
                        pop()
                        push(Screen.Results)
                    },
                )

                Screen.Results -> ResultsScreen(
                    query = query,
                    onItemClicked = { item -> push(Screen.Details(item)) },
                )

                is Screen.Details -> DetailsScreen(
                    item = current.item,
                )

                Screen.Settings -> SettingsScreen(
                    darkMode = darkMode,
                    offlineMode = offlineMode,
                    autoPlay = autoPlay,
                    onDarkModeChange = { darkMode = it },
                    onOfflineModeChange = { offlineMode = it },
                    onAutoPlayChange = { autoPlay = it },
                )

                Screen.Stress -> StressScreen()
            }
        }
    }
}
