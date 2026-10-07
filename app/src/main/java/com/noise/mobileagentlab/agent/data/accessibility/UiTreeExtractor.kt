package com.noise.mobileagentlab.agent.data.accessibility

import android.os.Build
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import com.noise.mobileagentlab.agent.domain.model.NodeBounds
import com.noise.mobileagentlab.agent.domain.model.NodeCapabilities
import com.noise.mobileagentlab.agent.domain.model.UiNode
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.safety.SensitiveFieldDetector

/**
 * Phase 3 — converts the platform accessibility tree into a bounded,
 * platform-free [UiSnapshot].
 *
 * Guarantees:
 *  - hard caps on nodes and depth (no unbounded traversal)
 *  - invisible nodes and their subtrees are pruned
 *  - only primitives are copied: no [AccessibilityNodeInfo] is retained
 *  - stable path-based ids (`n0`, `n0.1`, `n0.0.2`) usable for re-resolution
 *  - sensitive fields are flagged locally during extraction
 *
 * Access must happen on the thread that owns the window (main thread).
 */
object UiTreeExtractor {

    const val MAX_NODES = 400
    const val MAX_DEPTH = 40
    private const val MAX_TEXT_LENGTH = 120
    private const val ROOT_ID = "n0"

    fun extract(
        root: AccessibilityNodeInfo?,
        fallbackPackageName: String?,
        fallbackClassName: String?,
        sequence: Long,
    ): UiSnapshot? {
        if (root == null) return null
        val pkg = root.packageName?.toString() ?: fallbackPackageName ?: return null

        // Children ids are collected alongside each node, then frozen into
        // immutable copies at the end so the snapshot is fully read-only.
        val rawNodes = ArrayList<UiNode>(64)
        val rawChildren = ArrayList<ArrayList<String>>(64)
        var visited = 0
        var truncated = false

        fun visit(
            node: AccessibilityNodeInfo,
            id: String,
            parentId: String?,
            depth: Int,
        ): Int? {
            if (visited >= MAX_NODES || depth > MAX_DEPTH) {
                truncated = true
                return null
            }
            visited++

            val bounds = boundsOf(node)
            if (bounds.isEmpty || !node.isVisibleToUser) return null // prunes subtree

            val className = node.className?.toString().orEmpty()
            val nodePkg = node.packageName?.toString() ?: pkg
            val text = clip(node.text?.toString())
            val contentDescription = clip(node.contentDescription?.toString())
            val stateDescription =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) clip(node.stateDescription?.toString())
                else ""
            val viewId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.uniqueId else null
            val inputType = runCatching { node.inputType }.getOrDefault(InputType.TYPE_NULL)

            val capabilities = NodeCapabilities(
                clickable = node.isClickable || hasAction(node, AccessibilityNodeInfo.ACTION_CLICK),
                longClickable = node.isLongClickable ||
                    hasAction(node, AccessibilityNodeInfo.ACTION_LONG_CLICK),
                editable = hasAction(node, AccessibilityNodeInfo.ACTION_SET_TEXT),
                scrollable = node.isScrollable ||
                    hasAction(node, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD),
                focusable = node.isFocusable || hasAction(node, AccessibilityNodeInfo.ACTION_FOCUS),
                checkable = node.isCheckable ||
                    className.contains("CheckBox", ignoreCase = true) ||
                    className.contains("Switch", ignoreCase = true),
                checked = node.isChecked,
                enabled = node.isEnabled,
                visible = true,
            )

            val label = when {
                text.isNotBlank() -> text
                contentDescription.isNotBlank() -> contentDescription
                else -> stateDescription
            }
            val sensitive = SensitiveFieldDetector.isSensitive(
                label = buildString {
                    append(label)
                    if (stateDescription.isNotBlank()) append(' ').append(stateDescription)
                },
                viewId = viewId,
                isPassword = isPasswordInput(inputType),
            )

            val childIds = ArrayList<String>(node.childCount)
            val index = rawNodes.size
            rawNodes.add(
                UiNode(
                    id = id,
                    parentId = parentId,
                    childIds = emptyList(), // replaced by an immutable copy at the end
                    depth = depth,
                    className = className,
                    packageName = nodePkg,
                    text = text,
                    contentDescription = contentDescription,
                    stateDescription = stateDescription,
                    viewId = viewId,
                    bounds = bounds,
                    capabilities = capabilities,
                    sensitive = sensitive,
                ),
            )
            rawChildren.add(childIds)

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val childId = visit(child, "$id.$i", id, depth + 1)
                if (childId != null) childIds.add("$id.$i")
            }
            return index
        }

        val rootIndex = visit(root, ROOT_ID, null, 0)
        val nodes = rawNodes.mapIndexed { i, n -> n.copy(childIds = rawChildren[i].toList()) }
        val snapshot = UiSnapshot(
            sequence = sequence,
            capturedAtMs = System.currentTimeMillis(),
            packageName = pkg,
            className = fallbackClassName,
            rootId = rootIndex?.let { rawNodes[it].id },
            nodes = nodes,
            visitedNodes = visited,
            truncated = truncated,
        )
        return snapshot
    }

    private fun clip(value: String?): String =
        if (value.isNullOrBlank()) "" else value.replace(WHITESPACE, " ").trim().take(MAX_TEXT_LENGTH)

    private val WHITESPACE = Regex("\\s+")

    // Reused across the traversal: bounds are copied into NodeBounds immediately,
    // so one Rect per extraction is enough (Phase 23 — no per-node allocation).
    private val boundsScratch = Rect()

    private fun boundsOf(node: AccessibilityNodeInfo): NodeBounds {
        return try {
            node.getBoundsInScreen(boundsScratch)
            NodeBounds(boundsScratch.left, boundsScratch.top, boundsScratch.right, boundsScratch.bottom)
        } catch (e: Exception) {
            NodeBounds(0, 0, 0, 0)
        }
    }

    private fun hasAction(node: AccessibilityNodeInfo, action: Int): Boolean = try {
        node.actionList?.any { it.id == action } == true
    } catch (e: Exception) {
        false
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        if (inputType == InputType.TYPE_NULL) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
}
