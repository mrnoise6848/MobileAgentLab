# MobileAgent Lab

**A UI agent should be able to explain why it thinks a task is complete.**

Consider a simple task: enable offline mode. Finding a switch and issuing a click is only part of the job. The agent must observe the resulting checked state, handle a disappearing control and stop when further retries are no longer useful.

MobileAgent Lab makes that process visible on Android. A bundled target app, **Brew Lab**, supplies repeatable tasks and deliberate faults. The inspector shows each proposed action, validation decision, before/after state, verification result and recovery attempt.

## Follow one step all the way through

```text
Observe → Plan → Validate → Execute → Verify
   ↑                                  │
   └──── fresh state for recovery ─────┘
```

The planner proposes a typed action and an expectation. The local validator checks whether that action is allowed. After execution, the verifier reads the UI again and checks the expectation—such as a visible result, matching text or a checked element.

Verification polls for up to 1.5 seconds to accommodate delayed UI changes. A failure retains the state diff. Recovery waits for settling, observes again and replans within retry budgets. Step limits give the run a defined endpoint.

The [orchestrator](app/src/main/java/com/noise/mobileagentlab/agent/domain/orchestrator/AgentOrchestrator.kt) and [verifier](app/src/main/java/com/noise/mobileagentlab/agent/domain/verify/Verifier.kt) contain this loop. [Verification details](docs/verification.md) explain the expectation model.

## The target app is also the test fixture

Brew Lab includes search, results, details, settings and a stress screen. The fixed evaluation suite asks both whether normal tasks succeed and whether known faults terminate with the expected reason.

| Suite cases | What they exercise |
|---|---|
| Settings, search/type, open result, back, toggle, scroll-to-target | Goal sequencing and observable completion |
| Missing target | Target-not-found handling |
| Disappearing target | Re-observation and verification failure |
| Step exhaustion | Bounded execution |

Reports derive steps, retries, timings and verification failures from recorded runs. An expected-failure case can pass the suite while its task correctly fails; the [evaluation definitions](docs/evaluation.md) make that distinction explicit. Traces retain the reasoning behind the aggregate numbers.

## Local policy stays in charge

The default planner is deterministic and works without network access. An optional LLM integration can propose schema-validated actions, but it passes through the same local checks.

Only the Brew Lab package is allowlisted. The policy permits click, long-click, typing, scrolling and back; it rejects Home. Detected sensitive fields are blanked at extraction and blocked for actions. Accessibility is user-enabled, and runs start explicitly. Stop is cooperative and checked before execution.

Observation is bounded to 400 nodes/depth 40 and normalized to at most 120 elements. These limits keep context controlled, with the trade-off that some controls may be omitted. [Action model](docs/action-model.md) · [Safety](docs/safety.md) · [Privacy](docs/privacy.md)

## Run the experiment

```bash
./gradlew assembleDebug
adb install -r demoapp/build/outputs/apk/debug/demoapp-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable **Settings → Accessibility → MobileAgent Lab Observer**. In Run, select a task with the local planner and press Start. Expand a step, inspect Traces, then use **Run suite (9)** in Eval. Try the fault tasks and Stop as well as successful flows.

## Implementation and current reach

Two modules separate the host and target. Pure Kotlin ports isolate planning, observation, execution and launch from Android adapters; Compose renders controller state. Existing JVM tests cover the loop, expectations, policies, normalization, redaction and reports: `./gradlew testDebugUnitTest`. [Architecture](docs/architecture.md) · [Build/static review record](docs/review.md)

The lab is scoped to one demo app. Verification sees accessibility labels/state rather than arbitrary visual changes; sensitive-field detection is heuristic. Traces, metrics and settings are in memory, with at most 20 retained runs. A completed device evaluation report and live-provider LLM validation remain open work.
