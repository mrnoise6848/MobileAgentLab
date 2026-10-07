/**
 * CoffeeCatalog.kt — static data backing the RESULTS screen.
 *
 * The catalog is fixed and deterministic: 14 items in a stable order where
 * every description contains the word "coffee" so a query of "coffee"
 * matches all of them.
 */
package com.noise.mobileagentlab.demo

/** One catalog entry: the visible row title and its supporting text. */
data class CoffeeItem(
    val name: String,
    val description: String,
)

/** The full catalog in the exact display order expected by the UI contract. */
val coffeeCatalog: List<CoffeeItem> = listOf(
    CoffeeItem("Espresso", "Rich espresso coffee"),
    CoffeeItem("Latte", "Smooth latte coffee"),
    CoffeeItem("Flat White", "Creamy flat white coffee"),
    CoffeeItem("Mocha", "Chocolate mocha coffee"),
    CoffeeItem("Cappuccino", "Foamy cappuccino coffee"),
    CoffeeItem("Americano", "Bold americano coffee"),
    CoffeeItem("Macchiato", "Layered macchiato coffee"),
    CoffeeItem("Cortado", "Balanced cortado coffee"),
    CoffeeItem("Ristretto", "Intense ristretto coffee"),
    CoffeeItem("Affogato", "Sweet affogato coffee"),
    CoffeeItem("Cold Brew", "Chilled cold brew coffee"),
    CoffeeItem("Irish Coffee", "Boozy irish coffee"),
    CoffeeItem("Turkish Coffee", "Spiced turkish coffee"),
    CoffeeItem("Vietnamese Coffee", "Sweet vietnamese coffee"),
)
