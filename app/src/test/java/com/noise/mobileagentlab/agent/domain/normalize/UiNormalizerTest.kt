package com.noise.mobileagentlab.agent.domain.normalize

import com.noise.mobileagentlab.testutil.demoSnapshot
import com.noise.mobileagentlab.testutil.uiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiNormalizerTest {

    @Test
    fun `decorative nodes are dropped`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = ""),
                uiNode(id = "n1", label = "Brew Lab"),
            ),
        )
        assertEquals(listOf("n1"), state.elements.map { it.id })
    }

    @Test
    fun `invisible and zero-bounds nodes are dropped`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = "Hidden", visible = false),
                uiNode(id = "n1", label = "Zero", bounds = com.noise.mobileagentlab.agent.domain.model.NodeBounds(0, 0, 0, 0)),
                uiNode(id = "n2", label = "Visible"),
            ),
        )
        assertEquals(listOf("n2"), state.elements.map { it.id })
    }

    @Test
    fun `label whitespace is collapsed and length is capped`() {
        val long = "x".repeat(200)
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", text = "  Search    coffee  "),
                uiNode(id = "n1", text = long),
            ),
        )
        assertEquals("Search coffee", state.elements[0].label)
        assertEquals(UiNormalizer.MAX_LABEL_LENGTH, state.elements[1].label.length)
    }

    @Test
    fun `repeated label of a plain node collapses`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = "Results"),
                uiNode(id = "n1", label = "Results"), // plain duplicate → dropped
                uiNode(id = "n2", label = "Results", clickable = true), // actionable → kept
            ),
        )
        assertEquals(listOf("n0", "n2"), state.elements.map { it.id })
    }

    @Test
    fun `actionable controls are always kept`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = "A", clickable = true),
                uiNode(id = "n1", label = "B", editable = true),
                uiNode(id = "n2", label = "C", scrollable = true),
                uiNode(id = "n3", label = "D", checkable = true),
            ),
        )
        assertEquals(4, state.size)
        assertTrue(state.elements.all { it.actionable })
    }

    @Test
    fun `element budget is a hard cap even when actionable alone exceed it`() {
        // Regression: previously the budget could be exceeded because all
        // actionable candidates were kept unconditionally.
        val nodes = (0 until 200).map { i ->
            uiNode(id = "n$i", label = "Button $i", clickable = true)
        }
        val state = UiNormalizer.normalize(demoSnapshot(*nodes.toTypedArray()))
        assertEquals(UiNormalizer.MAX_ELEMENTS, state.size)
        assertTrue(state.budgetExceeded)
        // actionable controls win over plain text
        assertTrue(state.elements.all { it.actionable })
    }

    @Test
    fun `actionable first then informative fill under budget`() {
        val nodes = ArrayList<com.noise.mobileagentlab.agent.domain.model.UiNode>()
        repeat(50) { nodes.add(uiNode(id = "a$it", label = "Act $it", clickable = true)) }
        repeat(200) { nodes.add(uiNode(id = "t$it", label = "Text $it")) }
        val state = UiNormalizer.normalize(demoSnapshot(*nodes.toTypedArray()))
        assertEquals(UiNormalizer.MAX_ELEMENTS, state.size)
        assertTrue(state.budgetExceeded)
        assertEquals(50, state.elements.count { it.actionable })
        assertEquals(70, state.elements.count { !it.actionable })
    }

    @Test
    fun `small screen is not budget capped`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = "Brew Lab"),
                uiNode(id = "n1", label = "Settings", clickable = true),
            ),
        )
        assertFalse(state.budgetExceeded)
        assertEquals(2, state.size)
    }

    @Test
    fun `sensitive value and state are blanked but label survives`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(
                    id = "n0",
                    label = "Password",
                    text = "hunter2secret",
                    editable = true,
                    sensitive = true,
                ),
            ),
        )
        val element = state.elements.single()
        assertEquals("Password", element.label)
        assertEquals("", element.value)
        assertTrue(element.sensitive)
    }

    @Test
    fun `findElements prefers exact matches over containment`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = "Search coffee"),
                uiNode(id = "n1", label = "Search"),
            ),
        )
        assertEquals(listOf("n1"), state.findElements("Search").map { it.id })
        // containment fallback when there is no exact match
        assertEquals(
            listOf("n0", "n1"),
            state.findElements("arch").map { it.id }.sorted(),
        )
        assertTrue(state.findElements("   ").isEmpty())
    }

    @Test
    fun `render exposes truncation and budget flags`() {
        val nodes = (0 until 150).map { i -> uiNode(id = "n$i", label = "Button $i", clickable = true) }
        val snapshot = demoSnapshot(*nodes.toTypedArray(), truncated = true)
        val rendered = UiNormalizer.normalize(snapshot).render()
        assertTrue(rendered.contains("[truncated]"))
        assertTrue(rendered.contains("[budget-capped]"))
    }

    @Test
    fun `checked state and disabled flags survive normalization`() {
        val state = UiNormalizer.normalize(
            demoSnapshot(
                uiNode(id = "n0", label = "Offline mode", checkable = true, checked = true),
                uiNode(id = "n1", label = "Auto-play", clickable = true, enabled = false),
            ),
        )
        assertEquals(true, state.elements[0].checked)
        assertEquals(false, state.elements[1].enabled)
    }
}
