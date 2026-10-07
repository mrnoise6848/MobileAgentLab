package com.noise.mobileagentlab.agent.domain.port

import com.noise.mobileagentlab.agent.domain.model.ForegroundInfo
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Port through which the (pure Kotlin) agent core observes the device.
 * Implemented in the data layer by [com.noise.mobileagentlab.agent.data.accessibility.AccessibilityUiObserver].
 *
 * Contract: [observe] returns `null` whenever observation is impossible
 * (service disabled, destroyed, no window). Callers must treat `null` as a
 * failure, never as an empty screen.
 */
interface UiObserver {
    val status: StateFlow<AccessibilityStatus>
    val foreground: StateFlow<ForegroundInfo?>

    /** Pull a fresh, bounded snapshot of the active window. */
    suspend fun observe(): UiSnapshot?

    /**
     * Waits until no UI change event has been seen for [quietMs].
     * Returns false when [timeoutMs] elapsed with events still arriving.
     */
    suspend fun awaitQuietPeriod(quietMs: Long = 180L, timeoutMs: Long = 1_500L): Boolean
}
