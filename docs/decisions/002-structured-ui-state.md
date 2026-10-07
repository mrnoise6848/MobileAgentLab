# 002. Structured UI state instead of raw accessibility trees

**Status:** accepted (Phase 3–4)

## Context

Planners (human, deterministic or LLM) must reason about a screen. Raw
`AccessibilityNodeInfo` trees are large, noisy, mutable, thread-affine and
contain platform references that go stale.

## Decision

Two immutable, platform-free layers:

1. `UiSnapshot` — bounded extraction (`MAX_NODES=400`, `MAX_DEPTH=40`),
   primitives only, path-stable ids, sensitive content blanked at source.
2. `CompactUiState` (`UiNormalizer`) — decorative nodes dropped, labels
   de-duplicated, 120-element budget, actionable controls winning.

Everything downstream (planner, expectations, diffs, traces, LLM prompt)
consumes only these two types.

## Consequences

+ No `AccessibilityNodeInfo` can leak or leak-memory outside one call.
+ Bounded, stable input → deterministic planning and small LLM prompts.
+ Sensitive content is removed before any consumer can see it.
− Some information is lost (nested structure beyond parent/child ids) —
  accepted: the executor re-reads the live tree when it needs precision.
