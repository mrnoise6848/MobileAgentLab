# 006. Recovery: refresh and re-plan, bounded

**Status:** accepted (Phase 13)

## Context

UIs change under the agent. Naive options: replay the plan (wrong — the world
moved), retry forever (hangs), or give up at first glitch (useless).

## Decision

On execution failure or failed verification:

1. **settle** — `awaitQuietPeriod(180 ms, 2.5 s)` + 120 ms delay,
2. **re-observe** — pull a fresh snapshot (the old one is discarded),
3. **re-plan the same goal from the new state** — `GoalPlanner` is
   state-driven, so the goal resolves against what is *now* on screen.

Budgets (all hard): `maxRetriesPerAction = 2`, `maxPlannerFailures = 2`,
`maxValidationFailures = 2`, `observeRetries = 1`, `maxSteps` per task.
A user Stop is checked before observation and again immediately before
execution.

## Consequences

+ Transient glitches (animation, mid-frame) are absorbed.
+ Guaranteed termination: every loop has a counter bound.
+ Stale plans are structurally impossible — recovery always re-plans.
− Two retries is deliberately low; persistent failures surface as explicit
  `FailureReason`s rather than long hidden loops.
