/**
 * Screen.kt — navigation destinations for the Brew Lab demo app.
 *
 * Screens are modeled as a sealed hierarchy so the app's back stack
 * (a simple list of screens held in state) stays exhaustive in `when`.
 */
package com.noise.mobileagentlab.demo

/** A single screen of the demo app. [Details] carries the selected catalog item. */
sealed interface Screen {
    /** Landing screen: "Brew Lab" with the three navigation buttons. */
    data object Home : Screen

    /** Query entry screen with the "Drink name" text input. */
    data object Search : Screen

    /** Scrollable, filtered catalog results. */
    data object Results : Screen

    /** Detail page for one catalog item; back returns to [Results]. */
    data class Details(val item: CoffeeItem) : Screen

    /** Settings screen with three toggle rows. */
    data object Settings : Screen

    /** Controlled-failure screen (vanishing / locked targets, long list). */
    data object Stress : Screen
}
