# HRNS-NOW

HRNS-NOW is the **Harness Kit Desktop Control Plane** — a Windows-first Compose
Desktop (JVM) application that lets an operator drive harness-kit day-to-day
workflows (planning, execution, closure) without memorizing PowerShell
entrypoints.

`WORKFLOW_STATE.json` remains the single machine-readable source of truth, and
PowerShell (`run-cycle.ps1`, `doctor.ps1`, `validate-ops.ps1`, ...) remains the
execution engine. HRNS-NOW never writes `WORKFLOW_STATE.json` directly, never
calls the Claude API on its own, and never re-implements harness-kit logic in
Kotlin — it only reads state, renders the currently allowed next action, and
invokes the corresponding PowerShell entrypoint as a typed command.

See `doc/hrns_now_claude_plan.md` for the full phased development plan and
`doc/hrns_now_design_pattern.md` for the normative Kotlin architecture rules
(contract realignment → state reader → CTA policy → live cockpit → process
adapter → standard daily cycle → closure/recovery → packaging).

## Project layout

- [`core`](./core/src/main/kotlin) — Harness domain model, artifact/workspace
  contracts, and (from Phase 1B onward) pure CTA policy. No Compose, file I/O,
  or process dependency.
- [`infra`](./infra/src/main/kotlin) — Filesystem probes, `WORKFLOW_STATE.json`
  parsing, and (from Phase 3 onward) the PowerShell process adapter.
- [`composeApp`](./composeApp/src/jvmMain/kotlin) — The Compose Desktop UI
  (Setup / Cockpit / Strategy / Run screens) and the `demo` package holding
  mock projection data used only in demo mode.

## Build and run (Desktop/JVM)

```shell
# on Windows
.\gradlew.bat :composeApp:run

# run all module tests
.\gradlew.bat check
```

## Working rules

- `WORKFLOW_STATE.json` is read-only from the UI's perspective.
- Unknown/unrecognized workflow state is fail-closed: every write/execute
  action is locked until the state is understood.
- No arbitrary PowerShell console, no direct Claude API calls, no automatic
  session resume.

---

Learn more about [Compose Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform.html).
