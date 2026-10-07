# Verification — Never Trust the Return Value

## 1. The rule

`performAction()` returning `true` means *the platform dispatched it* — not
that the UI did what was intended. Every action is therefore followed by:

```text
wait for UI update (poll 120 ms, timeout 1500 ms)
   ↓ fresh observation (on demand, main thread)
UiNormalizer.normalize → "after" state
   ↓ Expectation.evaluate(before, after)   — pure function
VerificationResult(passed, detail, attempts, elapsedMs, diff)
```

`passed` is decided **only** by the deterministic expectation. The executor's
boolean never reaches this decision.

## 2. Expectations

| Expectation | Checks |
|---|---|
| `NodeVisible(label)` | at least one element matches (exact then containment) |
| `NodeNotVisible(label)` | no element matches |
| `ElementTextContains(label, text)` | matched element's value (or label) contains `text` |
| `ElementChecked(label, checked)` | platform `checked` flag **or** accessible state `"On"/"Off"` |
| `TreeChanged(minChanged)` | `StateDiff.changeCount ≥ min` (used for scroll/back) |
| `PackageIs(pkg)` | normalized `packageName` matches |
| `Unspecified` | explicit no-check; only for policy side-effect-free actions |

All are pure functions over two `CompactUiState`s — no observers, no time, no
non-determinism, JVM-unit-testable.

### Default expectations (`Expectations.forAction`)

When a planner declares no outcome, the action still gets a *real* check:
`TYPE_TEXT` → `ElementTextContains(label, text)`; `CLICK`/`SCROLL`/`BACK` →
`TreeChanged(1)`. There is no "assume success" default.

## 3. The verifier loop

```text
attempts = 0
loop:
  attempts++
  snapshot = observe()            → null ⇒ fail(SERVICE_UNAVAILABLE)
  state = normalize(snapshot)
  outcome = expectation.evaluate(before, state)
  passed  → return success (diff computed)
  elapsed ≥ 1500 ms or skipDelay → break
  delay(120 ms)
return fail(VERIFICATION_FAILED, last diff kept)
```

The last observed state and its diff are kept even on failure — that is what
makes a failed step debuggable in the inspector (`docs/failures.md`).

## 4. What failure triggers

A `NOT_VERIFIED` step enters recovery (bounded): settle → fresh observation →
re-plan **from the new state** — never replay a stale plan. Budget:
`maxRetriesPerAction = 2`; after that the run terminates with
`VERIFICATION_FAILED`. Task completion is only declared when
`task.completion.evaluate(before, after)` passes — after every verified step
and after the final `Complete` outcome.

## 5. State diff

`StateDiff.compute(before, after)` compares normalized elements by id:

```text
+ added      element id present only after
- removed    element id present only before
~ changed    same id, different label/state/checked/enabled signature
```

Rendered as the Phase 21 view: `Before / Action / After / Verification` plus
colored `+ / - / ~` lines, and a compact `+n −n ~n` badge on collapsed step
cards. Diffs store labels only — no trees, bounded by the 120-element budget.
