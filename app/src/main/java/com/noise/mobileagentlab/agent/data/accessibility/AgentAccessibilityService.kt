package com.noise.mobileagentlab.agent.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Phase 2 — Accessibility Service foundation.
 *
 * Deliberately thin: it observes lifecycle + window/content events and forwards
 * them to [AccessibilityBridge]. It never keeps [android.view.accessibility.AccessibilityNodeInfo]
 * references beyond a single call, so stale node handles cannot leak out of the
 * extraction/execution path.
 *
 * The service can only be enabled by the user in system settings; nothing in the
 * app activates it programmatically.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrEmpty()) {
                    AccessibilityBridge.onForegroundWindow(
                        packageName = pkg,
                        className = event.className?.toString(),
                    )
                }
                AccessibilityBridge.onUiEvent()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                AccessibilityBridge.onUiEvent()
            }
        }
    }

    /** We never perform feedback gestures, so there is nothing to interrupt. */
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }
}
