# MobileAgent Lab — Final Technical Showcase Specification & Agent Instructions

## 1. PROJECT GOAL

Build a **small, technically deep Android agent showcase** demonstrating how an AI agent can safely inspect and operate a real Android UI through the Android Accessibility framework.

This is a **technical showcase / engineering lab**, not a consumer application.

The core question is:

> **Can an AI reliably operate a real Android UI, verify the result, recover from failure, and produce a reproducible execution trace?**

The project should demonstrate:

* Android Accessibility Service
* UI hierarchy inspection
* structured UI state extraction
* typed agent actions
* action execution
* post-action verification
* retry/recovery
* execution traces
* measurable agent performance
* clean Android architecture

---

# 2. CRITICAL EXECUTION RULES

You are implementing a **Kotlin + Jetpack Compose Android project**.

### NON-NEGOTIABLE

1. **Do NOT turn this into a normal consumer app.**
2. Keep the scope small and technically focused.
3. Do not build a generic chatbot.
4. Do not build a general-purpose phone assistant.
5. Do not attempt unrestricted autonomous control of the entire device.
6. The user must explicitly start an agent run.
7. The Accessibility Service must be explicitly enabled by the user.
8. Do not bypass Android permission dialogs.
9. Do not bypass authentication mechanisms.
10. Do not collect passwords, OTP codes, payment credentials, or other secrets.
11. Do not perform destructive device actions such as uninstalling apps or wiping data.
12. Do not silently control unrelated applications.
13. Use an explicit allowlist / safe execution boundary for supported target apps.
14. Do not implement hidden or persistent autonomous background control.
15. Do not execute actions after the user has stopped the run.
16. Do NOT change the existing Gradle version if the repository already has one.
17. Do NOT change the Gradle Wrapper version.
18. Do NOT change AGP.
19. Do NOT change Kotlin version.
20. Do NOT change Java/JDK version.
21. Do NOT change existing Compose/plugin versions unnecessarily.
22. Do NOT change package/application IDs unnecessarily.
23. Reuse the existing project's architecture and dependencies where possible.
24. Do not introduce unnecessary dependencies.
25. You may reuse mature open-source libraries, Android samples, and reference implementations when appropriate.
26. Inspect licenses before using external code.
27. Do not blindly copy another repository.
28. **Do NOT run tests until ALL implementation phases are complete.**
29. Optimize token/compute usage: inspect only relevant files, avoid unnecessary rereads, avoid unnecessary builds, and avoid unnecessary explanation.
30. **Commit after every completed phase.**

---

# 3. WHY THIS PROJECT EXISTS

Many AI demos stop at:

```text
User
 ↓
LLM
 ↓
Text response
```

This project demonstrates something more difficult:

```text
Android UI
   ↓
Accessibility Tree
   ↓
Structured State
   ↓
Agent Reasoning
   ↓
Typed Action
   ↓
Android Execution
   ↓
New UI State
   ↓
Verification
```

The agent must not simply assume that an action succeeded.

It must verify the resulting UI state.

---

# 4. CORE DEMO

The primary demo should be a small, controlled task inside a supported/demo Android application.

Example:

> Open the demo app, navigate to Settings, enable a supported non-sensitive option, and verify that the option is enabled.

Another possible task:

> Open a demo app, search for "coffee", select a result, and verify that the detail page is visible.

The exact demo should use **non-sensitive, deterministic interactions**.

Avoid tasks involving:

* passwords
* OTP
* payment information
* private messages
* account security settings
* destructive actions

---

# 5. ARCHITECTURE

The core architecture should look like:

```text
                    ┌──────────────────────┐
                    │    Compose UI        │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Agent Orchestrator   │
                    └──────────┬───────────┘
                               │
             ┌─────────────────┼─────────────────┐
             ▼                 ▼                 ▼
      State Extractor    Action Planner    Run Tracker
             │                 │                 │
             ▼                 ▼                 ▼
      Accessibility      Typed Actions      Trace Store
             │                 │
             └──────────┬──────┘
                        ▼
              Accessibility Executor
                        │
                        ▼
                 Android UI
                        │
                        ▼
                 Verification
```

Keep responsibilities separate.

---

# 6. DOMAIN MODEL

Create clean domain models.

Possible models:

```kotlin
sealed interface AgentAction {
    data class Click(...)
    data class TypeText(...)
    data class Scroll(...)
    data object Back
    data object Home
}
```

Possible state models:

```text
UiSnapshot
UiNode
NodeBounds
NodeCapabilities
AgentTask
AgentStep
VerificationResult
ExecutionTrace
AgentRun
AgentRunResult
```

The domain layer must not depend directly on:

* `AccessibilityNodeInfo`
* `AccessibilityService`
* `Context`
* Compose
* Android views

Platform-specific conversion belongs in the Android/data layer.

---

# 7. PHASE 1 — EXISTING PROJECT INSPECTION

Inspect:

* project structure
* Gradle configuration
* Kotlin version
* JDK
* Compose
* dependencies
* architecture
* navigation
* state management
* existing UI
* manifest
* tests
* reusable components

Do not change foundational configuration.

Create:

```text
docs/architecture.md
```

Document:

* current architecture
* MobileAgent Lab integration
* Accessibility Service strategy
* agent/action architecture
* verification approach
* safety boundaries

### Commit after completion.

---

# 8. PHASE 2 — ACCESSIBILITY SERVICE FOUNDATION

Implement the Accessibility Service foundation.

Requirements:

* explicit user enablement
* correct manifest/service configuration
* receive accessibility events
* observe supported window/content changes
* safely dispose resources

Handle:

* service enabled
* service disabled
* target app changed
* stale node references
* inaccessible nodes
* destroyed service

Do not assume the service is always available.

### Commit after completion.

---

# 9. PHASE 3 — UI TREE EXTRACTION

Build a structured representation of the current accessibility tree.

Convert platform nodes into a simplified model:

```text
UiNode
 ├── className
 ├── text
 ├── contentDescription
 ├── bounds
 ├── clickable
 ├── enabled
 ├── editable
 ├── scrollable
 ├── focusable
 └── children
```

Do not copy every Android platform field.

Keep the representation compact.

### Requirements

* stable node identifiers where possible
* parent/child relationships
* meaningful labels
* bounds
* supported actions
* visibility
* enabled state

Avoid excessive tree duplication in memory.

### Commit after completion.

---

# 10. PHASE 4 — UI STATE NORMALIZATION

Raw accessibility trees can be noisy.

Build a normalization layer that:

* removes irrelevant nodes
* reduces duplicated labels
* normalizes whitespace
* groups meaningful elements
* keeps actionable controls
* preserves enough structure for agent reasoning

Example:

```text
Raw Accessibility Tree
        ↓
Normalization
        ↓
Compact UI State
```

The goal is to reduce agent context size and improve deterministic behavior.

### Commit after completion.

---

# 11. PHASE 5 — TYPED ACTION SYSTEM

Create a strictly typed action model.

At minimum:

```text
CLICK
TYPE_TEXT
SCROLL
BACK
```

Optional:

```text
LONG_CLICK
FOCUS
SWIPE
```

Each action should contain enough information to locate and execute its target.

Example:

```text
ClickAction
 ├── targetId
 ├── expectedLabel
 └── expectedBounds
```

Do not let the model directly generate executable Android commands.

The agent output must first be parsed into a validated action.

### Commit after completion.

---

# 12. PHASE 6 — ACTION VALIDATION

Before execution, validate:

* action type
* target existence
* target capabilities
* allowed target package
* allowed action
* safety constraints

Reject invalid actions.

Example:

```text
Agent requested:
CLICK node=42

Validation:
✓ target exists
✓ target is clickable
✓ target belongs to allowlisted package
✓ action allowed
```

or:

```text
✕ action rejected
Reason:
target is outside allowed application scope
```

### Commit after completion.

---

# 13. PHASE 7 — ACCESSIBILITY ACTION EXECUTOR

Implement actual action execution.

Use appropriate Accessibility APIs.

Support:

* click
* text input
* scroll
* back

Requirements:

* detect execution failure
* return structured result
* do not assume success
* handle stale node references
* handle UI changes during execution

Example:

```text
Click
 ↓
Accessibility action
 ↓
ExecutionResult
```

### Commit after completion.

---

# 14. PHASE 8 — POST-ACTION VERIFICATION

This is one of the most important features.

After every action:

```text
Action
 ↓
Wait for UI update
 ↓
Re-read accessibility tree
 ↓
Compare expected state
 ↓
VerificationResult
```

Example:

```text
Action:
CLICK "Search"

Expected:
Search input visible

Result:
✓ Verified
```

Failure:

```text
Action:
CLICK "Search"

Expected:
Search input visible

Result:
✕ Not verified
```

Do not mark an action successful merely because the API call returned `true`.

### Commit after completion.

---

# 15. PHASE 9 — EXPECTATION MODEL

Every action should optionally contain an expected outcome.

Examples:

```text
Click "Search"
→ expect node "Search input" to appear
```

```text
Type "coffee"
→ expect text field value to contain "coffee"
```

```text
Scroll
→ expect additional content / changed visible nodes
```

Implement deterministic verification rules.

Do not rely exclusively on an LLM to decide whether an action succeeded.

### Commit after completion.

---

# 16. PHASE 10 — AGENT PLANNER INTERFACE

Create an abstraction:

```text
AgentPlanner
```

It should receive a compact structured state and task:

```text
Task
+
UiSnapshot
```

and produce:

```text
AgentActionProposal
```

The implementation should be replaceable.

Possible implementations:

```text
Local / Fake Planner
Remote LLM Planner
```

Do not hard-code the application to a single LLM provider.

---

# 17. PHASE 11 — STRUCTURED LLM OUTPUT

If an LLM planner is used, require structured output.

Example:

```json
{
  "action": "CLICK",
  "target_id": "node_42",
  "reason": "Open the search screen",
  "expected": {
    "type": "NODE_VISIBLE",
    "target_id": "search_input"
  }
}
```

Do not accept arbitrary executable code.

Do not allow the model to generate:

* shell commands
* arbitrary intents
* raw accessibility calls
* hidden device commands

The LLM only proposes a typed action.

The local validator decides whether it is allowed.

---

# 18. PHASE 12 — AGENT ORCHESTRATOR

Implement the full loop:

```text
Task
 ↓
Observe
 ↓
Normalize UI State
 ↓
Plan Action
 ↓
Validate
 ↓
Execute
 ↓
Verify
 ↓
Success?
 ├── Yes → Continue
 └── No  → Recover / Retry / Stop
```

Limit the maximum step count.

Example:

```text
maxSteps = 15
```

Never create an infinite agent loop.

### Commit after completion.

---

# 19. PHASE 13 — RECOVERY STRATEGY

When verification fails, the agent should be able to recover.

Possible recovery:

```text
Verification failed
 ↓
Refresh UI state
 ↓
Re-plan
 ↓
Retry
```

Limit retries.

Example:

```text
Maximum retries per action: 2
```

If recovery fails:

```text
Run failed safely
```

Do not endlessly retry.

### Commit after completion.

---

# 20. PHASE 14 — SAFE EXECUTION BOUNDARY

The system must have explicit safety boundaries.

At minimum:

### Allowed

* click
* scroll
* type into non-sensitive demo fields
* back

### Restricted / blocked

* password entry
* OTP entry
* payment fields
* device security settings
* app installation/uninstallation
* system permission bypass
* destructive device actions
* actions outside the configured target app

The safety policy must be implemented locally, not only described in the prompt to the model.

### Commit after completion.

---

# 21. PHASE 15 — RUN TRACE

Record an execution trace for each run.

Example:

```text
Run #42

Step 1
Observe
42 nodes

Step 2
CLICK "Search"
✓ Verified

Step 3
TYPE "coffee"
✓ Verified

Step 4
CLICK "Search"
✓ Verified

Result
SUCCESS
```

Store useful metadata:

* step count
* action
* execution result
* verification result
* duration
* failure reason
* retry count

Do not store secrets or sensitive text.

### Commit after completion.

---

# 22. PHASE 16 — RUN INSPECTOR UI

Create a developer-focused UI.

Example:

```text
MobileAgent Lab

Task:
Find coffee and open the first result

Status:
RUNNING

Step 4 / 15

✓ Observe
✓ Click Search
✓ Type coffee
→ Open first result
```

Allow the developer to inspect the execution trace.

This is the main demo UI.

### Commit after completion.

---

# 23. PHASE 17 — PERFORMANCE METRICS

Record:

* planning latency
* action execution latency
* verification latency
* total run duration
* step count
* retries
* success/failure
* verification success rate

Example:

```text
Run Metrics

Success
92%

Average steps
5.4

Average action latency
184 ms

Verification success
96%

Average total run
4.8 s
```

These values must be calculated from real runs.

No fake metrics.

### Commit after completion.

---

# 24. PHASE 18 — EVALUATION HARNESS

Create a small deterministic evaluation framework.

Use a fixed set of safe demo tasks.

Example:

```text
Task 1
Open Settings screen

Task 2
Search for coffee

Task 3
Open first result

Task 4
Return to previous page
```

For each task record:

* success
* steps
* retries
* time
* verification failures

The evaluation should produce a simple report.

Example:

```text
Evaluation

Tasks
20

Successful
18

Success Rate
90%

Average Steps
5.6

Verification Failures
3
```

This is a major portfolio feature.

### Commit after completion.

---

# 25. PHASE 19 — FAILURE CASES

Explicitly support and demonstrate failure.

Examples:

* target node disappears
* wrong target selected
* UI changes unexpectedly
* action rejected
* service unavailable
* agent exceeds step limit
* verification fails
* target package leaves foreground

Expected behavior:

```text
Fail safely
→ capture reason
→ stop or recover
```

Do not hide failures.

Failure handling is part of the showcase.

### Commit after completion.

---

# 26. PHASE 20 — LOCAL DEMO APP / FIXED TEST TARGET

To make the project reproducible, create or include a tiny **safe demo target app** or a deterministic demo environment.

The target should contain simple screens:

```text
Home
Search
Results
Details
Settings
```

It should intentionally expose predictable accessibility nodes.

The demo target must not contain:

* real accounts
* real credentials
* payment
* private user data
* destructive operations

This allows recruiters to clone the repository and reproduce the demonstration.

### Commit after completion.

---

# 27. PHASE 21 — STATE DIFF / VERIFICATION VIEW

Show a compact before/after state comparison.

Example:

```text
Before
Search button visible

Action
CLICK Search

After
Search input visible

Verification
✓ PASS
```

For debugging:

```text
Before nodes: 42
After nodes: 47

Changed:
+ SearchInput
+ ClearButton
```

This makes the project much more compelling to technical reviewers.

### Commit after completion.

---

# 28. PHASE 22 — REPRODUCIBLE DEMO COMMAND / FLOW

Provide a clearly documented flow for running the showcase.

README should explain:

1. Build project.
2. Install the demo target.
3. Enable Accessibility Service.
4. Start MobileAgent Lab.
5. Choose a demo task.
6. Run agent.
7. Inspect trace.
8. View metrics.

The demo must be reproducible.

### Commit after completion.

---

# 29. PHASE 23 — PERFORMANCE / MEMORY

Keep UI tree processing efficient.

Avoid:

* retaining large AccessibilityNodeInfo trees
* unnecessary repeated tree traversal
* storing every raw snapshot forever
* excessive Compose recomposition
* uncontrolled trace growth

Use:

* compact normalized state
* bounded trace retention
* background processing where appropriate
* throttled updates

### Commit after completion.

---

# 30. PHASE 24 — PRIVACY / SECURITY REVIEW

Review the full implementation.

Ensure:

* no credentials collected
* no OTPs collected
* no payment information collected
* no arbitrary device commands
* no hidden background control
* no unrestricted app control
* no remote upload of accessibility trees by default
* no sensitive logs

If an external LLM provider is used, document exactly what data is sent.

Prefer compact UI state over raw accessibility structures.

Do not send unnecessary information.

### Commit after completion.

---

# 31. PHASE 25 — DOCUMENTATION

Create/update:

```text
docs/architecture.md
docs/accessibility.md
docs/action-model.md
docs/verification.md
docs/safety.md
docs/evaluation.md
docs/performance.md
docs/decisions/
```

Suggested decisions:

```text
001-accessibility-service.md
002-structured-ui-state.md
003-typed-agent-actions.md
004-post-action-verification.md
005-safe-execution-boundary.md
006-recovery-strategy.md
007-evaluation-harness.md
008-demo-target-app.md
```

### Commit after completion.

---

# 32. PHASE 26 — README / PORTFOLIO PRESENTATION

README must immediately explain the engineering problem.

Recommended opening:

```text
# MobileAgent Lab

Can an AI reliably operate a real Android UI?

MobileAgent Lab is a small Android research/engineering
showcase that connects an agent to Android Accessibility,
executes typed actions, verifies UI changes, and measures
success, latency, retries, and failures.
```

Then show:

```text
## Architecture

## Demo

## Action Model

## Verification

## Safety Boundaries

## Evaluation

## Performance

## Failure Recovery

## Screenshots

## Limitations

## Roadmap
```

Include a short demo GIF/video if possible.

Do not oversell the system as a general autonomous phone agent.

### Commit after completion.

---

# 33. PHASE 27 — FINAL STATIC REVIEW

After implementation is complete:

Review:

* architecture
* action validation
* Accessibility lifecycle
* state normalization
* verification correctness
* retry logic
* loop termination
* safety boundaries
* memory
* performance
* logs
* privacy
* unnecessary dependencies
* fake data
* debug code

Do NOT run tests yet.

### Commit after completion.

---

# 34. TESTING RULE

## DO NOT RUN TESTS UNTIL ALL IMPLEMENTATION PHASES ARE COMPLETE

During Phases 1–27:

Do NOT execute:

* unit tests
* instrumentation tests
* UI tests
* integration tests
* benchmark tests
* evaluation suites

You may inspect or modify tests, but do not execute them.

This is intentional to reduce token/compute consumption.

---

# 35. FINAL VERIFICATION

Only after all implementation phases are complete:

Run the appropriate final checks.

For example:

```text
./gradlew assembleDebug
```

Then execute:

* unit tests
* instrumentation tests
* UI tests
* action-model tests
* verification tests
* evaluation harness
* lint/static analysis

Do not modify the build configuration merely to make verification pass.

---

# 36. FINAL MANUAL VALIDATION

Validate the complete showcase:

1. Enable Accessibility Service.
2. Start a safe demo task.
3. Observe UI tree.
4. Generate typed action.
5. Validate action.
6. Execute action.
7. Verify resulting state.
8. Continue multiple steps.
9. Trigger a controlled failure.
10. Verify recovery.
11. Trigger step-limit behavior.
12. Inspect execution trace.
13. Inspect metrics.
14. Run evaluation suite.
15. Verify safe termination.
16. Disable Accessibility Service.
17. Confirm no background agent activity continues.

Only report scenarios that were actually verified.

---

# 37. DEFINITION OF DONE

The project is complete when:

* existing project is preserved
* Kotlin + Compose is used
* Gradle/toolchain versions remain unchanged unless truly impossible
* Accessibility Service works
* UI tree extraction works
* UI normalization works
* typed actions work
* action validation works
* Accessibility execution works
* post-action verification works
* recovery works
* loop termination works
* safety boundaries work
* execution trace works
* performance metrics work
* evaluation harness works
* controlled failures work
* demo target app exists
* reproducible demo works
* no sensitive data is collected
* no hidden device control exists
* no fake metrics exist
* no fake agent actions exist
* README exists
* documentation exists
* each implementation phase has a Git commit
* all implementation phases are complete

---

# 38. GIT COMMIT RULE

After every completed phase:

```text
Review
 ↓
Remove temporary/debug code
 ↓
Update documentation
 ↓
Review git diff
 ↓
Commit
```

Use meaningful commit messages, for example:

```text
feat(agent): add accessibility service
feat(agent): add ui tree normalization
feat(agent): add typed action model
feat(agent): add action verification
feat(agent): add execution trace
feat(agent): add evaluation harness
```

Do not create empty commits.

Do not combine unrelated phases.

---

# 39. TOKEN / COMPUTE EFFICIENCY

Optimize for low token and compute usage.

* Do not reread the full repository repeatedly.
* Inspect only files relevant to the current phase.
* Reuse existing code.
* Avoid unnecessary dependencies.
* Avoid unnecessary refactors.
* Do not run tests during implementation.
* Do not run unnecessary builds repeatedly.
* Keep explanations concise.
* Prefer deterministic local logic over unnecessary LLM calls.
* Keep UI state compact.
* Use the smallest useful context for the planner.

Token efficiency must never sacrifice:

* correctness
* reliability
* security
* maintainability
* reproducibility

---

# 40. CONTINUOUS IMPLEMENTATION REQUIREMENT

After implementation begins:

> **Continue automatically through every implementation phase until Phase 27 is complete.**

Do not stop after planning.

Do not ask for approval between phases.

Do not wait for user confirmation.

If a genuine technical blocker occurs:

1. inspect existing project capabilities
2. inspect Android APIs
3. inspect mature open-source implementations
4. use the least invasive compatible solution
5. document the limitation

Do not silently change foundational project versions.

---

# 41. FINAL INSTRUCTION

Build **MobileAgent Lab as a small, reproducible, technically deep Android showcase**.

The project must demonstrate:

```text
Accessibility
+
Structured UI State
+
Agent Planning
+
Typed Actions
+
Action Validation
+
Real Android Execution
+
Post-Action Verification
+
Recovery
+
Evaluation
+
Metrics
```

The important engineering principle is:

> **The agent must never assume that an action succeeded.**

It must:

> **Observe → Act → Verify → Recover**

Do not build a generic chatbot.

Do not build a fake phone assistant.

Do not build unrestricted autonomous control.

Build a focused technical showcase that a senior engineer can clone, run, inspect, and understand.

**Start implementation immediately.**

**Continue through all phases automatically.**

**Do NOT run any tests until every implementation phase is complete.**

**Commit after every completed phase.**

Optimize for token/compute efficiency while keeping the implementation clean, deterministic, safe, maintainable, and technically credible.
