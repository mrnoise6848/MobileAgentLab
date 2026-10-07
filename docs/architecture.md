# MobileAgent Lab — Architecture

## 1. What this project is

MobileAgent Lab is a **technical showcase / engineering lab**, not a consumer app.

It answers one question:

> Can an AI reliably operate a real Android UI, verify the result, recover from
> failure, and produce a reproducible execution trace?

The pipeline it demonstrates:

```text
Android UI
   ↓  AccessibilityService
Accessibility Tree
   ↓  UiTreeExtractor
Structured UiSnapshot
   ↓  UiNormalizer
CompactUiState            ← smallest useful context
   ↓  AgentPlanner (replaceable: GoalPlanner | LlmPlanner)
AgentActionProposal (typed, JSON-validated)
   ↓  ActionValidator + SafetyPolicy (LOCAL, authoritative)
Validated AgentAction
   ↓  AccessibilityActionExecutor
ExecutionResult
   ↓  Verifier (re-reads the tree, compares expectations)
VerificationResult + StateDiff
   ↓
Observe → Act → Verify → Recover
```

**The agent must never assume an action succeeded.** Every action is followed by
a fresh observation and a deterministic comparison against an explicit
expectation.

---

## 2. Existing project inspection (Phase 1)

Inspected without changing any foundational configuration:

| Item | Value |
|---|---|
| Gradle wrapper | 9.8.0 (unchanged) |
| AGP | 9.4.1 (unchanged) |
| Kotlin | 2.4.20 (unchanged) |
| JDK / `sourceCompatibility` | Java 11 (unchanged) |
| Compose BOM | 2026.09.00 (unchanged) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 (unchanged) |
| applicationId / namespace | `com.noise.mobileagentlab` (unchanged) |
| Plugins | `com.android.application`, `org.jetbrains.kotlin.plugin.compose` (unchanged) |
| Dependencies | Compose BOM, activity-compose, material3, ui, core-ktx, lifecycle-runtime-ktx + test deps (unchanged) |
| Architecture | single `:app`, single `MainActivity` (Hello World), Material3 theme, no navigation library, no ViewModel library |
| Tests | template `ExampleUnitTest`, `ExampleInstrumentedTest` only |

Consequences for the design:

* No new dependencies are introduced. JSON handling, metrics, evaluation and
  navigation are implemented in-project (see §7).
* No `androidx.navigation`, no `hilt`, no `serialization` plugin — navigation is
  a small explicit `when(screen)` in Compose; wiring is done by one
  application-scoped `LabController` (dependency composition without DI
  framework overhead).
* The whole agent core is written as **pure Kotlin** so it can be unit tested on
  the JVM and reasoned about without an emulator.

---

## 3. Module layout

```text
:app       — MobileAgent Lab (agent host, accessibility service, inspector UI)
:demoapp   — deterministic, safe demo target ("Brew Lab"), package
             com.noise.mobileagentlab.demo
```

`:demoapp` exists so the showcase is reproducible: a reviewer can clone, build,
install both APKs, enable the service, and run the evaluation suite against a
target that never contains accounts, credentials, payments or destructive
operations.

---

## 4. Layering (strict dependency direction)

```text
ui/  (Compose)            → LabController state only
LabController             → orchestrator, stores, planner factory
domain/  (pure Kotlin)    → NO android.* imports, NO Compose, NO Context
data/    (Android)        → AccessibilityService, node extraction, execution,
                            target launching; converts platform types to domain
```

`domain` defines the ports; `data` implements them:

| Port (domain) | Implementation (data) |
|---|---|
| `UiObserver` | `AccessibilityUiObserver` (over `AccessibilityBridge` + `AgentAccessibilityService`) |
| `ActionExecutor` | `AccessibilityActionExecutor` |
| `TargetLauncher` | `AndroidTargetLauncher` (launch-intent, allowlisted package only) |
| `AgentPlanner` | `GoalPlanner` (local, deterministic) / `RemoteLlmPlanner` (remote, optional) |

---

## 5. Component responsibilities

| Component | Responsibility |
|---|---|
| `AgentAccessibilityService` | explicit user enablement, event observation, foreground-package tracking, safe teardown, `UiObserver` entry point |
| `UiTreeExtractor` | bounded depth-first conversion `AccessibilityNodeInfo → UiSnapshot` (max nodes / max depth), copies primitives only, retains **no** platform node references |
| `UiNormalizer` | noise removal, whitespace normalization, label de-duplication, decorative-node collapsing, element budget → `CompactUiState` |
| `AgentAction` | strictly typed action model (`CLICK`, `TYPE_TEXT`, `SCROLL`, `BACK`, `LONG_CLICK`, `HOME`), never raw platform commands |
| `ProposalSchema` | the *only* way agent/LLM output becomes an action: strict JSON → typed proposal, unknown fields/keys rejected |
| `ActionValidator` + `SafetyPolicy` | local, authoritative allowlist checks (package, action type, sensitive fields, enabled state). The prompt never grants permission |
| `AccessibilityActionExecutor` | re-resolves the target on a **fresh** tree (stale-node safe), performs the platform action, returns a structured `ExecutionResult` |
| `Verifier` + `Expectation` | deterministic post-action verification: wait → re-read → compare; polls until timeout, never trusts the `true` returned by `performAction` |
| `StateDiff` | compact before/after comparison (added / removed / changed) for verification and inspector UI |
| `AgentOrchestrator` | the loop: observe → normalize → plan → validate → execute → verify → recover, with `maxSteps`, per-action retries, stop flag, bounded termination |
| `TraceStore` | bounded in-memory execution traces (redacted, no secrets), feeding metrics and the inspector |
| `MetricsAggregator` | real numbers derived from real runs only (planning / execution / verification latency, retries, verification success rate) |
| `EvaluationHarness` | fixed suite of safe demo tasks incl. controlled-failure tasks; produces a report |
| `RunInspectorScreen` | developer-facing live run view, trace list, metrics, evaluation, diff |

---

## 6. Accessibility Service strategy

* The service is **never** enabled programmatically; the user must toggle it in
  Android Settings (the app only deep-links to that screen).
* Status is published through `AccessibilityBridge` (`StateFlow<AccessibilityStatus>`):
  `Disabled`, `Connected`, `Destroyed`.
* Only *content/window* change events are used, and they are coalesced
  (throttled) — snapshots are taken **on demand** by the orchestrator, not
  retained per event (Phase 23: memory).
* The service instance is a `@Volatile` singleton reference cleared in
  `onDestroy`; every call site handles "service not available".
* The service observes the **foreground package** from
  `TYPE_WINDOW_STATE_CHANGED` to enforce the execution boundary.
* Platform nodes are used only inside a single extraction/execution call; only
  plain data (`UiSnapshot`) crosses the boundary. This makes stale
  `AccessibilityNodeInfo` references structurally impossible outside one call.

---

## 7. Deliberate minimal-dependency decisions

| Need | Solution | Why |
|---|---|---|
| JSON parse/encode for planner I/O | small in-repo `Json` codec | avoids a serialization plugin + dependency (rule: no unnecessary dependencies) |
| navigation | explicit `LabTab` enum + `when` | 5 tabs; a nav library is overkill |
| DI | `LabController` (application-scoped) | wiring is 1 graph; no runtime cost |
| state holder | `LabController` with `StateFlow` | `lifecycle-viewmodel-compose` is not in the catalog and must not be added |
| metrics | pure-Kotlin aggregator over stored runs | no fake/hard-coded numbers |
| LLM access | `HttpURLConnection`, opt-in, user-provided endpoint | no SDK dependency; provider-neutral (replaceable `AgentPlanner`) |

---

## 8. Verification approach

```text
Action executed
   ↓  Verifier (poll, default 1500 ms, up to N re-reads)
Fresh UiSnapshot ("after")
   ↓  Expectation.evaluate(before, after)  — pure, deterministic
VerificationResult(passed, reason, attempts, elapsedMs, diff)
```

Rules: `NODE_VISIBLE`, `NODE_NOT_VISIBLE`, `ELEMENT_TEXT_CONTAINS`,
`ELEMENT_CHECKED`, `TREE_CHANGED`, `PACKAGE_IS`. The result (not the API return
value) decides whether the step succeeded; failure triggers the recovery path.

---

## 9. Safety boundaries (summary — details in `docs/safety.md`)

1. Explicit start/stop by the user; no background autonomy; nothing runs after
   stop.
2. Service must be explicitly enabled by the user.
3. Target package **allowlist** = `com.noise.mobileagentlab.demo` only.
4. Allowed actions: `CLICK`, `LONG_CLICK`, `TYPE_TEXT`, `SCROLL`, `BACK`.
   (`HOME` exists in the model but is denied by the default policy.)
5. Sensitive fields (password / OTP / PIN / CVV / card / payment / account
   security heuristics) are blocked **locally** before execution.
6. Typed text is never logged raw without redaction; traces store no secrets.
7. Accessibility trees are kept local; only an opt-in LLM planner sends the
   compact state (documented in `docs/safety.md`).
8. Fixed `maxSteps`, bounded retries, bounded trace retention → guaranteed
   termination.

---

## 10. Integration plan (phase → artifact)

```text
P1  docs/architecture.md                     (this file)
P2  AgentAccessibilityService + config + bridge
P3  UiTreeExtractor → UiSnapshot
P4  UiNormalizer → CompactUiState
P5  AgentAction (typed)
P6  ActionValidator + SafetyPolicy
P7  AccessibilityActionExecutor → ExecutionResult
P8  Verifier (observe → compare)
P9  Expectation model + deterministic rules
P10 AgentPlanner port + GoalPlanner (+ LlmPlanner shell)
P11 ProposalSchema (strict structured output)
P12 AgentOrchestrator (loop, limits)
P13 recovery (refresh → re-plan → bounded retry)
P14 safe execution boundary (policy enforcement + docs/safety.md)
P15 trace store
P16 Run Inspector UI
P17 metrics aggregation
P18 evaluation harness
P19 controlled failure cases
P20 :demoapp target module
P21 state diff view
P22 reproducible demo flow (README)
P23 performance / memory discipline
P24 privacy/security review
P25 documentation set
P26 README / portfolio presentation
P27 final static review
```
