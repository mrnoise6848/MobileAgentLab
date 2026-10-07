# 003. Typed agent actions — sealed type, no command strings

**Status:** accepted (Phase 5, hardened Phase 11)

## Context

An agent that can emit "commands" can be prompted into emitting shell, intents
or raw accessibility calls. The safety surface must be structural, not
textual.

## Decision

- The only action representation is the `AgentAction` sealed type:
  `Click`, `LongClick`, `TypeText`, `Scroll`, `Back`, `Home`.
- The only path from model text to an action is `ProposalSchema.parse`:
  strict JSON, unknown fields rejected, required fields enforced, bounded
  strings. Anything else → `PARSE_ERROR`.
- `Home` is representable but absent from `SafetyPolicy.allowedActions`
  (denied with `ACTION_NOT_ALLOWED`).

## Consequences

+ No plausible output of an LLM can express an unauthorized capability —
  there is no enum value for it.
+ Malformed/adversarial output fails closed and is recorded as a failed step.
− Adding a capability requires code + policy change (intentional friction).
