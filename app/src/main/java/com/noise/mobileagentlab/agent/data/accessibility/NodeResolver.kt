package com.noise.mobileagentlab.agent.data.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.noise.mobileagentlab.agent.domain.model.stableLabel
import com.noise.mobileagentlab.agent.domain.normalize.UiNormalizer

/**
 * Phase 7 — re-locates a planned target on a FRESH accessibility tree.
 *
 * The snapshot the planner saw may be stale by the time we execute, so the
 * executor never reuses an old node reference: it walks a freshly obtained root
 * and verifies the node still looks like the one that was planned.
 */
object NodeResolver {

    /** Walks the path id (`n0.1.3` = root → child 1 → child 3) on the live tree. */
    fun resolveById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (root == null || !id.startsWith("n0")) return null
        val parts = id.split('.')
        var current: AccessibilityNodeInfo = root
        for (i in 1 until parts.size) {
            val index = parts[i].toIntOrNull() ?: return null
            if (index < 0 || index >= current.childCount) return null
            val child = current.getChild(index) ?: return null
            current = child
        }
        return current
    }

    /**
     * Best-effort label search used when the path id no longer resolves.
     * Prefers an exact, enabled match; otherwise the first containment match
     * (pre-order). Traversal is capped by [UiTreeExtractor.MAX_NODES].
     */
    fun findByLabel(
        root: AccessibilityNodeInfo?,
        label: String,
        accepts: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val wanted = UiNormalizer.normalizeLabel(label)
        if (wanted.isEmpty()) return null

        var visited = 0
        var containsMatch: AccessibilityNodeInfo? = null
        var exactMatch: AccessibilityNodeInfo? = null

        fun visit(node: AccessibilityNodeInfo) {
            if (visited >= UiTreeExtractor.MAX_NODES || exactMatch != null) return
            visited++
            val nodeLabel = rawLabel(node)
            if (nodeLabel.isNotEmpty() && accepts(node)) {
                val normalized = nodeLabel.equals(wanted, ignoreCase = true)
                if (normalized) {
                    if (exactMatch == null && node.isEnabled) exactMatch = node
                } else if (nodeLabel.contains(wanted, ignoreCase = true)) {
                    if (containsMatch == null) containsMatch = node
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
            }
        }

        visit(root)
        return exactMatch ?: containsMatch
    }

    /** First enabled scrollable node in pre-order, or `null`. */
    fun firstScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var visited = 0
        var found: AccessibilityNodeInfo? = null

        fun visit(node: AccessibilityNodeInfo) {
            if (visited >= UiTreeExtractor.MAX_NODES || found != null) return
            visited++
            if (node.isEnabled && (node.isScrollable)) {
                found = node
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
            }
        }

        visit(root)
        return found
    }

    fun labelOf(node: AccessibilityNodeInfo): String = rawLabel(node)

    /** Same identity rule the normalizer uses, applied to a live node. */
    private fun rawLabel(node: AccessibilityNodeInfo): String = UiNormalizer.normalizeLabel(
        stableLabel(
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            stateDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                node.stateDescription?.toString().orEmpty()
            } else {
                ""
            },
            editable = node.actionList?.any {
                it.id == AccessibilityNodeInfo.ACTION_SET_TEXT
            } == true,
        ),
    )

    fun boundsOf(node: AccessibilityNodeInfo): Rect {
        val rect = Rect()
        return try {
            node.getBoundsInScreen(rect)
            rect
        } catch (e: Exception) {
            Rect()
        }
    }
}
