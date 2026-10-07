package com.noise.mobileagentlab.agent.domain.normalize

import com.noise.mobileagentlab.agent.domain.model.ElementKind
import com.noise.mobileagentlab.agent.domain.model.NodeBounds
import com.noise.mobileagentlab.agent.domain.model.NodeCapabilities
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot

/**
 * Phase 4 — the compact, normalized view of a screen that planners reason over.
 *
 * Normalization goals (deterministic, no LLM involved):
 *  - collapse whitespace, cap label length
 *  - drop decorative nodes (no label, nothing to do)
 *  - drop label duplicates inherited from ancestors
 *  - keep every actionable control plus the informative text nodes
 *  - hard element budget so the planner context stays small and stable
 */
data class UiElement(
    val id: String,
    val label: String,
    /** Current raw value of the node (e.g. text typed into an input). */
    val value: String,
    val kind: ElementKind,
    val bounds: NodeBounds,
    val depth: Int,
    val enabled: Boolean,
    val actionable: Boolean,
    val checked: Boolean?,
    val state: String,
    val sensitive: Boolean,
    val capabilities: NodeCapabilities,
)

data class CompactUiState(
    val sequence: Long,
    val capturedAtMs: Long,
    val packageName: String?,
    val screenLabel: String,
    val elements: List<UiElement>,
    val rawNodeCount: Int,
    val ignoredNodeCount: Int,
    val truncated: Boolean,
    val budgetExceeded: Boolean,
) {
    val size: Int get() = elements.size

    fun element(id: String): UiElement? = elements.firstOrNull { it.id == id }

    /** Exact label matches first, then containment matches; order is stable. */
    fun findElements(query: String): List<UiElement> {
        if (query.isBlank()) return emptyList()
        val q = normalizeLabel(query)
        val exact = elements.filter { it.label.equals(q, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return elements.filter { it.label.contains(q, ignoreCase = true) }
    }

    fun containsLabel(query: String): Boolean = findElements(query).isNotEmpty()

    /** Compact rendering used as planner context (and in traces). */
    fun render(): String = buildString {
        append("screen=\"").append(screenLabel).append("\" pkg=").append(packageName ?: "?")
        append(" nodes=").append(rawNodeCount).append('\n')
        for (e in elements) {
            append('#').append(e.id).append(' ')
            append(e.kind.name)
            if (!e.enabled) append(" [disabled]")
            if (e.sensitive) append(" [sensitive]")
            val stateSuffix = if (e.state.isNotBlank()) " state=\"${e.state}\"" else ""
            val checkedSuffix = when (e.checked) {
                null -> ""
                true -> " checked"
                false -> " unchecked"
            }
            append(" \"").append(e.label).append('"')
            if (e.value.isNotBlank() && e.value != e.label) append(" value=\"").append(e.value).append('"')
            if (stateSuffix.isNotEmpty()) append(stateSuffix)
            if (checkedSuffix.isNotEmpty()) append(checkedSuffix)
            append('\n')
        }
    }

    companion object {
        fun empty(packageName: String? = null) = CompactUiState(
            sequence = 0L,
            capturedAtMs = 0L,
            packageName = packageName,
            screenLabel = "",
            elements = emptyList(),
            rawNodeCount = 0,
            ignoredNodeCount = 0,
            truncated = false,
            budgetExceeded = false,
        )
    }
}

object UiNormalizer {

    const val MAX_ELEMENTS = 120
    const val MAX_LABEL_LENGTH = 80

    private val WHITESPACE = Regex("\\s+")

    fun normalize(snapshot: UiSnapshot): CompactUiState {
        val ancestorLabels = ArrayDeque<String>()
        val candidates = ArrayList<UiElement>(snapshot.nodeCount)

        for (node in snapshot.nodes) {
            // 1. structural pruning: nothing to say and nothing to do
            val label = normalizeLabel(node.label)
            val actionable = node.capabilities.actionable
            val meaningfulState = node.capabilities.checkable || node.stateDescription.isNotBlank()

            if (label.isEmpty() && !actionable && !meaningfulState) continue
            if (!node.capabilities.visible || node.bounds.isEmpty) continue

            // 2. duplicate label collapse: a plain text node repeating an ancestor's
            //    label carries no extra information for the planner.
            val duplicatedAncestor = !actionable && !meaningfulState &&
                ancestorLabels.any { it.equals(label, ignoreCase = true) }
            if (duplicatedAncestor) continue

            val element = UiElement(
                id = node.id,
                label = label,
                value = normalizeLabel(node.text),
                kind = node.kind,
                bounds = node.bounds,
                depth = node.depth,
                enabled = node.capabilities.enabled,
                actionable = actionable,
                checked = if (node.capabilities.checkable) node.capabilities.checked else null,
                state = normalizeLabel(node.stateDescription),
                sensitive = node.sensitive,
                capabilities = node.capabilities,
            )
            candidates.add(element)

            if (label.isNotEmpty()) {
                ancestorLabels.addLast(label)
                if (ancestorLabels.size > 8) ancestorLabels.removeFirst()
            }
        }

        // 3. element budget — actionable controls always win over plain text
        var budgetExceeded = false
        val elements: List<UiElement>
        if (candidates.size > MAX_ELEMENTS) {
            budgetExceeded = true
            val actionableCount = candidates.count { it.actionable }
            val informativeRoom = (MAX_ELEMENTS - actionableCount).coerceAtLeast(0)
            var informativeTaken = 0
            val keptIds = HashSet<String>(candidates.size)
            for (c in candidates) {
                if (c.actionable) {
                    keptIds.add(c.id)
                } else if (informativeTaken < informativeRoom) {
                    keptIds.add(c.id)
                    informativeTaken++
                }
            }
            elements = candidates.filter { keptIds.contains(it.id) }
        } else {
            elements = candidates
        }

        return CompactUiState(
            sequence = snapshot.sequence,
            capturedAtMs = snapshot.capturedAtMs,
            packageName = snapshot.packageName,
            screenLabel = deriveScreenLabel(elements, snapshot),
            elements = elements,
            rawNodeCount = snapshot.nodeCount,
            ignoredNodeCount = (snapshot.nodeCount - elements.size).coerceAtLeast(0),
            truncated = snapshot.truncated,
            budgetExceeded = budgetExceeded,
        )
    }

    fun normalizeLabel(value: String?): String =
        if (value.isNullOrBlank()) "" else value.replace(WHITESPACE, " ").trim().take(MAX_LABEL_LENGTH)

    private fun deriveScreenLabel(elements: List<UiElement>, snapshot: UiSnapshot): String {
        elements.firstOrNull { it.depth <= 3 && it.label.length in 2..40 && it.kind != ElementKind.INPUT }
            ?.let { return it.label }
        return snapshot.className?.substringAfterLast('.') ?: snapshot.packageName ?: ""
    }
}
