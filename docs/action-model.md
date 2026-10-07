# Action Model — Typed Actions and Structured Proposals

## 1. Why typed actions

The agent and any model behind it never emit executable platform commands.
The entire action vocabulary is one sealed type:

```text
AgentAction
 ├─ Click(targetId, expectedLabel, expectedBounds)
 ├─ LongClick(targetId, expectedLabel)
 ├─ TypeText(targetId, text, clearFirst, expectedLabel)
 ├─ Scroll(direction, targetId?, labelHint?)
 ├─ Back            (data object)
 └─ Home            (data object — representable, but NOT allowed by policy)
```

There is no member for shell, intents, permissions, app installation, key
events, or arbitrary accessibility calls — so no planner output (deterministic
or LLM) can ever express them. `Home` exists only so the *type* is complete;
`SafetyPolicy.default()` omits `ActionType.HOME` from `allowedActions`, so a
HOME proposal is rejected with `ACTION_NOT_ALLOWED`.

Every targeted action carries what it needs to re-locate itself on a fresh
tree: `targetId` (path id) plus the label/bounds observed at planning time.

`traceLabel` is the human-readable rendering used in steps and traces —
`TypeText` goes through `TraceRedactor.mask()` (24-char preview, redaction
rules applied).

## 2. The two producers

| Producer | Output path |
|---|---|
| `GoalPlanner` (local, default) | builds `AgentAction` directly from the current `CompactUiState` |
| `RemoteLlmPlanner` (opt-in) | free-form model text → `ProposalSchema.parse` → typed proposal |

Both land in the same `PlannerOutcome.Proposal(action, expectation, reason,
goalIndex, completesGoal)` — the orchestrator cannot tell (and does not care)
which one produced it.

## 3. `ProposalSchema` — the only door for model output

Rules, all enforced in `parse()`:

- exactly one JSON object (code fences/prose are stripped first)
- **unknown root fields rejected** — no smuggling instructions past the schema
- `action` must be a known type; required fields per action must be present
  (`target_id` for CLICK/TYPE_TEXT/LONG_CLICK, `text` for TYPE_TEXT)
- `direction` must be `FORWARD`/`BACKWARD`
- `reason` capped at 200 chars
- optional `expected` object must be one of the known deterministic
  expectation types with its required fields; unknown keys rejected

The result is `ProposalParseResult.Ok(proposal)` or `Invalid(detail)` — an
`Invalid` becomes `PlannerOutcome.Failed(PARSE_ERROR, …)`, recorded as a
`PLANNING_FAILED` step. **The schema is a filter, not the permission**: a
schema-valid proposal still passes through `ActionValidator` before anything
executes (`docs/safety.md`).

## 4. Validation order (`ActionValidator`)

```text
1. action type ∈ allowedActions            → else ACTION_NOT_ALLOWED
2. boundaryViolation(observed, foreground)  → else PACKAGE_NOT_ALLOWED
3. targetId == null (global action)         → allowed (no target checks)
4. target exists in snapshot                → else TARGET_NOT_FOUND
5. target package ∈ allowlist               → else PACKAGE_NOT_ALLOWED
6. target enabled                           → else TARGET_DISABLED
7. target not sensitive (policy)            → else SENSITIVE_TARGET_BLOCKED
8. capability matches action                → else TARGET_NOT_ACTIONABLE
   TypeText additionally: non-blank, ≤ 64 chars, not secret-looking
→ Allowed(targetId)
```

Rejected steps are recorded as `StepStatus.REJECTED` with the full reason —
never silently retried past `maxValidationFailures = 2`.
