# Privacy / Security Review

Phase 24 — full-implementation audit against the spec's checklist. Every item
was verified **in code**, not by intention.

## Checklist

| Requirement | Status | Evidence |
|---|---|---|
| No credentials collected | ✅ | No input fields, no account model, no storage. `SensitiveFieldDetector` blocks password/OTP/PIN/payment targets **during extraction**; their content never enters the snapshot (`UiTreeExtractor`: `storedText = if (sensitive) "" else text`) |
| No OTPs collected | ✅ | OTP/2FA terms in `SENSITIVE_TERMS`; typed text that looks like a secret is rejected by `ActionValidator` (`looksLikeSecret`) |
| No payment information collected | ✅ | card/CVV/IBAN/payment terms blocked the same way; demo target has no payment surfaces |
| No arbitrary device commands | ✅ | The only executable input shape is the `AgentAction` sealed type (CLICK/LONG_CLICK/TYPE_TEXT/SCROLL/BACK). No shell, no intents-as-actions, no raw accessibility calls are representable |
| No hidden background control | ✅ | Runs start only from an explicit tap. The service only *observes* (it never calls `performGlobalAction` — HOME isn't even allowed). Loop always terminates (`maxSteps`, bounded retries) |
| No unrestricted app control | ✅ | `SafetyPolicy.allowedPackages = {com.noise.mobileagentlab.demo}`, enforced twice per step: orchestrator boundary check **and** `ActionValidator` via `boundaryViolation()` |
| No remote upload of accessibility trees by default | ✅ | Default planner is local `GoalPlanner` — zero network code runs. The remote planner requires the user to fill in endpoint + model before any request is made |
| No sensitive logs | ✅ | `grep -r "Log\.\|println\|Timber" app/src` → **no matches**. The app performs no logging at all; the only persisted text is the in-memory trace, and every line of it passes `TraceRedactor` (digit runs ≥4, e-mails, sensitive terms) |
| Document exactly what the LLM receives | ✅ | `docs/safety.md` §4 — task text, compact normalized state, ≤8 step summaries, schema instructions. Never raw trees, screenshots, or other apps' data |

## Findings fixed in this phase

1. **Sensitive values could reach the planner context.** `UiElement.value` was
   populated from `node.text` for every field, so a filled password field's
   content would appear in `CompactUiState.render()` → local planner context,
   LLM prompt and trace diffs. Now: sensitive nodes' `text`/`stateDescription`
   are blanked **at extraction**, and the normalizer drops `value`/`state` for
   any sensitive element as a second layer. The label a sensitive field keeps is
   only what it declares about itself (e.g. "Password") — never its content.
2. **`INTERNET` permission was missing** even though the opt-in LLM planner is
   documented. Added as the *only* permission in the app, with a manifest
   comment stating that no network I/O happens unless the user configures an
   endpoint.
3. **`android:allowBackup` was `true`.** The app stores nothing on disk, so
   backup had no purpose; now `false` — nothing about runs/traces/settings can
   leave via device backup. (LLM endpoint/key live in memory only.)

## Data inventory

| Data | Lives | Leaves the device |
|---|---|---|
| Accessibility snapshots | memory, per step, never stored | only in compact normalized form if the user enabled the LLM planner |
| Traces / metrics / evaluation | memory, bounded (≤20 runs), redacted | never |
| LLM endpoint / API key | memory (`LlmConfig` StateFlow) | sent to *the user's own endpoint* as `Authorization` header when configured |
| Nothing else | — | — |

## Demo target

`:demoapp` (Brew Lab) contains no accounts, no credentials, no payment, no
personal data, no destructive operations — by construction, see its README
header comments. The only "stress" surfaces are intentionally failing UI
elements for the controlled failure cases.
