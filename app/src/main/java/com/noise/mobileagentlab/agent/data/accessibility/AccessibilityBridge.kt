package com.noise.mobileagentlab.agent.data.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.noise.mobileagentlab.agent.domain.model.ForegroundInfo
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide connection between the accessibility service and the agent.
 *
 * Handles every "service may not exist" case:
 *  - disabled by the user           -> [AccessibilityStatus.Disabled]
 *  - bound                          -> [AccessibilityStatus.Connected]
 *  - destroyed while in use         -> [AccessibilityStatus.Destroyed]
 *
 * Window/content change events are throttled and coalesced into [uiEvents] so
 * observers can wait for the UI to settle without processing every event.
 * Snapshots are always pulled on demand, never cached per event (Phase 23).
 */
object AccessibilityBridge {

    /** Minimum spacing between forwarded UI change events. */
    private const val EVENT_THROTTLE_MS = 100L

    private val _status = MutableStateFlow<AccessibilityStatus>(AccessibilityStatus.Disabled)
    val status: StateFlow<AccessibilityStatus> = _status.asStateFlow()

    private val _foreground = MutableStateFlow<ForegroundInfo?>(null)
    val foreground: StateFlow<ForegroundInfo?> = _foreground.asStateFlow()

    private val _uiEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val uiEvents: SharedFlow<Unit> = _uiEvents.asSharedFlow()

    @Volatile
    private var service: AgentAccessibilityService? = null

    @Volatile
    private var lastForwardedAtMs: Long = 0L

    // --- lifecycle -------------------------------------------------------------

    internal fun attach(service: AgentAccessibilityService) {
        this.service = service
        _status.value = AccessibilityStatus.Connected
    }

    internal fun detach(service: AgentAccessibilityService) {
        if (this.service === service) {
            this.service = null
            _status.value = AccessibilityStatus.Destroyed
            _foreground.value = null
        }
    }

    fun isAvailable(): Boolean = service != null &&
        _status.value == AccessibilityStatus.Connected

    /** Never assume availability: callers must handle `null`. */
    fun serviceOrNull(): AgentAccessibilityService? = service

    // --- events ----------------------------------------------------------------

    internal fun onForegroundWindow(packageName: String, className: String?) {
        val current = _foreground.value
        if (current?.packageName != packageName || current.className != className) {
            _foreground.value = ForegroundInfo(packageName, className)
        }
    }

    internal fun onUiEvent() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastForwardedAtMs < EVENT_THROTTLE_MS) return
        lastForwardedAtMs = now
        _uiEvents.tryEmit(Unit)
    }

    /**
     * Root of the active window, to be read on the main thread only.
     * Returns `null` when the service is gone or the window is unavailable.
     */
    fun rootNode(): AccessibilityNodeInfo? = try {
        service?.rootInActiveWindow
    } catch (e: IllegalStateException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
