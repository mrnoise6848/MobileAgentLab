# Final Static Review (Phase 27)

Performed after all implementation phases, before any test execution.

| Area | Verdict | Notes |
|---|---|---|
| Architecture | ✅ | strict `ui → controller → domain ← data` layering; ports in domain, Android in data; no DI/nav/serialization deps |
| Action validation | ✅ | two gates per step: orchestrator boundary check + `ActionValidator` (order documented in `docs/action-model.md` §4); rejections recorded |
| Accessibility lifecycle | ✅ | user-enabled only; `attach`/`detach` publish status; `rootNode()` null-safe; no node references retained past a call |
| State normalization | ✅ | bounded (400/40 → 120), sensitive content blanked at source, no raw snapshots retained |
| Verification correctness | ✅ | fresh observation, pure expectations, no reliance on `performAction` boolean; failed state + diff preserved |
| Retry logic | ✅ | `maxRetriesPerAction`/`maxPlannerFailures`/`maxValidationFailures` = 2, counter reset only on verified success |
| Loop termination | ✅ | `stepIndex < maxSteps` hard bound; verifier 1.5 s timeout; quiet-period 2.5 s timeout; suite bounded; trace capped at 20 runs |
| Safety boundaries | ✅ | one allowlist package, five action types, no `HOME`; `boundaryViolation()` shared by both call sites |
| Memory | ✅ | no cached trees, no per-event snapshots, bounded traces, reused `Rect`, no platform node retention |
| Performance | ✅ | `docs/performance.md`; double normalization removed in this phase's perf pass |
| Logs | ✅ | zero `Log`/`println` in the codebase |
| Privacy | ✅ | `docs/privacy.md`; INTERNET is the only permission; backup disabled; LLM data documented |
| Unnecessary dependencies | ✅ | version catalog untouched — only the template's Compose/KotlinX set |
| Fake data | ✅ | metrics/evaluation computed from recorded runs only; empty store renders zeros |
| Debug code | ✅ | no TODO/FIXME/stubs; placeholders for METRICS/EVAL tabs were completed |

## Findings fixed during the review (dead code / drift)

Removed never-read members: `AgentRunState.lastDiff`, `AgentStep.totalMs`,
`AgentRun.verificationAttempts`, `TraceStore.latest/byId`,
`MetricsAggregator.aggregateByTask`, `AccessibilityBridge.resetForegroundTracking`,
`Expectation.Unspecified`, `ValidationOutcome.Allowed.targetId` (now a
`data object`), `CompactUiState.element/containsLabel/capturedAtMs/ignoredNodeCount`,
`UiSnapshot.capturedAtMs/rootId/visitedNodes/childrenOf/root()`,
`NodeCapabilities.focusable`, `NodeBounds.centerX/centerY/area/contains`.

Wired instead of removed: `CompactUiState.truncated/budgetExceeded` now appear
in `render()` (planner context shows when the view is incomplete);
`AgentRunState.finishedAtMs` now drives a live duration line in the inspector.

Doc drift fixed: `architecture.md` port table (`AccessibilityUiObserver`,
`AndroidTargetLauncher`, `RemoteLlmPlanner`, `AgentPlanner`), `TraceStore`
naming, `LabTab` navigation; `verification.md` no longer lists `Unspecified`.

Build: `assembleDebug` green for `:app` and `:demoapp`, **zero compiler
warnings** (one `isChecked` deprecation suppressed with a rationale comment —
still the canonical flag on minSdk 29).

Not done per spec: no test execution during phases 1–27.
