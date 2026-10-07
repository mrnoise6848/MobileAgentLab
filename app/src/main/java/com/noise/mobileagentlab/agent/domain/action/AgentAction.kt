package com.noise.mobileagentlab.agent.domain.action

import com.noise.mobileagentlab.agent.domain.model.NodeBounds
import com.noise.mobileagentlab.agent.domain.trace.TraceRedactor

/**
 * Phase 5 — strictly typed agent actions.
 *
 * The agent/LLM never emits executable platform commands (no shell, no intents,
 * no raw accessibility calls). It may only propose one of these values, which is
 * then parsed, validated by the local safety policy, and only then executed.
 *
 * Every targeted action carries enough information to re-locate its target on a
 * FRESH tree: [targetId] plus the label/bounds observed at planning time.
 */
enum class ActionType { CLICK, LONG_CLICK, TYPE_TEXT, SCROLL, BACK, HOME }

enum class ScrollDirection { FORWARD, BACKWARD }

sealed interface AgentAction {
    val type: ActionType

    /** Node this action targets, or `null` for global actions. */
    val targetId: String?

    /** Human-readable, trace-friendly rendering (already redaction-safe). */
    val traceLabel: String

    data class Click(
        override val targetId: String,
        val expectedLabel: String? = null,
        val expectedBounds: NodeBounds? = null,
    ) : AgentAction {
        override val type: ActionType get() = ActionType.CLICK
        override val traceLabel: String get() = "CLICK \"${expectedLabel ?: targetId}\""
    }

    data class LongClick(
        override val targetId: String,
        val expectedLabel: String? = null,
    ) : AgentAction {
        override val type: ActionType get() = ActionType.LONG_CLICK
        override val traceLabel: String get() = "LONG_CLICK \"${expectedLabel ?: targetId}\""
    }

    data class TypeText(
        override val targetId: String,
        val text: String,
        val clearFirst: Boolean = true,
        val expectedLabel: String? = null,
    ) : AgentAction {
        override val type: ActionType get() = ActionType.TYPE_TEXT
        override val traceLabel: String get() =
            "TYPE \"${TraceRedactor.mask(text.take(24))}\" into \"${expectedLabel ?: targetId}\""
    }

    data class Scroll(
        val direction: ScrollDirection = ScrollDirection.FORWARD,
        override val targetId: String? = null,
        val labelHint: String? = null,
    ) : AgentAction {
        override val type: ActionType get() = ActionType.SCROLL
        override val traceLabel: String get() =
            "SCROLL ${direction.name}${labelHint?.let { " \"$it\"" } ?: ""}"
    }

    data object Back : AgentAction {
        override val type: ActionType get() = ActionType.BACK
        override val targetId: String? get() = null
        override val traceLabel: String get() = "BACK"
    }

    data object Home : AgentAction {
        override val type: ActionType get() = ActionType.HOME
        override val targetId: String? get() = null
        override val traceLabel: String get() = "HOME"
    }
}
