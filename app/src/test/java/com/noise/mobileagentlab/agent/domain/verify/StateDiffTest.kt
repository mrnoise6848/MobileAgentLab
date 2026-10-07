package com.noise.mobileagentlab.agent.domain.verify

import com.noise.mobileagentlab.testutil.demoState
import com.noise.mobileagentlab.testutil.uiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDiffTest {

    @Test
    fun `identical states produce an empty diff`() {
        val state = demoState(uiNode(id = "n0", label = "Brew Lab"))
        val diff = StateDiff.compute(state, state)
        assertTrue(diff.isEmpty)
        assertEquals(0, diff.changeCount)
        assertEquals(1, diff.beforeCount)
        assertEquals(1, diff.afterCount)
        assertEquals(StateDiff(1, 1, emptyList(), emptyList(), emptyList()), diff)
    }

    @Test
    fun `added and removed elements are reported`() {
        val before = demoState(uiNode(id = "n0", label = "Brew Lab"))
        val after = demoState(uiNode(id = "n1", label = "Results"))
        val diff = StateDiff.compute(before, after)
        assertEquals(listOf("TEXT \"Results\""), diff.added)
        assertEquals(listOf("TEXT \"Brew Lab\""), diff.removed)
        assertTrue(diff.changed.isEmpty())
        assertEquals(2, diff.changeCount)
        assertEquals(1, diff.beforeCount)
        assertEquals(1, diff.afterCount)
    }

    @Test
    fun `changed element reports before and after`() {
        val before = demoState(uiNode(id = "n0", label = "Offline mode"))
        val after = demoState(
            uiNode(id = "n0", label = "Offline mode", checkable = true, checked = true),
        )
        val diff = StateDiff.compute(before, after)
        assertEquals(1, diff.changed.size)
        assertTrue(diff.changed.single().contains("→"))
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun `render lists all changes with markers`() {
        val before = demoState(
            uiNode(id = "n0", label = "Gone"),
            uiNode(id = "n1", label = "Kept"),
        )
        val after = demoState(
            uiNode(id = "n1", label = "Kept"),
            uiNode(id = "n2", label = "New"),
        )
        val rendered = StateDiff.compute(before, after).render()
        assertTrue(rendered.startsWith("nodes 2 → 2"))
        assertTrue(rendered.contains("+ TEXT \"New\""))
        assertTrue(rendered.contains("- TEXT \"Gone\""))
        assertFalse(rendered.contains("~ "))
    }

    @Test
    fun `empty diff renders only the header`() {
        assertEquals("nodes 0 → 0", StateDiff.EMPTY.render())
    }
}
