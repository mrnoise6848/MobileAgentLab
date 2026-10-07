package com.noise.mobileagentlab.agent.data.accessibility

import com.noise.mobileagentlab.agent.domain.model.ForegroundInfo
import com.noise.mobileagentlab.agent.domain.model.UiSnapshot
import com.noise.mobileagentlab.agent.domain.port.UiObserver
import com.noise.mobileagentlab.agent.domain.service.AccessibilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 3/8 — [UiObserver] implementation on top of [AccessibilityBridge].
 *
 * Snapshots are pulled on demand (never cached), always on the main thread
 * because the window's node tree belongs to that thread, and always bounded by
 * [UiTreeExtractor] caps.
 */
class AccessibilityUiObserver : UiObserver {

    private val sequence = AtomicLong(0L)

    override val status: StateFlow<AccessibilityStatus> = AccessibilityBridge.status

    override val foreground: StateFlow<ForegroundInfo?> = AccessibilityBridge.foreground

    override suspend fun observe(): UiSnapshot? = withContext(Dispatchers.Main) {
        if (!AccessibilityBridge.isAvailable()) return@withContext null
        val foreground = AccessibilityBridge.foreground.value
        val root = AccessibilityBridge.rootNode() ?: return@withContext null
        UiTreeExtractor.extract(
            root = root,
            fallbackPackageName = foreground?.packageName,
            fallbackClassName = foreground?.className,
            sequence = sequence.incrementAndGet(),
        )
    }

    override suspend fun awaitQuietPeriod(quietMs: Long, timeoutMs: Long): Boolean {
        val startedAt = System.currentTimeMillis()
        var lastEventAt = startedAt
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastEventAt >= quietMs) return true
            if (now - startedAt >= timeoutMs) return false
            val event = withTimeoutOrNull(timeoutMs - (now - startedAt)) {
                AccessibilityBridge.uiEvents.first()
                true
            }
            if (event != true) return true // nothing changed for quietMs -> settled
            lastEventAt = System.currentTimeMillis()
        }
    }
}
