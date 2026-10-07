package com.noise.mobileagentlab.agent.domain.planner

import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.action.ScrollDirection
import com.noise.mobileagentlab.agent.domain.verify.Expectation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalSchemaTest {

    private fun parseOk(json: String): AgentActionProposal {
        val result = ProposalSchema.parse(json)
        assertTrue("expected Ok, got $result", result is ProposalParseResult.Ok)
        return (result as ProposalParseResult.Ok).proposal
    }

    private fun parseInvalid(json: String): String {
        val result = ProposalSchema.parse(json)
        assertTrue("expected Invalid, got $result", result is ProposalParseResult.Invalid)
        return (result as ProposalParseResult.Invalid).detail
    }

    @Test
    fun `parses a click proposal`() {
        val proposal = parseOk(
            """{"action":"CLICK","target_id":"n1","label":"Settings","reason":"open settings"}""",
        )
        val action = proposal.action as AgentAction.Click
        assertEquals("n1", action.targetId)
        assertEquals("Settings", action.expectedLabel)
        assertEquals("open settings", proposal.reason)
        assertNull(proposal.expectation)
    }

    @Test
    fun `parses type text with clear_first default`() {
        val proposal = parseOk(
            """{"action":"TYPE_TEXT","target_id":"n2","label":"Drink name","text":"coffee","reason":"type"}""",
        )
        val action = proposal.action as AgentAction.TypeText
        assertEquals("coffee", action.text)
        assertTrue(action.clearFirst)
    }

    @Test
    fun `honours clear_first false`() {
        val proposal = parseOk(
            """{"action":"TYPE_TEXT","target_id":"n2","text":"coffee","clear_first":false,"reason":"r"}""",
        )
        assertFalse((proposal.action as AgentAction.TypeText).clearFirst)
    }

    @Test
    fun `parses scroll with directions`() {
        val forward = parseOk("""{"action":"SCROLL","reason":"r"}""")
        assertTrue((forward.action as AgentAction.Scroll).direction == ScrollDirection.FORWARD)

        val backward = parseOk(
            """{"action":"SCROLL","direction":"backward","reason":"r"}""",
        )
        assertEquals(ScrollDirection.BACKWARD, (backward.action as AgentAction.Scroll).direction)
    }

    @Test
    fun `parses back and home`() {
        assertEquals(AgentAction.Back, parseOk("""{"action":"BACK","reason":"r"}""").action)
        // HOME is representable at the schema layer; the local policy rejects it later
        assertEquals(AgentAction.Home, parseOk("""{"action":"HOME","reason":"r"}""").action)
    }

    @Test
    fun `parses every supported expectation type`() {
        val nodeVisible = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"NODE_VISIBLE","label":"Results"}}""",
        )
        assertEquals(Expectation.NodeVisible("Results"), nodeVisible.expectation)

        val nodeNotVisible = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"NODE_NOT_VISIBLE","label":"Results"}}""",
        )
        assertEquals(Expectation.NodeNotVisible("Results"), nodeNotVisible.expectation)

        val textContains = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"ELEMENT_TEXT_CONTAINS","label":"Drink name","text":"coffee"}}""",
        )
        assertEquals(
            Expectation.ElementTextContains("Drink name", "coffee"),
            textContains.expectation,
        )

        val checked = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"ELEMENT_CHECKED","label":"Offline mode","checked":true}}""",
        )
        assertEquals(Expectation.ElementChecked("Offline mode", true), checked.expectation)

        val treeChanged = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"TREE_CHANGED","min_changed":3}}""",
        )
        assertEquals(Expectation.TreeChanged(3), treeChanged.expectation)

        val pkg = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"PACKAGE_IS","package":"com.noise.mobileagentlab.demo"}}""",
        )
        assertEquals(
            Expectation.PackageIs("com.noise.mobileagentlab.demo"),
            pkg.expectation,
        )
    }

    @Test
    fun `target_id is accepted as an alias for label in expectations`() {
        val proposal = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"NODE_VISIBLE","target_id":"Results"}}""",
        )
        assertEquals(Expectation.NodeVisible("Results"), proposal.expectation)
    }

    @Test
    fun `tree changed coerces min_changed to at least 1`() {
        val proposal = parseOk(
            """{"action":"BACK","reason":"r","expected":{"type":"TREE_CHANGED","min_changed":0}}""",
        )
        assertEquals(Expectation.TreeChanged(1), proposal.expectation)
    }

    @Test
    fun `unknown root fields are rejected`() {
        val detail = parseInvalid(
            """{"action":"BACK","reason":"r","shell":"rm -rf /"}""",
        )
        assertTrue(detail.contains("unknown field"))
        assertTrue(detail.contains("shell"))
    }

    @Test
    fun `unknown expectation fields are rejected`() {
        val detail = parseInvalid(
            """{"action":"BACK","reason":"r","expected":{"type":"TREE_CHANGED","payload":"code"}}""",
        )
        assertTrue(detail.contains("invalid \"expected\""))
    }

    @Test
    fun `missing action is rejected`() {
        assertTrue(parseInvalid("""{"reason":"r"}""").contains("action"))
    }

    @Test
    fun `unsupported action is rejected`() {
        val detail = parseInvalid("""{"action":"SHELL","reason":"r"}""")
        assertTrue(detail.contains("unsupported action"))
    }

    @Test
    fun `click without target_id is rejected`() {
        assertTrue(parseInvalid("""{"action":"CLICK","reason":"r"}""").contains("target_id"))
    }

    @Test
    fun `type text without text is rejected`() {
        assertTrue(
            parseInvalid("""{"action":"TYPE_TEXT","target_id":"n1","reason":"r"}""")
                .contains("text"),
        )
    }

    @Test
    fun `invalid scroll direction is rejected`() {
        val detail = parseInvalid("""{"action":"SCROLL","direction":"UP","reason":"r"}""")
        assertTrue(detail.contains("invalid scroll direction"))
    }

    @Test
    fun `unknown expectation type is rejected`() {
        val detail = parseInvalid(
            """{"action":"BACK","reason":"r","expected":{"type":"EXEC_SHELL"}}""",
        )
        assertTrue(detail.contains("invalid \"expected\""))
    }

    @Test
    fun `blank output is rejected`() {
        assertTrue(parseInvalid("   ").contains("empty"))
        assertTrue(parseInvalid("not json at all").contains("not valid JSON"))
    }

    @Test
    fun `root must be an object`() {
        assertTrue(parseInvalid("[1,2,3]").contains("root must be a JSON object"))
    }

    @Test
    fun `reason is truncated to 200 chars`() {
        val long = "x".repeat(500)
        val proposal = parseOk("""{"action":"BACK","reason":"$long"}""")
        assertEquals(200, proposal.reason.length)
    }

    @Test
    fun `code fences and prose wrappers are stripped`() {
        val fenced = """
            ```json
            {"action":"BACK","reason":"r"}
            ```
        """.trimIndent()
        assertEquals(AgentAction.Back, parseOk(fenced).action)

        val prose = """
            Sure! Here you go:
            {"action":"BACK","reason":"r"}
            Hope that helps.
        """.trimIndent()
        assertEquals(AgentAction.Back, parseOk(prose).action)
    }

    @Test
    fun `prompt instructions forbid code and unknown fields`() {
        assertTrue(ProposalSchema.PROMPT_INSTRUCTIONS.contains("EXACTLY ONE JSON object"))
        assertTrue(ProposalSchema.PROMPT_INSTRUCTIONS.contains("no code fences"))
        assertTrue(ProposalSchema.PROMPT_INSTRUCTIONS.contains("Never output shell commands"))
    }
}
