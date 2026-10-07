# Failure Handling

Phase 19 — failures are a first-class showcase: **fail safely → capture reason → stop or recover.**
Nothing is hidden; every failure below is recorded in the run trace, shown in the
Run Inspector, and counted in metrics/evaluation.

## Catalog

| Spec example | FailureReason | Where handled | Behavior |
|---|---|---|---|
| Target node disappears | `TARGET_NOT_FOUND` | `AccessibilityActionExecutor.resolve`, `ActionValidator`, `GoalPlanner` | One label-based fallback re-resolve, then bounded retry (`maxRetriesPerAction`), then **stop** with reason |
| Wrong target selected / node changed under us | `UI_CHANGED_DURING_ACTION` | `AccessibilityActionExecutor.locate` (fresh-tree re-resolve) | Treated as execution failure: refresh UI, re-plan from the *new* state |
| UI changes unexpectedly (verification) | `VERIFICATION_FAILED` | `AgentOrchestrator` step 6 | Never assumes success: re-observe, re-plan same goal, bounded by `maxRetriesPerAction`, then **stop** |
| Action rejected by policy | `ACTION_NOT_ALLOWED`, `PACKAGE_NOT_ALLOWED`, `SENSITIVE_TARGET_BLOCKED`, `INVALID_ACTION`, `TARGET_DISABLED`, `TARGET_NOT_ACTIONABLE` | `ActionValidator` (before every execution) | Step recorded as `REJECTED`; after `maxValidationFailures` the run fails |
| Service unavailable | `SERVICE_UNAVAILABLE` | run precondition + per-step check in `AgentOrchestrator` | Run fails immediately, no action is executed |
| Agent exceeds step limit | `STEP_LIMIT_EXCEEDED` | loop bound `stepIndex < maxSteps` | Hard termination — the loop can never run past `maxSteps` |
| Target package leaves foreground | `TARGET_LEFT_FOREGROUND`, `PACKAGE_NOT_ALLOWED` | precondition + `SafetyPolicy.boundaryViolation` each step | Precondition failure aborts before step 1; mid-run boundary violation aborts the loop |
| Task cannot complete | `PLANNER_ERROR`, `PARSE_ERROR`, `NO_UI_OBSERVED`, `USER_STOPPED` | planner/outcome handling, observe retry, cooperative stop | Every path ends the run with an explicit `failureReason` |

## Guarantees

- **Bounded**: `maxSteps`, `maxRetriesPerAction = 2`, `maxPlannerFailures = 2`,
  `maxValidationFailures = 2`, `observeRetries = 1`. No unbounded loops.
- **Recover, then stop**: recovery = settle → fresh observation → re-plan.
  Never replays a stale plan; never retries past the budgets above.
- **Recorded**: every failed step keeps `status`, `failureReason`, `detail`,
  timings and the state diff in the trace (`TraceStore`, redacted).
- **Never masked**: `performAction() == false` is a failure; verification is a
  separate observation, not the API return value.

## Controlled failure demonstrations (Run Inspector → "Controlled failure cases")

| Task | Injected fault | Expected failure |
|---|---|---|
| `fault_missing_target` | planned control ("Ghost control") does not exist | `TARGET_NOT_FOUND` |
| `fault_stale_target` | "Vanishing target" disappears between observe and execute | `TARGET_NOT_FOUND` or `VERIFICATION_FAILED` |
| `fault_step_limit` | endless list, impossible goal ("Free Unicorn Latte") | `STEP_LIMIT_EXCEEDED` at `maxSteps` |

These tasks run against the `:demoapp` Stress test screen. A controlled task that
fails with one of its `expectedFailures` is reported as **FAILED AS EXPECTED**
(Run Inspector) and **PASS** (evaluation harness); any other outcome is a FAIL.
