package com.noise.mobileagentlab.agent.domain.verify

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.testutil.demoState
import com.noise.mobileagentlab.testutil.uiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpectationsTest {

    private val before = demoState(
        uiNode(id = "n0", label = "Brew Lab"),
        uiNode(id = "n1", label = "Results"),
    )

    private val after = demoState(
        uiNode(id = "n0", label = "Brew Lab"),
        uiNode(id = "n2", label = "Details for Espresso"),
    )

    @Test
    fun `NodeVisible passes when the label exists`() {
        val outcome = Expectation.NodeVisible("Details").evaluate(before, after)
        assertTrue(outcome.passed)
    }

    @Test
    fun `NodeVisible fails when the label is absent`() {
        val outcome = Expectation.NodeVisible("Results").evaluate(before, after)
        assertFalse(outcome.passed)
    }

    @Test
    fun `NodeNotVisible is the mirror of NodeVisible`() {
        assertTrue(Expectation.NodeNotVisible("Results").evaluate(before, after).passed)
        assertFalse(Expectation.NodeNotVisible("Details").evaluate(before, after).passed)
    }

    @Test
    fun `TreeChanged passes when elements changed`() {
        assertTrue(Expectation.TreeChanged(1).evaluate(before, after).passed)
        assertFalse(Expectation.TreeChanged(1).evaluate(before, before).passed)
        assertFalse(Expectation.TreeChanged(5).evaluate(before, after).passed)
    }

    @Test
    fun `PackageIs checks the observed package`() {
        val pkg = demoState(uiNode(id = "n0", label = "Brew Lab")).let { state ->
            state.copy(packageName = "com.noise.mobileagentlab.demo")
        }
        val other = pkg.copy(packageName = "com.other.app")
        assertTrue(
            Expectation.PackageIs("com.noise.mobileagentlab.demo")
                .evaluate(pkg, pkg)
                .passed,
        )
        assertFalse(
            Expectation.PackageIs("com.noise.mobileagentlab.demo")
                .evaluate(pkg, other)
                .passed,
        )
    }

    @Test
    fun `ElementTextContains checks the typed value`() {
        val typed = demoState(
            uiNode(id = "n0", label = "Drink name", text = "espresso coffee", editable = true),
        )
        assertTrue(
            Expectation.ElementTextContains("Drink name", "coffee")
                .evaluate(before, typed)
                .passed,
        )
        assertFalse(
            Expectation.ElementTextContains("Drink name", "latte")
                .evaluate(before, typed)
                .passed,
        )
        // missing element fails
        assertFalse(
            Expectation.ElementTextContains("Ghost", "coffee")
                .evaluate(before, typed)
                .passed,
        )
    }

    @Test
    fun `ElementTextContains falls back to the label when value is blank`() {
        // text is blank → the node's identity comes from its content description
        val state = demoState(uiNode(id = "n0", label = "coffee menu", text = ""))
        val element = state.elements.single()
        assertEquals("", element.value) // proves the value really is blank
        assertTrue(
            Expectation.ElementTextContains("coffee menu", "coffee")
                .evaluate(state, state)
                .passed,
        )
    }

    @Test
    fun `ElementChecked reads the platform checked flag`() {
        val on = demoState(
            uiNode(id = "n0", label = "Offline mode", checkable = true, checked = true),
        )
        val off = demoState(
            uiNode(id = "n0", label = "Offline mode", checkable = true, checked = false),
        )
        assertTrue(Expectation.ElementChecked("Offline mode", true).evaluate(before, on).passed)
        assertFalse(Expectation.ElementChecked("Offline mode", true).evaluate(before, off).passed)
    }

    @Test
    fun `ElementChecked accepts an On state description fallback`() {
        val state = demoState(
            uiNode(id = "n0", label = "Offline mode", stateDescription = "On"),
        )
        assertTrue(Expectation.ElementChecked("Offline mode", true).evaluate(before, state).passed)
        assertFalse(Expectation.ElementChecked("Offline mode", false).evaluate(before, state).passed)
    }

    @Test
    fun `ElementChecked fails when no state is exposed`() {
        val state = demoState(uiNode(id = "n0", label = "Offline mode"))
        assertFalse(Expectation.ElementChecked("Offline mode", true).evaluate(before, state).passed)
    }

    @Test
    fun `default expectations are real checks`() {
        val click = AgentAction.Click("n1", "Settings")
        assertEquals(Expectation.TreeChanged(1).describe, Expectations.forAction(click, null).describe)

        val type = AgentAction.TypeText("n2", "coffee")
        val typed = Expectations.forAction(type, "Drink name")
        assertEquals(Expectation.ElementTextContains("Drink name", "coffee").describe, typed.describe)

        // without a declared label the text check degrades to a change check
        assertEquals(
            Expectation.TreeChanged(1).describe,
            Expectations.forAction(type, null).describe,
        )
        assertEquals(
            Expectation.TreeChanged(1).describe,
            Expectations.forAction(AgentAction.Scroll(), null).describe,
        )
        assertEquals(
            Expectation.TreeChanged(1).describe,
            Expectations.forAction(AgentAction.Back, null).describe,
        )
    }

    @Test
    fun `expectation describes are stable and quoted`() {
        assertEquals("NODE_VISIBLE \"Settings\"", Expectation.NodeVisible("Settings").describe)
        assertEquals("TREE_CHANGED>=2", Expectation.TreeChanged(2).describe)
        assertEquals(
            "PACKAGE_IS com.noise.mobileagentlab.demo",
            Expectation.PackageIs("com.noise.mobileagentlab.demo").describe,
        )
    }
}
