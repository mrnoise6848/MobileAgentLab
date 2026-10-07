package com.noise.mobileagentlab.agent.domain.service

/**
 * Availability of the platform accessibility connection.
 *
 * This is a pure domain value: the agent must never assume the service exists.
 * Every observation/execution entry point checks it first.
 */
sealed interface AccessibilityStatus {
    /** The user has not enabled the service (or Android disabled it). */
    data object Disabled : AccessibilityStatus

    /** The service is bound and can deliver window content. */
    data object Connected : AccessibilityStatus

    /** The service instance was destroyed while a run was possible. */
    data object Destroyed : AccessibilityStatus
}
