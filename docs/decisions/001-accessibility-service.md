# 001. Accessibility Service as the only observation channel

**Status:** accepted (Phase 2)

## Context

The agent needs to read and act on another app's UI. Options: Accessibility
Service, UIAutomator/Instrumentation, screen capture + OCR, root/shizuku.

## Decision

Use a user-enabled `AccessibilityService` and nothing else.

- It is the only supported, non-privileged channel for *both* reading the node
  tree and dispatching actions to another app.
- It must be enabled manually in system settings; the app only deep-links
  there (`exported=false`, `BIND_ACCESSIBILITY_SERVICE`).
- The service observes events but pulls snapshots on demand (see
  `docs/accessibility.md`).

## Consequences

+ No privileged access, no root, Play-policy friendly, reproducible on any
  device from Settings.
+ Node-level data (labels, bounds, checked state) — far better than OCR.
− Requires a manual user step and an OS warning dialog.
− The OS may kill the service; every call site handles `null` explicitly.
