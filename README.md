# MobileAgent Lab

**Can an AI reliably operate a real Android UI?**

MobileAgent Lab is a small Android research/engineering showcase that connects
an agent to Android Accessibility, executes typed actions, verifies UI changes,
and measures success, latency, retries, and failures — against a bundled,
deterministic demo target app (**Brew Lab**).

> Not a consumer product and not a general autonomous phone agent. This is a
> technical showcase of the *engineering* behind a mobile UI agent:
> a hard safety boundary, mandatory post-action verification, bounded recovery,
> honest metrics — and a reproducible way to demonstrate all of it.

---

## Architecture

```text
Android UI
   ↓  AccessibilityService (user-enabled, observes only)
Bounded UiSnapshot (≤400 nodes, primitives only, secrets blanked at source)
   ↓  UiNormalizer
CompactUiState (≤120 elements)          ← smallest useful context
   ↓  AgentPlanner (replaceable: deterministic GoalPlanner | opt-in LLM)
Typed AgentAction + Expectation         ← the ONLY executable shape
   ↓  ActionValidator + SafetyPolicy    ← LOCAL, authoritative, per action
Executed action
   ↓  Verifier (fresh observation, pure expectation)
VerificationResult + StateDiff          ← success is observed, never assumed
   ↓
observe → plan → validate → execute → verify → recover (bounded)
```

Two Gradle modules, strict layering:

| Module | Role |
|---|---|
| `:app` | agent host: `domain/` (pure Kotlin — no `android.*`) + `data/` (accessibility implementation) + Compose inspector UI |
| `:demoapp` | deterministic safe target (**Brew Lab**) — the only allowlisted package |

Ports (`UiObserver`, `ActionExecutor`, `TargetLauncher`, `AgentPlanner`) keep
the domain testable on the JVM; `LabController` is the single composition root
(no DI framework). Details: [docs/architecture.md](docs/architecture.md).

## Demo

Reproducible end-to-end (details in [Reproducible demo flow](#reproducible-demo-flow)):

```bash
./gradlew assembleDebug
adb install -r demoapp/build/outputs/apk/debug/demoapp-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
# enable Settings → Accessibility → MobileAgent Lab Observer
# open the app → Run tab → pick a task → Start run
```

Six safe demo tasks (open settings, search & type, open result, back,
toggle, scroll-to-target) plus three controlled failure cases — each run is
live-streamed as steps with timings, validation details and a structured
before/after state diff, then kept as a redacted trace.

## Action Model

The agent never emits commands — only values of one sealed type:
`Click`, `LongClick`, `TypeText`, `Scroll`, `Back`, `Home`. Shell, intents and
raw accessibility calls are *not representable*. Model output (if the opt-in
LLM planner is enabled) must pass a strict JSON schema — unknown fields
rejected — and still faces local validation. [docs/action-model.md](docs/action-model.md).

## Verification

`performAction() == true` is never trusted. Every action is followed by a
fresh observation and a deterministic expectation evaluated as a pure function
of two normalized states (`NodeVisible`, `ElementTextContains`,
`ElementChecked`, `TreeChanged`, …). Failure keeps the last diff for debugging
and enters bounded recovery. [docs/verification.md](docs/verification.md).

```text
Before      42 nodes
Action      CLICK "Search"
After       47 nodes
Verification ✓ PASS — found 1 match(es) for "Results"

Changed (2):
+ BUTTON "Results"
~ INPUT "Drink name" value="coffee"
```

## Safety Boundaries

- **One allowlisted package** (`com.noise.mobileagentlab.demo`), checked by a
  single `boundaryViolation()` function called by both validator and
  orchestrator, every step.
- **Five allowed actions** (no `HOME`), sensitive fields (password/OTP/PIN/
  payment) blocked at extraction — their content never enters the snapshot.
- Nothing executes after Stop; all loops bounded (`maxSteps`, retry budgets).
- Runs start only from an explicit tap; the service only observes.

The policy is code, not prompt: [docs/safety.md](docs/safety.md).

## Evaluation

A fixed 9-task suite (6 safe + 3 controlled failures) run sequentially with
one tap. The report is a pure function of recorded runs — empty store shows
zeros, never invented numbers. Controlled failures **pass only when they fail
with their declared reason**, so the suite also proves the agent still fails
safely. [docs/evaluation.md](docs/evaluation.md), [docs/failures.md](docs/failures.md).

```text
Evaluation
Tasks 9
Successful …
Success Rate …%
Average Steps …
Verification Failures …
```

## Performance

Hard bounds everywhere: 400-node/40-depth extraction, 120-element compact
state, 100 ms event throttling with coalescing, snapshots pulled on demand
(never cached per event), bounded traces (20 runs, in memory), single
`StateFlow` UI updates per step. Audit: [docs/performance.md](docs/performance.md).

## Failure Recovery

Failures are the showcase: fail safely → capture reason → stop or recover.
Recovery = settle → fresh observation → re-plan from the *new* state, bounded
by retry budgets; every failure path ends in an explicit recorded
`FailureReason`. The Run Inspector offers three injectable fault cases
(vanishing target, missing control, endless list).
[docs/failures.md](docs/failures.md).

## Screenshots

Capture instructions (the repo ships no pre-rendered images — every number and
image should come from a real run):

1. **Run tab during a task** — step cards with `+n −n ~n` diff badges.
2. **Expanded step** — the Before / Action / After / Verification block.
3. **Traces tab** — expanded redacted trace of a completed run.
4. **Eval tab** — report after "Run suite (9)".

```bash
adb shell screencap -p /sdcard/run.png && adb pull /sdcard/run.png
```

## Limitations

- Scoped to **one demo app** by design; it is not a general phone agent.
- The deterministic `GoalPlanner` covers the demo task catalog; broader
  navigation would need the (opt-in, schema-constrained) LLM planner.
- Accessibility cannot read text the OS hides from services (OTP autofill
  contents, other apps' secure windows) — and this app would block them anyway.
- Verification is label/state based; purely visual changes (colors, images
  without content descriptions) are invisible to it.
- In-memory only by design: traces, metrics and settings reset with the process.
- LLM planner is untested against live providers here; it is a documented,
  opt-in integration point with strict output validation.

## Roadmap

- Screenshot-diff verification for visual-only changes (on-device, bounded).
- Persistent (opt-in) trace export with the existing redaction pipeline.
- Additional deterministic fault cases (foreground loss mid-run).
- Benchmarks for extraction/normalization latency (JMH/AndroidX benchmark).
- Recorded demo GIF once run on a physical device.

---

## Reproducible demo flow

Everything below is reproducible from a clean clone. No accounts; no network
access required (the LLM planner is opt-in and off by default).

### 1. Build

```bash
./gradlew assembleDebug
```

```text
app/build/outputs/apk/debug/app-debug.apk          # MobileAgent Lab
demoapp/build/outputs/apk/debug/demoapp-debug.apk  # Brew Lab (demo target)
```

Requirements: JDK 17+ (Gradle toolchain resolves it), Android SDK via
`local.properties` or `ANDROID_HOME`.

### 2. Install the demo target

```bash
adb install -r demoapp/build/outputs/apk/debug/demoapp-debug.apk
```

Brew Lab is the **only allowlisted package** (`com.noise.mobileagentlab.demo`).
It exposes predictable accessibility nodes: Home, Search, Results, Details,
Settings and a Stress test screen (vanishing / locked targets, endless list)
used by the controlled failure cases. No accounts, credentials, payments or
personal data.

### 3. Install and open MobileAgent Lab

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.noise.mobileagentlab/.MainActivity
```

### 4. Enable the Accessibility service

**Settings → Accessibility → MobileAgent Lab Observer → Use service**.
The app never enables this itself; the Run tab shows `CONNECTED` and offers a
shortcut to system settings.

### 5. Choose a demo task

Run tab: one of the 6 safe tasks or one of the 3 controlled failure cases;
planner = local deterministic `GoalPlanner` (default) or the opt-in remote LLM.

### 6. Run the agent

**Start run** — watch the live step stream: timings, node counts,
validation/execution details, structured state diff. **Stop** is cooperative
and checked before every execution.

### 7. Inspect the trace

**Traces** tab: every finished run, redacted and bounded (max 20 runs), with
per-step plan/exec/verify timings, expectations and diffs.

### 8. View metrics

**Metrics** tab: success rate, average steps/retries/latencies, verification
success — computed from recorded runs only (empty store shows zeros).

**Eval** tab: **Run suite (9)** → per-task verdicts + aggregate report.
Controlled failure tasks pass when they fail with their expected reason.

---

## Documentation

| Doc | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | modules, layers, data flow |
| [docs/accessibility.md](docs/accessibility.md) | service, observation, extraction contract |
| [docs/action-model.md](docs/action-model.md) | typed actions, proposal schema, validation order |
| [docs/verification.md](docs/verification.md) | expectations, verifier loop, state diff |
| [docs/safety.md](docs/safety.md) | allowlist, sensitive-data rules, LLM data sent |
| [docs/failures.md](docs/failures.md) | failure catalog: reason → handling → demo |
| [docs/evaluation.md](docs/evaluation.md) | suite, report, metric definitions |
| [docs/performance.md](docs/performance.md) | traversal/storage/recomposition bounds |
| [docs/privacy.md](docs/privacy.md) | privacy/security audit with evidence |
| [docs/decisions/](docs/decisions/) | 8 decision records (ADR style) |
