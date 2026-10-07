package com.noise.mobileagentlab.agent.domain.model

/**
 * Machine-readable reason a step/run did not succeed.
 *
 * Failures are first-class: they are recorded in traces, metrics and the
 * evaluation report. Nothing is silently swallowed.
 */
enum class FailureReason(val message: String) {
    SERVICE_UNAVAILABLE("Accessibility service is not available"),
    NO_UI_OBSERVED("Could not observe the user interface"),
    TARGET_LEFT_FOREGROUND("Allowlisted target app is not in the foreground"),
    TARGET_NOT_FOUND("Target node no longer exists"),
    TARGET_NOT_ACTIONABLE("Target node does not support this action"),
    TARGET_DISABLED("Target node is disabled"),
    SENSITIVE_TARGET_BLOCKED("Refusing to act on a sensitive field"),
    PACKAGE_NOT_ALLOWED("Target is outside the allowed application scope"),
    ACTION_NOT_ALLOWED("Action type is not allowed by the safety policy"),
    INVALID_ACTION("Action failed validation"),
    EXECUTION_FAILED("Platform action did not execute"),
    VERIFICATION_FAILED("Post-action verification did not pass"),
    RETRY_LIMIT_EXCEEDED("Recovery retries exhausted"),
    STEP_LIMIT_EXCEEDED("Agent exceeded the maximum number of steps"),
    PLANNER_ERROR("Planner could not produce a valid action"),
    PARSE_ERROR("Planner output was not a valid structured action"),
    UI_CHANGED_DURING_ACTION("UI changed while the action was executing"),
    USER_STOPPED("Run stopped by the user"),
    UNSUPPORTED_TASK("Task is not supported by the selected planner"),
}
