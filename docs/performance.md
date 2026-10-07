# Performance / Memory Review

Phase 23 — audit of every hot path against the spec's "avoid" list.
All bounds are hard constants in code, not conventions.

## UI tree processing

| Concern | Where | Bound |
|---|---|---|
| Unbounded traversal | `UiTreeExtractor` | `MAX_NODES = 400`, `MAX_DEPTH = 40`, invisible subtrees pruned |
| Retaining `AccessibilityNodeInfo` trees | `UiTreeExtractor`, `AgentAccessibilityService`, `NodeResolver` | only primitives (`UiNode`, `NodeBounds`) are copied; no node reference survives the call that read it; the service keeps no nodes at all |
| Per-node allocations | `UiTreeExtractor.boundsOf` | one reused `Rect` per extraction (extraction is main-thread only) |
| Long text | `UiTreeExtractor.clip` | 120 chars per text field; normalizer caps labels at 80 |
| Repeated traversal | `NodeResolver` | label fallback capped at `MAX_NODES` and stops at first exact match; called at most once per failed resolution (one fallback attempt) |

## Normalization & verification

- `UiNormalizer.normalize` is O(nodes) with a 120-element budget; the compact
  state is what planners and diffs operate on — raw snapshots are never stored.
- **Phase 23 fix:** the orchestrator already normalized the pre-action snapshot;
  `Verifier.verify(beforeState = state)` now reuses it instead of normalizing
  the same snapshot a second time every step (was: 2 normalizations/step).
- The verifier polls at 120 ms with a 1.5 s timeout — at most ~13 observations
  per action, each bounded by the extractor caps.

## Event flow

- `AccessibilityBridge.onUiEvent` throttles to 1 event / 100 ms and coalesces
  into a `MutableSharedFlow(extraBufferCapacity = 1, DROP_OLDEST)` — a rapid UI
  produces O(time) events, not O(changed views).
- Snapshots are **pulled on demand, never cached per event**; the bridge keeps
  only `ForegroundInfo` (2 strings).
- `awaitQuietPeriod` waits for quiet instead of sleeping a fixed amount.

## Storage / traces

- `TraceStore`: bounded to 20 finished runs (FIFO), in memory only, no file I/O.
- A run stores at most `maxSteps` (≤ 15 default) steps; each step keeps the
  compact diff (labels, not trees). No raw snapshot is ever persisted.
- Metrics and evaluation read the same bounded list — no duplicate retention.
- Redaction happens at render time (`TraceRedactor`) over the same bounded data.

## UI recomposition

- Live state is a single `StateFlow<AgentRunState>`; `publishProgress()` fires
  once per step (and on verification completion), not per frame.
- Step lists are replaced immutably per update; cards are keyed (`step.index`,
  `run.runId`) so lazy lists diff instead of rebuilding.
- Long text blocks render only when a card is expanded.

## Threading

- Node reads/execution: `Dispatchers.Main` (the window's tree belongs to it).
- Planning, normalization-heavy loop work: `Dispatchers.Default`.
- Remote LLM calls: `Dispatchers.IO` with connect/read timeouts.
- Trace store: `synchronized` + `StateFlow` snapshot swap — readers never lock.

## Known, accepted costs

- `rootInActiveWindow` is read fresh on each observation (never cached): this
  is a correctness choice — a cached tree risks stale-target execution.
- One full traversal per observe + one per verify poll; with ≤ 400 nodes this
  is sub-millisecond-level work on modern devices and is what makes
  verification trustworthy.
