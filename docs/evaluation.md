# Evaluation — Harness, Metrics, Report

## 1. Principles

- **Fixed suite** (`DemoTasks.evaluation`): same tasks every run, in order.
- **Real runs only**: the report is computed from recorded `AgentRun`s in
  `TraceStore`. An empty store yields zeros — no invented numbers anywhere.
- **Deterministic**: `EvaluationHarness.buildReport(runs)` is a pure function;
  same runs → same report. Per task, the *latest* recorded run wins.
- **Failures count too**: a controlled-failure task *passes* when it failed
  with one of its declared `expectedFailures` (`AgentRun.matchesExpectation`).

## 2. The suite (9 tasks)

| # | Task | What it exercises |
|---|---|---|
| 1 | Open Settings | single click + visibility check |
| 2 | Search for coffee | click → type → click, multi-goal sequencing |
| 3 | Open first result | 4 goals incl. result list |
| 4 | Return to previous page | `BACK` + home-screen recovery |
| 5 | Enable offline mode | `ElementChecked` state verification |
| 6 | Scroll to a deep result | `ScrollUntil` bounded scrolling |
| 7 | Fault: missing target | expected `TARGET_NOT_FOUND` |
| 8 | Fault: disappearing target | expected `TARGET_NOT_FOUND` / `VERIFICATION_FAILED` |
| 9 | Fault: step limit | expected `STEP_LIMIT_EXCEEDED` at `maxSteps = 6` |

Tasks 1–6 are also the Run Inspector's demo list; 7–9 run against the
`:demoapp` Stress test screen.

## 3. Recorded per task

`success` (or failed-as-expected), `steps`, `retries`, `time (ms)`,
`verification failures`, `failureReason` — exactly the spec's fields, taken
from the run record, not recomputed heuristics.

## 4. Report shape

```text
Evaluation
Tasks 9
Executed 9
Successful …
Success Rate …%
Average Steps …
Average Retries …
Average Run … ms
Verification Failures …

<title>: PASS|FAIL … · steps n · retries n · <t> ms · verification failures n
```

`EvaluationReport` exposes `executed`, `successful`, `successRate`,
`avgSteps`, `avgRetries`, `avgDurationMs`, `totalVerificationFailures`,
`isComplete`. Partial suites (stopped early) honestly show `executed <
suiteSize` and the Eval screen marks the report as still running.

## 5. Driving it

- **UI**: Run tab → "Run suite (9)" — sequential execution with live progress
  (`EvaluationState.Running(completed, total, currentTaskTitle, report)`),
  cooperative Stop that halts the whole suite, final `Finished(report)`.
- **Metrics tab**: `MetricsAggregator` over all recorded runs (success rate,
  avg steps/retries/latency, verification success rate).
- **Traces tab**: the full redacted trace behind every number.

## 6. Latency definitions (metrics)

| Metric | Definition |
|---|---|
| plan latency | `sum(step.planMs) / steps` |
| action latency | `sum(step.executeMs) / steps with executeMs > 0` |
| verification latency | `sum(step.verifyMs) / steps with verification` |
| verification success | passed verifications / verification attempts |
| run duration | `finishedAtMs − startedAtMs` |
