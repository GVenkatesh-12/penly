# Architecture Overview

> **The document/ink model must not know about Compose UI.**

This is the single most important rule in Penly. It enables testability without Android UI, future desktop/iOS/KMP work, and keeps performance-critical code free of UI framework concerns.

Source of truth: [plan.md §6–7, §41–42, §68](../plan.md#6-architecture); master-plan extensions in [plan.md §75](../plan.md#75-master-plan-architecture-strategy) and ADR-013..019.

---

## 1. Layered, modular, unidirectional architecture

```text
                         ┌──────────────────────────┐
                         │        Android App        │
                         │      Compose + Views      │
                         └─────────────┬────────────┘
                                       │
                             feature/editor
                                       │
                         ┌─────────────▼─────────────┐
                         │     Application State     │
                         │ ViewModels / Use Cases    │
                         └─────────────┬─────────────┘
                                       │
                     ┌─────────────────┼────────────────┐
                     │                 │                │
                     ▼                 ▼                ▼
              Document Core      Ink Core          Search Core
                     │                 │                │
                     └─────────────────┼────────────────┘
                                       ▼
                                Storage Core
                                       │
                       ┌───────────────┼───────────────┐
                       ▼                               ▼
                    SQLite                         File Store
```

The high-frequency active-stroke path is the **only** exception to the generic unidirectional state flow: it uses a specialized low-latency pipeline instead of forcing every pointer sample through general application state ([plan §43](../plan.md#43-state-management)).

## 2. Module structure

```
app/

core/
  core-common/
  core-model/
  core-document/
  core-ink/
  core-geometry/
  core-renderer/
  core-storage/
  core-database/
  core-search/
  core-export/
  core-pdf/
  core-settings/
  core-telemetry/
  core-ai/          (master plan: AI providers, ADR-016)
  core-agent/       (master plan: agent framework)
  core-sync/        (master plan: sync transports + E2EE, ADR-015)
  core-plugin/      (master plan: plugin SDK + runtime, ADR-014/018)
  core-crypto/      (master plan: E2EE primitives)
  core-browser/     (master plan: clipper/reader/embeds)
  core-audio/       (master plan: audio-linked notes)
  core-knowledge/   (master plan: links/tags/graph)

feature/
  feature-home/
  feature-notebook/
  feature-editor/
  feature-settings/
  feature-knowledge/  (master plan)
  feature-ai/         (master plan)
  feature-sync/       (master plan)
  feature-plugins/    (master plan)
  feature-browser/    (master plan)

editor/
  editor-canvas/
  editor-tools/
  editor-selection/
  editor-gestures/
  editor-history/

platform/
  platform-android/
  platform-file-picker/
  platform-share/
  platform-stylus/
  platform-desktop/       (master plan: desktop shell + Skiko ink, ADR-017)
  platform-llm/           (master plan: on-device ONNX/NNAPI providers)
  platform-plugin-host/   (master plan: QuickJS host, ADR-014)
  platform-crypto/        (master plan: platform crypto backends)

testing/
  testing-fakes/
  testing-fixtures/
  testing-benchmarks/
```

### Dependency direction

```text
feature → editor/core/platform
editor  → core + platform
core    → core only
platform→ Android/desktop-specific APIs
app     → feature modules
```

### Kotlin Multiplatform (master plan, ADR-013)

Platform-free `core:*` modules are KMP: `commonMain` + `androidMain` +
`desktopMain`. The rules below apply to all source sets. AndroidX Ink stays
Android-only behind `InkAdapter`; desktop ink is Skiko (ADR-017). Persisted
formats are platform-neutral.

### Never allowed

```text
core-model → Compose
core-model → Activity
core-model → ViewModel
```

The same rules hold for all KMP source sets (`commonMain` must contain no
Compose/Activity/ViewModel imports; `desktopMain` may use Skiko/desktop APIs
only behind adapters). No downward layer may import a higher-level feature
package ([plan §68](../plan.md#68-architecture-boundary-summary)).

## 3. Boundary summary

```text
                 ┌─────────────────────┐
                 │       Compose       │
                 │     UI / Shell      │
                 └──────────┬──────────┘
                            │
                 ┌──────────▼──────────┐
                 │   Editor Coordinator │
                 └──────────┬──────────┘
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │ Ink Core │   │ Document │   │ Storage  │
        │          │   │   Core   │   │   Core   │
        └────┬─────┘   └────┬─────┘   └────┬─────┘
             │              │              │
             ▼              ▼              ▼
         InkAdapter      Repositories   SQLite/File
             │
             ▼
       AndroidX Ink
```

Key adapter boundaries that keep the architecture upgradeable ([plan §48](../plan.md#48-upgrade-compatibility)):

- `InkAdapter` around AndroidX Ink — AndroidX Ink types must never leak into business-layer classes
- PDF engine boundary (`PdfDocumentSource`, `PdfPageRenderer`, `PdfAnnotationLayer`, `PdfExporter`) — never leak a PDF implementation through the document model
- Image decoding and storage implementations behind adapters/interfaces

## 4. State management

Unidirectional flow for normal operations:

```text
User action → Intent → ViewModel / editor coordinator → Use case
   → Domain operation → Repository → State update → UI
```

The editor is composed of independent subsystems ([plan §15](../plan.md#15-canvas-architecture)):

```text
EditorSurface
│
├── InputRouter
├── GestureRouter
├── ToolController
├── InProgressInkLayer
├── DocumentRenderer
├── SelectionRenderer
├── OverlayRenderer
├── ViewportController
└── AccessibilityBridge
```

## 5. Threading model

Keep the main thread for interactive UI and minimum orchestration only ([plan §41](../plan.md#41-threading-model)):

```text
Main
 ├── input events
 ├── frame coordination
 └── UI state

Default/IO
 ├── database
 ├── file store
 ├── serialization
 ├── thumbnails
 └── exports

Default/CPU
 ├── geometry
 ├── indexing
 └── expensive transformations
```

- Never perform full serialization, PDF conversion, or database vacuuming on the main thread.
- Active stroke input must avoid unnecessary coroutine dispatching per pointer sample; prefer efficient batching and dedicated render/input APIs.
- The UI must never wait for disk I/O per input point.

## 6. Memory management

Rules ([plan §42](../plan.md#42-memory-management)):

1. Never keep every page decoded in memory.
2. Cache by size and viewport requirements.
3. Evict thumbnails aggressively.
4. Keep immutable stroke data shared where possible.
5. Avoid copying large stroke lists for every edit.
6. Use immutable snapshots carefully, not blindly.
7. Profile allocations during continuous writing.

Future optimizations (only when measured need exists): persistent page caches, compressed stroke pages, tiled rendering, native memory for extremely large geometry datasets. Do **not** prematurely introduce Rust/C++.

## 7. Error handling & logging

- Use **typed domain errors** rather than generic exceptions everywhere ([plan §35](../plan.md#35-error-handling)): `StorageError`, `DocumentCorruptError`, `UnsupportedFormatError`, `PermissionError`, `ExportError`, `RenderingError`, `MigrationError`.
- Display friendly UI messages; log diagnostic details. Never expose stack traces to users.
- Central logger with TRACE/DEBUG/INFO/WARN/ERROR levels; release builds redact personal/document contents ([plan §36](../plan.md#36-logging-and-diagnostics)). Never log handwriting payloads, note text, file contents, access tokens, or credentials.

## 8. Architecture rules checklist for changes

- [ ] No new dependency from `core*` → Compose/Activity/ViewModel
- [ ] Dependency direction respected (no upward imports)
- [ ] Stable IDs used for anything persisted (never list indexes)
- [ ] AndroidX Ink types stay behind `InkAdapter`
- [ ] No banned performance anti-patterns ([plan §67](../plan.md#67-performance-anti-patterns-to-ban))
- [ ] No user content in logs
- [ ] ADR added for significant decisions