# 008. Bundled demo target app (`:demoapp` Brew Lab)

**Status:** accepted (Phase 20)

## Context

Reproducible evaluation needs a deterministic target. Real apps change with
updates, contain accounts/payments, and cannot be guaranteed to expose the
labels a task catalog depends on.

## Decision

Ship a second module, `:demoapp` ("Brew Lab", package
`com.noise.mobileagentlab.demo`), as the **only allowlisted target**:

- Screens: Home, Search, Results, Details, Settings, Stress test — exact
  label contract matched by `DemoTasks` (e.g. "Search coffee", "Drink name",
  "Offline mode", 14 coffee results, "Espresso", "Turkish Coffee").
- Stress test screen for fault tasks: *Vanishing target* (disappears between
  observe and execute), *Locked target*, endless list for the step-limit task.
- No accounts, credentials, payments, personal data or destructive
  operations — by construction.

## Consequences

+ Anyone can clone → build → install both APKs → run the full suite.
+ Evaluation is deterministic: same UI, same labels, same outcomes.
+ Fault injection is first-class instead of simulated.
− Contract is coupled: changing a demo label requires updating `DemoTasks`
  (kept in one place, documented in both headers).
