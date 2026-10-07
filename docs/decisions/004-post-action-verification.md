# 004. Post-action verification is mandatory

**Status:** accepted (Phase 8–9)

## Context

`performAction() == true` only means the platform dispatched the action.
Buttons can no-op, animations can hide results, targets can vanish, wrong
elements can match. An agent that trusts the boolean lies in its traces.

## Decision

Every action is followed by:

1. a **fresh observation** (new pull, never the pre-action snapshot),
2. a deterministic `Expectation` evaluated as a pure function of
   `(before, after)` normalized states,
3. a recorded `VerificationResult` with attempts, elapsed time and the
   `StateDiff` — success *or* failure.

Default expectations are real checks (`TreeChanged(1)`,
`ElementTextContains`) — there is no "assume success" path. Task completion is
only declared when `task.completion` itself verifies.

## Consequences

+ Success rates in metrics/evaluation mean something.
+ Failed verification triggers bounded recovery instead of silent drift.
+ Every step is debuggable after the fact (diff + detail).
− Slower steps (poll up to 1.5 s) — bounded and visible in latency metrics.
