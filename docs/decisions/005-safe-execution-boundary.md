# 005. Safe execution boundary is local code, not a prompt

**Status:** accepted (Phase 6, unified Phase 14)

## Context

Prompt-level safety ("only touch the demo app") evaporates under prompt
injection, model drift or bugs. The boundary must hold for *any* planner.

## Decision

- One authoritative `SafetyPolicy`: allowlisted package
  (`com.noise.mobileagentlab.demo`), allowlisted actions
  (CLICK/LONG_CLICK/TYPE_TEXT/SCROLL/BACK), sensitive-field blocking, text
  cap, foreground enforcement.
- `SafetyPolicy.boundaryViolation(observed, foreground)` is the **single**
  boundary function, called by *both* the validator (per action) and the
  orchestrator (per step) — no second implementation to drift.
- `ActionValidator` runs between *every* planner proposal and *every*
  execution. Rejections are recorded, not swallowed.

## Consequences

+ A compromised or confused planner still cannot leave the sandbox.
+ One function to audit; `docs/safety.md` maps each rule to its code path.
− The demo cannot act outside one app — accepted, this is the point.
