# MobileAgent Lab

A small Android research/engineering showcase that connects an agent to Android
Accessibility, executes typed actions, verifies UI changes, and measures
success, latency, retries, and failures — against a bundled, deterministic demo
target app (**Brew Lab**).

> Scope: **not a consumer app**. This is a technical showcase with a hard-coded
> local safety policy: one allowlisted package, five action types, every action
> validated before execution and verified after.

---

## Reproducible demo flow

Everything below is reproducible from a clean clone. No accounts, no network
access is required (the LLM planner is opt-in and off by default).

### 1. Build

```bash
./gradlew assembleDebug
```

Produces two APKs:

```text
app/build/outputs/apk/debug/app-debug.apk          # MobileAgent Lab
demoapp/build/outputs/apk/debug/demoapp-debug.apk  # Brew Lab (demo target)
```

Requirements: JDK 17+ (Gradle toolchain resolves it), Android SDK via
`local.properties` or `ANDROID_HOME`. No other setup.

### 2. Install the demo target

```bash
adb install -r demoapp/build/outputs/apk/debug/demoapp-debug.apk
```

Brew Lab is the **only allowlisted package** (`com.noise.mobileagentlab.demo`).
It exposes predictable accessibility nodes: Home, Search, Results, Details,
Settings and a Stress test screen (vanishing / locked targets, endless list)
used by the controlled failure cases. It contains no accounts, credentials,
payments or personal data.

### 3. Install and open MobileAgent Lab

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.noise.mobileagentlab/.MainActivity
```

### 4. Enable the Accessibility service

Open **Settings → Accessibility → MobileAgent Lab Observer → Use service**.

The app never enables this itself; the Run tab shows `CONNECTED` when the
service is bound and offers a shortcut to system settings.

### 5. Choose a demo task

On the **Run** tab, pick one of the 6 safe demo tasks (Open Settings, Search
for coffee, Open first result, Return to previous page, Enable offline mode,
Scroll to a deep result) or one of the 3 controlled failure cases. Pick a
planner: local deterministic `GoalPlanner` (default) or the opt-in remote LLM
planner.

### 6. Run the agent

Press **Start run**. The agent, per step:

```text
observe → normalize → plan → validate (local safety policy)
        → execute → verify (fresh observation) → …
```

Watch the live step stream: timings, node counts, validation/execution details,
and the before/after state diff. **Stop** is cooperative and checked before
every execution.

### 7. Inspect the trace

**Traces** tab: every finished run, redacted and bounded (max 20 runs), with
per-step plan/exec/verify timings, expectations, verification results and the
state diff.

### 8. View metrics

**Metrics** tab: success rate, average steps/retries/latencies, verification
success — all computed from the recorded runs (empty store shows zeros, never
invented numbers).

**Eval** tab: run the full fixed suite (9 tasks incl. controlled failures) and
get a per-task + aggregate report:

```text
Evaluation
Tasks 9
Success Rate …%
Average Steps …
Verification Failures …
```

### 9. (Optional) repeatable evaluation

```bash
# In the app: Eval tab → "Run suite (9)"
```

Controlled failure tasks **pass when they fail with their expected reason**
(`fault_missing_target` → `TARGET_NOT_FOUND`, `fault_step_limit` →
`STEP_LIMIT_EXCEEDED`, …). Anything else is reported as FAIL.

---

## Safety in one paragraph

The agent cannot leave its sandbox: `SafetyPolicy` allowlists exactly one
package and five action types (`CLICK`, `LONG_CLICK`, `TYPE_TEXT`, `SCROLL`,
`BACK` — `HOME` is not allowed), blocks sensitive fields (password/OTP/PIN/
payment), and is consulted **before every execution** by the orchestrator and
again by the validator. Nothing executes after Stop. All loops are bounded
(`maxSteps`, retry budgets). See [docs/safety.md](docs/safety.md).

## Documentation

| Doc | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | modules, layers, data flow |
| [docs/safety.md](docs/safety.md) | allowlist, sensitive-data rules, LLM data sent |
| [docs/failures.md](docs/failures.md) | failure catalog: reason → handling → demo |
