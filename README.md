# MobileAgent Lab

**Inspect whether a mobile UI agent achieved the intended change, not just whether Android accepted its action.**

A click can return success while the target disappears, the wrong screen opens or the expected state never arrives. An agent that treats execution as completion cannot explain these failures or recover reliably.

MobileAgent Lab makes that boundary visible on Android. A bundled target app, **Brew Lab**, provides repeatable tasks and controlled faults. The host records each plan, local validation, action, fresh observation and verification result, so a reviewer can trace why a run advanced, retried or stopped.

## The agent loop is the product

```text
User starts a task in Brew Lab
    → observe Accessibility tree (≤400 nodes, depth ≤40)
    → normalize compact state (≤120 elements)
    → planner proposes typed action + expectation
    → local package/action/sensitive-field validation
    → execute
    → fresh observation + expectation evaluation + state diff
    → advance, re-observe/re-plan within retry budgets, or stop
```

`performAction() == true` is insufficient. Expectations such as element visibility, text and checked state are evaluated against observed state. The verifier polls for up to 1.5 seconds; a failed expectation retains a before/after diff. Recovery waits for settling and plans from a new observation instead of blindly replaying an old action.

Source: [orchestrator](app/src/main/java/com/noise/mobileagentlab/agent/domain/orchestrator/AgentOrchestrator.kt), [verifier](app/src/main/java/com/noise/mobileagentlab/agent/domain/verify/Verifier.kt). Details: [verification](docs/verification.md), [failure recovery](docs/failures.md).

## Local execution boundaries

The default deterministic planner needs no network. An optional remote LLM proposes schema-validated values; it does not bypass the local validator.

- Only `com.noise.mobileagentlab.demo` is allowlisted for actions.
- The policy allows click, long-click, typing, scrolling and back. The action model includes Home, but this policy rejects it. Shell commands and arbitrary raw accessibility calls are not action proposals.
- Sensitive-field detection blanks identified content at extraction and blocks actions on those fields. This is heuristic protection, not proof that every sensitive UI is recognized.
- Accessibility must be enabled by the user. Runs begin explicitly; the observer does not independently start tasks.
- Step and retry budgets bound the loop. Stop is cooperative and checked before execution; it does not undo an action already dispatched.

See [action model](docs/action-model.md), [safety policy](docs/safety.md) and [privacy](docs/privacy.md). The optional planner's transmitted context is documented there; live-provider operation has not been verified here.

## Evaluation distinguishes expected failure from task success

The fixed suite contains six task flows and three faults: missing target, disappearing target and step exhaustion. A fault case passes only if its recorded failure matches an expected reason. Therefore **suite pass rate is not the success rate of arbitrary phone tasks**.

Reports derive steps, retries, duration and verification failures from recorded runs. An empty store provides no performance evidence. [Evaluation definitions](docs/evaluation.md) distinguish suite verdicts from the Metrics tab's run aggregates.

No completed device evaluation report or screenshot is included yet. The next useful evidence is a real expanded verification step and a full nine-task report, accompanied by traces rather than an unsupported reliability claim.

## Run the lab

Use the configured Gradle/JDK and Android SDK toolchain:

```bash
./gradlew assembleDebug
adb install -r demoapp/build/outputs/apk/debug/demoapp-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable **Settings → Accessibility → MobileAgent Lab Observer**. Open the host's Run tab, choose a task with the local planner and press Start. Inspect the live steps and Traces; run **Run suite (9)** in Eval. Exercise Stop and the fault tasks as well as successful flows.

For automated domain checks:

```bash
./gradlew testDebugUnitTest
```

Existing tests cover planning, validation, verification, normalization, redaction, metrics and orchestration. The [static review record](docs/review.md) reports builds for both modules; it is not a device evaluation or a test report from this documentation pass.

## Architecture and limits

Two modules separate the host (`:app`) from its deterministic target (`:demoapp`). Pure Kotlin domain ports isolate observation, execution, target launch and planning from Android implementations. A single controller wires them into Compose state. See [architecture](docs/architecture.md) and [decisions](docs/decisions/).

This is a constrained lab, not a general autonomous phone agent. Accessibility-visible labels and state cannot verify purely visual changes. Bounded extraction can omit controls. Traces retain at most 20 runs in memory, and traces/settings/metrics reset with the process. Broader navigation, persistent trace export and measured extraction latency remain future work.
