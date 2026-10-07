package com.noise.mobileagentlab.agent.domain.model

/**
 * Phase 3 — structured representation of the accessibility tree.
 *
 * Pure Kotlin: no `AccessibilityNodeInfo`, no `Context`, no Compose. Platform
 * conversion happens once in the data layer ([UiTreeExtractor]).
 *
 * Memory discipline: the snapshot is a FLAT pre-order list (each node stored
 * exactly once) with parent/child links by id, instead of a duplicated tree of
 * objects. Node references are never retained; only primitives are copied.
 */

data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun overlaps(other: NodeBounds): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** Capability flags the agent may rely on when validating an action. */
data class NodeCapabilities(
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
) {
    val actionable: Boolean
        get() = clickable || longClickable || editable || scrollable || checkable
}

/**
 * Stable identity label for a node.
 *
 * Editable fields keep their declared description as identity (otherwise typing
 * into a field would change its label and break re-targeting/verification);
 * everything else prefers its visible text.
 */
fun stableLabel(
    text: String,
    contentDescription: String,
    stateDescription: String,
    editable: Boolean,
): String = when {
    editable && contentDescription.isNotBlank() -> contentDescription
    text.isNotBlank() -> text
    contentDescription.isNotBlank() -> contentDescription
    else -> stateDescription
}

/**
 * One node of the accessibility tree, flattened.
 *
 * @param id stable path id (`n0`, `n0.1`, `n0.1.3`) — stable while the tree
 *           structure is unchanged, and re-resolved before every execution.
 * @param sensitive true when local heuristics classify this as a password / OTP /
 *           PIN / payment / account-security field. Sensitive nodes are never typed into.
 */
data class UiNode(
    val id: String,
    val parentId: String?,
    val childIds: List<String>,
    val depth: Int,
    val className: String,
    val packageName: String,
    val text: String,
    val contentDescription: String,
    val stateDescription: String,
    val viewId: String?,
    val bounds: NodeBounds,
    val capabilities: NodeCapabilities,
    val sensitive: Boolean,
) {
    /** Best human label: stable identity (see [stableLabel]). */
    val label: String
        get() = stableLabel(
            text = text,
            contentDescription = contentDescription,
            stateDescription = stateDescription,
            editable = capabilities.editable,
        )

    val kind: ElementKind
        get() = when {
            capabilities.editable -> ElementKind.INPUT
            capabilities.scrollable -> ElementKind.SCROLL
            capabilities.checkable -> ElementKind.TOGGLE
            capabilities.clickable -> ElementKind.BUTTON
            className.contains("Image", ignoreCase = true) -> ElementKind.IMAGE
            text.isNotBlank() -> ElementKind.TEXT
            else -> ElementKind.CONTAINER
        }
}

enum class ElementKind { BUTTON, INPUT, TOGGLE, SCROLL, TEXT, IMAGE, CONTAINER }

/** Foreground window as seen by the service (used for the execution boundary). */
data class ForegroundInfo(val packageName: String, val className: String?)

/**
 * A point-in-time snapshot of the active window.
 *
 * @param nodes pre-order flat list; parent/child links are ids
 * @param truncated true when the safety caps (max nodes / max depth) were hit —
 *        the agent must treat a truncated snapshot as incomplete
 */
data class UiSnapshot(
    val sequence: Long,
    val packageName: String?,
    val className: String?,
    val nodes: List<UiNode>,
    val truncated: Boolean,
) {
    val nodeCount: Int get() = nodes.size

    private val index: Map<String, UiNode> by lazy { nodes.associateBy { it.id } }

    fun node(id: String): UiNode? = index[id]

    companion object {
        fun empty(sequence: Long = 0L) = UiSnapshot(
            sequence = sequence,
            packageName = null,
            className = null,
            nodes = emptyList(),
            truncated = false,
        )
    }
}
