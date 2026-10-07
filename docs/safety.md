# Safety — Safe Execution Boundary

The safety policy is **code in this repository**, not prose in a prompt. A model
(present or future) cannot widen it: every proposal is validated locally before
anything touches the device.

## 1. What the agent may do

| Allowed | Where enforced |
|---|---|
| `CLICK`, `LONG_CLICK` | `SafetyPolicy.allowedActions` → `ActionValidator` |
| `TYPE_TEXT` into non-sensitive fields | same, plus `SensitiveFieldDetector` |
| `SCROLL` | same |
| `BACK` (system back) | same |

## 2. What is blocked

| Blocked | Where enforced |
|---|---|
| `HOME` (leaving the target) | in the action model, **not** in `allowedActions` → `ACTION_NOT_ALLOWED` |
| Any package except `com.noise.mobileagentlab.demo` | `SafetyPolicy.allowedPackages` → `boundaryViolation()` (checked by validator *and* orchestrator) |
| Password / OTP / PIN / CVV / card / payment / account-security fields | `SensitiveFieldDetector` during extraction (`UiNode.sensitive`) → `SENSITIVE_TARGET_BLOCKED` |
| Text that looks like a secret (4+ digit runs, secret-like labels) | `SensitiveFieldDetector.looksLikeSecret()` in `ActionValidator` |
| Acting when the target app is not in the foreground | `boundaryViolation(observed, foreground)` |
| Acting after the user pressed Stop | `stopRequested` checked before observation *and* immediately before execution |
| Shell commands, intents, raw accessibility calls, arbitrary code | **not representable**: the only input shape is the `AgentAction` sealed type, produced exclusively by `ProposalSchema` |
| Hidden background control | runs start only from an explicit user action; the loop always terminates (`maxSteps`, bounded retries) |

## 3. Boundaries checked on every step

```text
service connected?            → else SERVICE_UNAVAILABLE
target launched to foreground?→ else TARGET_LEFT_FOREGROUND
snapshot.package ∈ allowlist? → else PACKAGE_NOT_ALLOWED  (run aborted)
foreground ∈ allowlist?       → else PACKAGE_NOT_ALLOWED  (run aborted)
action type allowed?          → else ACTION_NOT_ALLOWED   (step rejected)
target exists/enabled?        → else TARGET_*             (step rejected)
target sensitive?             → else SENSITIVE_TARGET_BLOCKED
target capability matches?    → else TARGET_NOT_ACTIONABLE
execute → verify              → else VERIFICATION_FAILED (+ bounded recovery)
```

`SafetyPolicy.default()` is the only policy used by the app. It is intentionally
narrow: one target package, five action types, no `HOME`, no uninstall/install,
no permission handling, no authentication flows.

## 4. Secrets and privacy

* The agent never asks for, stores or transmits passwords, OTPs, payment data,
  messages or account security settings — and the demo target app contains none.
* Traces store `TYPE` actions with a 24-character preview only, and every stored
  string goes through the same sensitive-term heuristics.
* Accessibility trees are processed **on device** and kept in memory only.
  Nothing is uploaded by default.
* No screenshots, no media capture, no `AccessibilityEvent` payloads are logged.

### If the optional remote LLM planner is enabled

Exactly these fields are sent to the endpoint **you** configure:

1. task title/description and the current goal,
2. the compact normalized screen state (`UiNormalizer` output: element ids,
   labels, bounds, roles, checked/disabled/sensitive flags),
3. the last ≤8 step summaries (action label + verified/failed),
4. the fixed schema instructions.

Never sent: raw `AccessibilityNodeInfo` structures, view ids of other apps,
screenshots, keystrokes outside the agent's own demo typing, credentials, or any
data from apps outside the allowlist (the agent never observes them for planning).

The key/endpoint are held in memory only and are never written to traces.

## 5. Explicit non-goals

This project deliberately does **not** implement: unrestricted device control,
background/autonomous operation, permission-dialog bypass, authentication
bypass, credential collection, app installation/uninstallation, or control of
apps other than the allowlisted demo target.
