# 007. Evaluation harness: fixed suite, real runs, expected failures

**Status:** accepted (Phase 18)

## Context

Showcase numbers must be reproducible and honest. Fake or hand-tuned metrics
are worse than no metrics; runs that "pass" by accident are worse than
failures.

## Decision

- A **fixed 9-task suite** (`DemoTasks.evaluation`): 6 safe tasks + 3
  controlled-failure tasks, executed sequentially by the harness.
- The report is a **pure function of recorded runs**
  (`EvaluationHarness.buildReport`) — same runs, same report; empty store →
  zeros; partial suite → `executed < suiteSize` shown honestly.
- Controlled failures declare `expectedFailures`; the harness counts
  *failed-as-expected* as PASS and **any other outcome as FAIL** — so the
  suite also proves the agent still fails *safely* (it would catch an agent
  that accidentally succeeds by clicking something else, or fails with the
  wrong reason).

## Consequences

+ One tap produces a complete, checkable report.
+ The suite doubles as a regression test for the safety/recovery machinery.
− Requires the deterministic `GoalPlanner` for reproducibility; the LLM
  planner's report is explicitly labeled with its planner id.
