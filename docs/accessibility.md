# Accessibility — Service, Observation, Extraction

## 1. The service

`AgentAccessibilityService` is deliberately thin. It does three things and
nothing else:

1. **Lifecycle** — `onServiceConnected` publishes `Connected`, `onDestroy`
   publishes `Destroyed` and clears the service reference (`AccessibilityBridge`).
2. **Foreground tracking** — `TYPE_WINDOW_STATE_CHANGED` updates
   `ForegroundInfo(package, class)`, which feeds the execution boundary.
3. **Event throttling** — window/content events are coalesced to at most
   **1 per 100 ms** into a `SharedFlow(extraBufferCapacity = 1, DROP_OLDEST)`.

It never retains an `AccessibilityNodeInfo`, never calls
`performGlobalAction`, and never starts an action itself. The service can only
be enabled by the user in system settings (`exported=false` +
`BIND_ACCESSIBILITY_SERVICE`); the app deep-links to that screen.

## 2. Pull, don't cache

Observation is **on demand**: `AccessibilityUiObserver.observe()` reads
`rootInActiveWindow` at the moment the orchestrator asks, on `Dispatchers.Main`
(the thread that owns the window's tree). Events only *signal that it may be
worth waiting* — `awaitQuietPeriod(180 ms, 2.5 s timeout)` waits for quiet
instead of sleeping a fixed amount.

Why not cache trees per event: a cached tree is a stale tree, and a stale tree
means stale-target execution — the exact failure mode `NodeResolver` exists to
prevent (see `docs/decisions/002-structured-ui-state.md`).

## 3. Extraction contract (`UiTreeExtractor`)

| Guarantee | Mechanism |
|---|---|
| Bounded | `MAX_NODES = 400`, `MAX_DEPTH = 40`; traversal stops and sets `truncated` |
| Pruned | invisible nodes (`isVisibleToUser == false`, empty bounds) skip their whole subtree |
| No platform leakage | only primitives are copied into `UiNode`; the returned `UiSnapshot` contains zero `AccessibilityNodeInfo` references |
| Stable ids | path-based (`n0.1.3`), valid for `NodeResolver.resolveById` on a *fresh* tree |
| Text bounded | 120 chars per field; whitespace collapsed |
| Sensitive at source | `SensitiveFieldDetector` runs **during extraction**; a sensitive node's `text`/`stateDescription` are stored empty — the content never exists downstream |
| Thread-safe by construction | one reused `Rect`, main-thread-only callers |

`UiSnapshot` is immutable plain data. It may be handed to the validator,
normalizer and diff freely — it is small (≤ 400 nodes × primitives) and is
never stored past the step that created it.

## 4. From snapshot to planner input

`UiNormalizer.normalize(snapshot)` produces `CompactUiState`:

- drops decorative nodes (no label, nothing actionable, no meaningful state)
- collapses labels that duplicate an ancestor (non-actionable only)
- caps the element budget at **120**, actionable controls winning over text
- derives a human `screenLabel`

The compact state — not the snapshot — is what planners, expectations, diffs
and the optional LLM prompt ever see.

## 5. Re-resolution during execution

The planner's ids can go stale between plan and execute. The executor
therefore never reuses a node reference; it re-walks a **fresh** root:

1. `NodeResolver.resolveById` — walk the path id, bounded.
2. On miss: **one** label-based fallback (`findByLabel`, exact-match-first,
   capped at `MAX_NODES`) that must also pass the `accepts` capability check.
3. Only then is `performAction` attempted; `false` is a failure, never a
   success (`docs/failures.md`).
