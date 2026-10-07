package com.noise.mobileagentlab.agent.domain.planner

import com.noise.mobileagentlab.agent.domain.action.ActionType
import com.noise.mobileagentlab.agent.domain.action.AgentAction
import com.noise.mobileagentlab.agent.domain.action.ScrollDirection
import com.noise.mobileagentlab.agent.domain.json.Json
import com.noise.mobileagentlab.agent.domain.json.Json.boolean
import com.noise.mobileagentlab.agent.domain.json.Json.string
import com.noise.mobileagentlab.agent.domain.json.JsonException
import com.noise.mobileagentlab.agent.domain.json.JsonValue
import com.noise.mobileagentlab.agent.domain.verify.Expectation

/** A validated proposal produced by the schema layer (never raw model output). */
data class AgentActionProposal(
    val action: AgentAction,
    val expectation: Expectation?,
    val reason: String,
)

sealed interface ProposalParseResult {
    data class Ok(val proposal: AgentActionProposal) : ProposalParseResult
    data class Invalid(val detail: String) : ProposalParseResult
}

/**
 * Phase 11 — the ONLY way model output becomes an action.
 *
 * Strict by construction:
 *  - JSON must parse, root must be an object
 *  - unknown fields are rejected (no smuggling extra instructions)
 *  - the action must be one of the known typed actions with its required fields
 *  - expectations must be one of the known deterministic rule types
 *  - no shell commands, intents, raw accessibility calls or code are representable
 *
 * The resulting proposal still has to pass the local safety policy (Phase 6/14)
 * before anything executes — the schema is a filter, not the permission.
 */
object ProposalSchema {

    const val PROMPT_INSTRUCTIONS = """
Reply with EXACTLY ONE JSON object and nothing else (no prose, no code fences).
Allowed "action" values: CLICK, TYPE_TEXT, SCROLL, BACK.
Fields:
  "action"    required, one of the allowed values
  "target_id" required for CLICK and TYPE_TEXT (must be an id from the screen state)
  "label"     recommended, the exact label you saw for that element
  "text"      required for TYPE_TEXT
  "direction" optional for SCROLL: "FORWARD" or "BACKWARD"
  "reason"    required, one short sentence
  "expected"  optional object describing the outcome you expect:
       {"type":"NODE_VISIBLE","label":"..."}
       {"type":"NODE_NOT_VISIBLE","label":"..."}
       {"type":"ELEMENT_TEXT_CONTAINS","label":"...","text":"..."}
       {"type":"ELEMENT_CHECKED","label":"...","checked":true}
       {"type":"TREE_CHANGED","min_changed":1}
Never output shell commands, intents, code, or any field not listed above.
"""

    private val ALLOWED_ROOT_KEYS = setOf(
        "action", "target_id", "label", "text", "direction", "reason", "expected", "clear_first",
    )

    private val ALLOWED_EXPECTATION_KEYS = setOf(
        "type", "target_id", "label", "text", "checked", "package", "min_changed",
    )

    private val ALLOWED_ACTIONS = setOf(
        ActionType.CLICK.name,
        ActionType.LONG_CLICK.name,
        ActionType.TYPE_TEXT.name,
        ActionType.SCROLL.name,
        ActionType.BACK.name,
        ActionType.HOME.name, // representable, but blocked by the local policy
    )

    fun parse(raw: String): ProposalParseResult {
        val text = stripWrappers(raw)
        if (text.isBlank()) return ProposalParseResult.Invalid("empty model output")

        val root = try {
            Json.parse(text)
        } catch (e: JsonException) {
            return ProposalParseResult.Invalid("not valid JSON: ${e.message}")
        }

        val obj = root as? JsonValue.Obj
            ?: return ProposalParseResult.Invalid("root must be a JSON object")

        val unknown = obj.fields.keys - ALLOWED_ROOT_KEYS
        if (unknown.isNotEmpty()) {
            return ProposalParseResult.Invalid("unknown field(s): ${unknown.joinToString(",")}")
        }

        val actionType = obj.string("action")?.uppercase()
            ?: return ProposalParseResult.Invalid("missing required field \"action\"")
        if (actionType !in ALLOWED_ACTIONS) {
            return ProposalParseResult.Invalid("unsupported action \"$actionType\"")
        }

        val targetId = obj.string("target_id")
        val label = obj.string("label")
        val reason = obj.string("reason")?.take(200) ?: ""

        val action: AgentAction = when (actionType) {
            ActionType.CLICK.name -> {
                val id = targetId ?: return ProposalParseResult.Invalid("CLICK requires \"target_id\"")
                AgentAction.Click(id, label)
            }

            ActionType.LONG_CLICK.name -> {
                val id = targetId ?: return ProposalParseResult.Invalid("LONG_CLICK requires \"target_id\"")
                AgentAction.LongClick(id, label)
            }

            ActionType.TYPE_TEXT.name -> {
                val id = targetId ?: return ProposalParseResult.Invalid("TYPE_TEXT requires \"target_id\"")
                val textValue = obj.string("text")
                    ?: return ProposalParseResult.Invalid("TYPE_TEXT requires \"text\"")
                AgentAction.TypeText(id, textValue, obj.boolean("clear_first") ?: true, label)
            }

            ActionType.SCROLL.name -> {
                val direction = when (obj.string("direction")?.uppercase()) {
                    null, "FORWARD" -> ScrollDirection.FORWARD
                    "BACKWARD" -> ScrollDirection.BACKWARD
                    else -> return ProposalParseResult.Invalid("invalid scroll direction")
                }
                AgentAction.Scroll(direction, targetId, label)
            }

            ActionType.BACK.name -> AgentAction.Back
            ActionType.HOME.name -> AgentAction.Home
            else -> return ProposalParseResult.Invalid("unsupported action \"$actionType\"")
        }

        val expectation = when (val expected = obj["expected"]) {
            null, JsonValue.Null -> null
            else -> parseExpectation(expected)
                ?: return ProposalParseResult.Invalid("invalid \"expected\" object")
        }

        return ProposalParseResult.Ok(
            AgentActionProposal(action = action, expectation = expectation, reason = reason),
        )
    }

    private fun parseExpectation(value: JsonValue): Expectation? {
        val obj = value as? JsonValue.Obj ?: return null
        val unknown = obj.fields.keys - ALLOWED_EXPECTATION_KEYS
        if (unknown.isNotEmpty()) return null

        val type = obj.string("type")?.uppercase() ?: return null
        // "target_id" is accepted as an alias for "label" (spec example)
        val label = obj.string("label") ?: obj.string("target_id")

        return when (type) {
            "NODE_VISIBLE" -> label?.let { Expectation.NodeVisible(it) }
            "NODE_NOT_VISIBLE" -> label?.let { Expectation.NodeNotVisible(it) }
            "ELEMENT_TEXT_CONTAINS" -> {
                val text = obj.string("text") ?: return null
                label?.let { Expectation.ElementTextContains(it, text) }
            }

            "ELEMENT_CHECKED" -> {
                val checked = obj.boolean("checked") ?: return null
                label?.let { Expectation.ElementChecked(it, checked) }
            }

            "TREE_CHANGED" -> {
                val min = obj.fields["min_changed"]?.let { (it as? JsonValue.Num)?.value?.toInt() } ?: 1
                Expectation.TreeChanged(min.coerceAtLeast(1))
            }

            "PACKAGE_IS" -> obj.string("package")?.let { Expectation.PackageIs(it) }
            else -> null
        }
    }

    /** Removes code fences / surrounding prose that models sometimes add anyway. */
    fun stripWrappers(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n', "")
            text = text.substringBeforeLast("```")
        }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace > 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1)
        }
        return text.trim()
    }
}
