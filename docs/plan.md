# State-of-the-Art Open-Source Android Handwriting App — v0.1 Plan

## Project codename
**Working name:** `Penly` (rename later)

## Version
`0.1.0-alpha.1`

## Status
Foundational engineering release. The purpose of v0.1 is to establish a production-grade document/ink engine and a polished handwriting experience without creating architectural debt that blocks PDF annotation, search, sync, AI, collaboration, or desktop support later.

---

# 1. Product Thesis

The application should feel like **digital paper with the reliability of a database**.

The primary product promise is not “many features.” It is:

> Open a page, put a supported stylus on it, and write naturally with essentially no friction.

Everything else must protect that experience.

### Product principles

1. **Writing is the primary interaction.** UI never wins over pen latency or canvas stability.
2. **Local-first.** Creating and editing a note never requires an account, network connection, or cloud service.
3. **Lossless by default.** User-created content should remain editable and recoverable.
4. **Vector/object based.** Handwriting is data, not a screenshot.
5. **Open format.** Users must be able to export their work and the format must be versioned.
6. **Crash-safe.** A process crash must not silently destroy recent work.
7. **Performance is a feature.** Performance budgets are acceptance criteria, not optional optimization work.
8. **Stable dependencies first.** Prefer stable AndroidX APIs for core paths; experimental APIs are isolated behind adapters.
9. **Modular architecture.** Core document, ink, storage and rendering code must not depend on Compose UI.
10. **Future-compatible.** v0.1 must provide stable seams for PDF, OCR, semantic search, sync, AI and desktop ports.
11. **Extensible by default.** The plugin SDK is a first-class citizen; API stability is a promise.
12. **AI is optional, never required.** Writing never requires an account, a model download, or a cloud call.
13. **Every capability is an interface.** AI, sync, OCR, search and plugins are provider-abstracted so no vendor can lock the product.
14. **Open format + interop.** The native `.penly` format plus lossless Markdown/PDF export; import from mainstream note apps.
15. **Your data, your servers.** Self-hostable sync with end-to-end encryption; the official cloud is optional, never mandatory.
16. **Plugin sandboxing is security.** Third-party code never touches notes without explicit, user-granted capabilities.

---

# 2. v0.1 Definition of Done

Version 0.1 is complete only when the following is true.

### Core writing experience

- Stylus writing works smoothly on supported Android phones/tablets.
- Pressure is preserved.
- Stylus/finger/palm input is correctly differentiated where supported.
- Pen, pencil, marker and highlighter are usable.
- Standard eraser works reliably.
- Undo/redo is deterministic and fast.
- Pan and zoom are fluid.
- There is no visible toolbar/layout jump while writing.
- Canvas remains responsive during long strokes and dense pages.
- Writing remains editable after save/reload.

### Document system

- Users can create notebooks, sections and documents/pages.
- Pages use a versioned document model.
- Page objects have stable IDs.
- Notes survive app restarts.
- Autosave occurs without blocking the UI.
- Crash recovery is implemented.
- Thumbnails are cached.
- Duplicate or orphaned content can be detected and repaired.

### Editor

- Blank, ruled, grid and dotted paper are available.
- Page navigation is fast.
- Lasso selection is implemented.
- Selected strokes can be moved, copied and deleted.
- Basic text and image objects can coexist with ink.
- Tool state survives normal configuration changes where appropriate.

### Quality

- No known data-loss bugs.
- Core flows have unit, integration and UI tests.
- Performance benchmarks exist and run in CI/nightly.
- Strict crash/error logging exists for debug and opt-in release diagnostics.
- The project can be built reproducibly from a clean checkout.
- A migration test exists for every persisted schema/document version introduced by v0.1.

---

# 3. What v0.1 Will NOT Build

Explicit non-goals prevent the first release from becoming an unstable “everything app.”

Do **not** put these into the critical v0.1 path:

- Mandatory accounts
- Real-time collaboration
- Cloud-first storage
- Full handwriting OCR engine
- Semantic/vector search
- Audio recording
- AI assistant
- Advanced equation recognition
- Complex animation-heavy home screens
- Dozens of brushes
- Full desktop client
- Full DOCX/PPTX export
- Custom plugin marketplace
- CRDT synchronization
- Pixel-level eraser if the selected stable ink dependency does not provide a reliable persistence path yet

These are designed-for capabilities, not necessarily v0.1 implementations.

---

# 4. Recommended Technology Stack

## Primary platform

- **Android**
- Kotlin
- Jetpack Compose for application UI
- Native Android View/rendering primitives for the performance-critical ink surface

## Ink

Use **AndroidX Ink 1.0.0 stable** for the v0.1 production path. AndroidX Ink 1.0.0 became stable on December 17, 2025. As of July 29, 2026, the 1.1.0 line is still alpha, so experimental 1.1 APIs must not become hard dependencies of the v0.1 persisted format or core document model. AndroidX Ink provides authoring, rendering, brush, geometry, strokes and storage modules, including low-latency in-progress stroke rendering. [AndroidX Ink release notes](https://developer.android.com/jetpack/androidx/releases/ink)

## UI

- Jetpack Compose
- Material 3 as the system/component foundation
- Custom design system on top
- Adaptive layouts for phones and large screens

As of July 29, 2026, Compose 1.11.4 is the stable line for core Compose artifacts and Material 3 1.4.0 is stable. Pin versions through a central version catalog/BOM strategy rather than scattering versions across modules. [Compose release notes](https://developer.android.com/jetpack/androidx/releases/compose)

## Data

- Room + SQLite for structured metadata/indexes
- SQLite FTS5 for future full-text search
- File-based content store for large/opaque payloads
- DataStore for user preferences/configuration
- Kotlin Serialization for stable document metadata serialization

## Async/concurrency

- Kotlin Coroutines
- Flow / StateFlow
- Structured concurrency

## Background work

- WorkManager for durable background work such as thumbnail regeneration, maintenance, backup/export preparation and later sync.

## Images

- Coil for image loading/caching

## PDF

- AndroidX PDF where applicable, but keep the PDF boundary abstract. Do not allow the PDF implementation to leak throughout the document model.

## Dependency injection

- Hilt is the default choice.
- Keep domain/core modules constructor-injectable and Android-framework-light so Hilt is not embedded into core algorithms.

## Navigation

- AndroidX Navigation / Compose navigation. Keep screen routes typed or centrally defined rather than free-form strings.

## Quality/performance

- JUnit
- AndroidX Test
- Compose UI Test
- Macrobenchmark
- Baseline Profiles
- Strict lint/static analysis
- Detekt and/or equivalent Kotlin static analysis
- Ktlint or equivalent formatting enforcement
- GitHub Actions

## Optional future technologies, not v0.1 dependencies

- TFLite/MediaPipe/ML Kit or dedicated OCR backend
- ONNX Runtime
- WebDAV / S3-compatible object sync
- CRDT library
- Rust native core for selected CPU-heavy algorithms, only after profiling demonstrates a need

---

# 5. Dependency Policy

The project should treat dependencies as part of its architecture.

## Rules

1. Never use an alpha dependency in the hot path merely because it has a desirable feature.
2. Experimental dependencies must be isolated behind an interface/adapter.
3. Every dependency needs an owner module and a reason to exist.
4. Prefer official AndroidX/Kotlin libraries for foundational platform behavior.
5. Do not build from scratch what a stable, maintained library already provides: if a module or library fits the workflow, use it. In-house implementation is justified only for core differentiators (ink pipeline, document format) or when no stable fit exists. The default bias is reuse, not reinvention (complements rule 6, the 100-line floor).
6. Do not add a library for functionality that requires fewer than roughly 100 lines of clear, well-tested code unless the library solves a difficult platform compatibility problem.
7. Pin versions centrally.
8. Use dependency locking/verification where practical.
9. Review release notes before upgrades.
10. Upgrade one foundation family at a time when possible.
11. Every persistence dependency upgrade must run migration and recovery tests.

## Version strategy

Create:

```text
gradle/libs.versions.toml
```

and centralize:

```text
Kotlin
AGP
Compose BOM
AndroidX Ink
Room
Navigation
Lifecycle
Coroutines
Serialization
WorkManager
Coil
Testing libraries
```

Use Renovate or Dependabot only for pull requests, never unattended production upgrades.

---

# 6. Architecture

Use a **layered, modular, unidirectional architecture**.

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

The critical rule is:

> The document/ink model must not know about Compose UI.

This enables future desktop/iOS/KMP work and makes the editor testable without Android UI.

---

# 7. Module Structure

Use a multi-module Gradle project from the beginning.

```text
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

feature/
  feature-home/
  feature-notebook/
  feature-editor/
  feature-settings/

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
platform→ Android-specific APIs
app     → feature modules
```

Never allow:

```text
core-model → Compose
core-model → Activity
core-model → ViewModel
```

---

# 8. Document Model

The document model is the most important long-term compatibility decision.

## Hierarchy

```text
Workspace
  └── Notebook
       └── Section
            └── Document
                 └── Page
                      ├── InkObject
                      ├── TextObject
                      ├── ImageObject
                      ├── ShapeObject
                      └── EmbeddedObject
```

A `Document` may be a multi-page note. A `Page` is the editable page coordinate space.

## Stable IDs

Every persisted entity receives a stable ID, preferably UUID-based or another collision-resistant identifier.

Never use list indexes as persistent identity.

Bad:

```text
pageIndex = 4
```

Good:

```text
pageId = 550e8400-e29b-41d4-a716-446655440000
```

## Object model

Each page object should conceptually contain:

```text
ObjectId
PageId
ObjectType
Transform
Bounds
ZIndex
Visibility
CreatedAt
UpdatedAt
Revision
PayloadRef
```

### Transform

Store transforms explicitly rather than baking movement into raw geometry:

```text
translation
scale
rotation
```

This enables non-destructive manipulation.

---

# 9. Ink Data Model

A stroke is not a bitmap.

Conceptually:

```text
InkObject
 ├── StrokeId
 ├── BrushId
 ├── Color
 ├── Size
 ├── Opacity
 ├── Transform
 └── StrokeInputBatch
       ├── x
       ├── y
       ├── timestamp
       ├── pressure
       ├── tilt
       └── orientation
```

Prefer AndroidX Ink's stroke representation and serialization at the storage boundary, rather than inventing a second incompatible representation without a strong reason.

However, **your own document format must not directly depend on an unstable Ink internal wire representation**.

Use an adapter:

```text
DocumentInk
   ↕
InkAdapter
   ↕
AndroidX Ink
```

This is essential for future dependency upgrades.

---

# 10. Document Format

Introduce a versioned native package format immediately.

Suggested extension:

```text
.penly
```

Example:

```text
note.penly/
├── manifest.json
├── document.json
├── pages/
│   ├── page-01.bin
│   └── page-02.bin
├── assets/
│   ├── image-001.webp
│   └── attachment-001.pdf
└── thumbnails/
    ├── page-01.webp
    └── page-02.webp
```

The physical packaging can later be changed to ZIP/container storage without changing the logical document specification.

## Manifest

Example conceptual fields:

```json
{
  "format": "penly",
  "formatVersion": 1,
  "minimumReaderVersion": 1,
  "documentId": "...",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### Compatibility rules

Every format version must define:

- minimum reader version
- optional writer capabilities
- migration strategy
- unsupported-object behavior
- integrity checking

### Unknown fields

Readers should ignore unknown optional metadata fields.

This enables forward-compatible metadata additions.

### Unknown object types

A newer object type must not corrupt the whole document. The reader should preserve unknown objects as opaque payloads where practical and show a compatibility notice only when editing is impossible.

---

# 11. Database Schema

Room should store **metadata, indexes and transactional state**, not every large binary payload directly.

Suggested tables:

```text
workspaces
notebooks
sections
documents
pages
objects
assets
tags
document_tags
revisions
journal_entries
sync_state
settings_index
```

### Core relations

```text
workspace
  1 ─── N notebook
        1 ─── N section
              1 ─── N document
                    1 ─── N page
                          1 ─── N object
```

## Objects table

Store lightweight metadata:

```text
object_id
page_id
object_type
payload_ref
z_index
min_x
min_y
max_x
max_y
rotation
scale_x
scale_y
created_at
updated_at
revision
```

Bounding boxes are stored to enable future spatial indexing without decoding every stroke.

---

# 12. File Store

Create an application-private content store.

```text
/files/
  /documents/
  /pages/
  /assets/
  /thumbnails/
  /exports/
  /journal/
  /recovery/
```

Never construct file paths directly throughout the codebase.

Use:

```text
ContentStore
```

with operations such as:

```text
put()
open()
move()
delete()
exists()
checksum()
```

This permits future migration to another storage backend.

---

# 13. Crash-Safe Write Pipeline

A handwriting app must assume crashes can happen at any point.

Use a journal/recovery strategy.

```text
Stylus event
    ↓
In-memory editor state
    ↓
Command
    ↓
Journal append
    ↓
Async durable commit
    ↓
Document state
```

The UI must never wait for disk I/O for each input point.

## Save strategy

During a stroke:

- Keep active stroke in memory.
- Render immediately.
- Do not write one database row per point.

On stroke completion:

- Convert to immutable stroke data.
- Append one logical command/journal record.
- Persist asynchronously.
- Update page revision.
- Schedule thumbnail regeneration if necessary.

## Recovery

At startup:

```text
open document
    ↓
check journal
    ↓
replay incomplete operations
    ↓
validate content references
    ↓
repair if possible
    ↓
open document
```

Never delete the journal before the corresponding durable state is known to be valid.

---

# 14. Undo/Redo Architecture

Do not implement undo as “restore a previous bitmap.”

Use commands:

```text
AddObject
DeleteObject
UpdateObject
MoveObjects
TransformObjects
ChangeBrush
InsertImage
InsertText
```

Each command should have:

```text
execute()
undo()
serialize()
```

The history layer should expose:

```text
canUndo
canRedo
undo()
redo()
```

## Memory strategy

Avoid keeping unlimited raw snapshots.

Use command records plus periodic state checkpoints for very large documents.

Future-compatible design:

```text
Commands
   ↓
Checkpoint
   ↓
Commands
   ↓
Checkpoint
```

This later maps naturally to revision history and sync.

---

# 15. Canvas Architecture

The editor is composed of independent subsystems.

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

## Render layers

```text
1. Paper background
2. Document/page content
3. Finished ink
4. Images/shapes/text
5. Active stroke(s)
6. Selection/lasso overlay
7. Tool feedback
8. Temporary system overlays
```

Active strokes should have the lowest latency path.

---

# 16. Input Architecture

Input processing should be explicit and state-machine based.

```text
PointerDown
   ↓
Classify pointer
   ├── Stylus
   ├── Finger
   ├── Mouse
   └── Unknown
   ↓
Resolve current tool
   ↓
Capture input
   ↓
Process movement
   ↓
PointerUp / Cancel
   ↓
Commit command
```

Handle:

- `ACTION_DOWN`
- `ACTION_MOVE`
- `ACTION_UP`
- `ACTION_CANCEL`
- pointer IDs
- tool types
- stylus button state
- pressure
- tilt/orientation when available
- palm cancellation signals

Android's stylus/palm guidance should be treated as required reading during implementation.

---

# 17. Palm Rejection

Implement a layered strategy.

### Layer 1 — tool-aware input classification

Prefer stylus input for drawing.

### Layer 2 — gesture disambiguation

Finger input becomes pan/zoom unless explicitly configured otherwise.

### Layer 3 — cancellation handling

Correctly handle system-reported canceled pointers and palm rejection signals.

### Layer 4 — device-specific testing

Test Samsung S Pen, USI stylus, generic active stylus and devices with poor palm behavior.

Do not claim perfect universal palm rejection.

---

# 18. Brush System

Brushes must be configuration-driven.

```text
BrushDefinition
├── id
├── displayName
├── family
├── sizeRange
├── opacityRange
├── pressureResponse
├── tiltResponse
├── taper
├── smoothing
└── textureConfig
```

v0.1 ships with:

- Pen
- Pencil
- Marker
- Highlighter

Brush IDs must be stable because saved documents refer to them.

If a brush is removed in a future version, its old definition must remain decodable or be migrated to a compatibility brush.

---

# 19. Highlighter Rules

Highlighter must use a consistent compositing strategy and must not create surprising darkness where strokes overlap.

Requirements:

- translucent
- stable appearance while drawing
- stable appearance after reload
- stable appearance at different zoom levels
- no flicker when active/final stroke transitions

A highlighter should be treated as a rendering semantic, not just “a transparent pen.”

---

# 20. Eraser Strategy

v0.1 should ship with:

1. Stroke eraser
2. Optional area/lasso delete through selection

Partial/pixel erasing should only be promoted into v0.1 if the selected persistence path is stable and serializable.

Do not introduce an eraser that visually works but cannot be reliably saved/reloaded.

---

# 21. Lasso Selection

Use AndroidX Ink geometry APIs where appropriate, behind `SelectionGeometry` in your core module.

Pipeline:

```text
lasso input
    ↓
lasso geometry
    ↓
query candidate objects
    ↓
precise intersection test
    ↓
selected object IDs
    ↓
selection bounds
```

Selection must support:

- move
- delete
- copy
- cut
- duplicate
- recolor where valid

Resize/rotate can follow once the geometry and transform system are proven.

---

# 22. Spatial Index

Do not wait for performance problems before adding a spatial abstraction.

Start with a simple uniform grid or equivalent index.

```text
Page
 └── SpatialIndex
      ├── cell 0
      ├── cell 1
      ├── cell 2
      └── ...
```

Objects register their bounds.

Queries:

```text
query(viewport)
query(selectionBounds)
query(lassoBounds)
```

The implementation may change later to an R-tree/quadtree without affecting the editor API.

---

# 23. Viewport System

Use world coordinates.

```text
WorldSpace
   ↓
ViewportTransform
   ↓
ScreenSpace
```

Viewport state:

```text
centerX
centerY
scale
rotation
```

All document objects remain in document/world coordinates.

This prevents zoom from mutating document geometry.

---

# 24. Virtualized Rendering

Rendering should be based on visible bounds.

```text
viewport
   ↓
spatial query
   ↓
visible object list
   ↓
render
```

Do not decode every page object every frame.

Cache immutable render-ready representations.

Invalidate only affected regions where practical.

---

# 25. UI/UX Direction

The UI must feel like a premium native tablet app while remaining recognizably open-source.

## Editor composition

```text
┌─────────────────────────────────────────────┐
│ context bar / notebook                     │
├─────────────────────────────────────────────┤
│                                             │
│                                             │
│                 PAGE                        │
│                                             │
│                                             │
│                                             │
├─────────────────────────────────────────────┤
│ compact floating tool system                │
└─────────────────────────────────────────────┘
```

Avoid permanent giant toolbars consuming writing space.

## Visual principles

- strong typography hierarchy
- generous spacing
- minimal chrome during writing
- tool feedback near the interaction point
- clear selected-state indication
- subtle but intentional motion
- dark/light themes
- dynamic color support where appropriate
- tablet-first layouts
- landscape support
- keyboard/mouse support

“State of the art” means **interaction quality**, not adding decorative animation everywhere.

---

# 26. Design System

Create a real design system before implementing the full app.

Define:

```text
Color tokens
Typography tokens
Spacing scale
Corner radii
Elevation
Motion durations
Motion easing
Icon sizes
Touch targets
Tool states
Selection states
Error states
Empty states
```

The design system should be implemented in `core-ui` or a dedicated `design-system` module, but that module must not leak into document core.

---

# 27. Accessibility

v0.1 must include the architectural hooks for:

- TalkBack
- content descriptions
- keyboard navigation
- large font support
- high contrast
- touch target compliance
- external keyboard
- mouse

Do not hide semantic controls inside a custom canvas if they need to be accessible.

The canvas itself should expose meaningful actions where feasible, such as:

```text
Undo
Redo
Delete selection
Duplicate selection
```

---

# 28. Home / Library Experience

v0.1 screens:

```text
Library
 ├── All notebooks
 ├── Recent
 ├── Favorites
 └── Trash
```

Notebook browser should use lazy/paged loading.

Every document should have a thumbnail.

Do not load full document content just to display the library.

---

# 29. Page Templates

Ship:

- Blank
- Ruled
- Narrow ruled
- Grid
- Dots
- Cornell-style basic template

Templates should be data/config driven rather than hard-coded drawing code.

Future templates can add:

- music notation
- engineering graph
- mathematics
- isometric
- planner

---

# 30. Text and Image Objects

v0.1 should support basic non-ink objects because the document model should be hybrid from the beginning.

### Text object

- editable text
- basic font size/style
- selection
- move/resize

### Image object

- import from system picker
- scale
- move
- delete
- cached thumbnail

Do not build a complete word processor.

The goal is hybrid note composition, not document editing competition with a full word processor.

---

# 31. PDF Boundary

PDF support should be designed but not allowed to dominate the core architecture.

Create:

```text
PdfDocumentSource
PdfPageRenderer
PdfAnnotationLayer
PdfExporter
```

The application document stores the relationship to the PDF rather than converting the entire PDF into page bitmaps by default.

When PDF is implemented, preserve:

```text
original PDF
+
annotation objects
```

rather than destructive rasterization.

AndroidX PDF/Ink can be evaluated for implementation, but wrap it behind an internal interface so future PDF implementations remain possible.

---

# 32. Search Architecture Preparation

Do not implement semantic search in v0.1.

Do implement the boundary:

```text
SearchIndex
SearchQuery
SearchResult
IndexableObject
```

Prepare database fields for future:

```text
normalized_text
ocr_text
language
searchable
```

Use SQLite FTS5 later for text and OCR.

The search API should not care whether the final implementation is FTS, OCR, embeddings or a combination.

---

# 33. Sync Architecture Preparation

No cloud sync in the v0.1 critical path.

But every document-changing operation should be identifiable.

```text
OperationId
DocumentId
ObjectId
Revision
Timestamp
ActorId (future)
OperationType
Payload
```

This provides a future bridge to:

- revision history
- backups
- sync
- conflict resolution
- collaboration

Do not build a CRDT yet.

---

# 34. AI Architecture Preparation

AI should be an optional capability provider.

Define:

```text
AiProvider
  ├── summarize()
  ├── explain()
  ├── recognizeHandwriting()
  ├── solveEquation()
  └── generateStudyMaterial()
```

The editor must never depend on a specific cloud vendor.

Potential providers later:

- local model
- user-provided API key
- OpenRouter-like gateway
- hosted application backend

No AI code in the critical editor rendering path.

---

# 35. Error Handling

Use typed domain errors rather than generic exceptions everywhere.

Example categories:

```text
StorageError
DocumentCorruptError
UnsupportedFormatError
PermissionError
ExportError
RenderingError
MigrationError
```

Display friendly UI messages, but log diagnostic details.

Never expose stack traces to normal users.

---

# 36. Logging and Diagnostics

Create a central logger.

Levels:

```text
TRACE
DEBUG
INFO
WARN
ERROR
```

Debug builds may expose verbose logs.

Release builds should redact personal/document contents.

Never log:

- handwriting payloads
- note text
- file contents
- access tokens
- credentials

unless the user explicitly exports diagnostic data and the export is clearly described.

---

# 37. Telemetry Philosophy

The default app should be privacy-first.

No mandatory analytics service.

Optional anonymous crash reporting can be considered later, but it must be:

- opt-in or clearly disclosed
- privacy-preserving
- disabled for note contents
- removable
- open about what is collected

The application must be fully usable without telemetry.

---

# 38. Performance Budget

Set targets before implementation.

These are engineering targets, not platform guarantees.

| Area | Target |
|---|---:|
| Active stroke visual latency | ideally < 20 ms |
| Frame pacing | 60 FPS baseline |
| High-refresh devices | 90/120 FPS where device permits |
| UI jank during writing | near zero in normal pages |
| Page open | < 300 ms target for cached ordinary pages |
| Undo | < 50 ms target |
| Redo | < 50 ms target |
| Autosave | no visible blocking |
| Library scrolling | 60 FPS baseline |
| Large notebook | usable at hundreds of pages |
| Memory | bounded; no full-workspace loading |

Benchmark with both synthetic and real handwritten data.

---

# 39. Performance Test Corpus

Create deterministic datasets.

```text
Dataset A: 100 strokes
Dataset B: 1,000 strokes
Dataset C: 10,000 strokes
Dataset D: 50,000 strokes
Dataset E: mixed ink + images + text
Dataset F: 500-page notebook
Dataset G: PDF + annotations
```

Measure:

- first open
- cached open
- pan
- zoom
- drawing
- selection
- save
- thumbnail generation
- memory
- GC pressure
- battery impact

Include real exported stroke samples from multiple devices where licensing/privacy permits.

---

# 40. Benchmark Strategy

Use AndroidX Macrobenchmark for:

- cold start
- home-to-editor
- document open
- page navigation
- library scroll
- export startup

Use dedicated instrumentation for:

- stroke processing time
- renderer frame timing
- journal append time
- page load time
- object query time

Use Android Studio profiling to investigate:

- allocations during active strokes
- main-thread work
- GPU frame cost
- bitmap memory
- cache behavior

Baseline Profiles should be introduced once critical startup/navigation traces are stable.

---

# 41. Threading Model

Keep the main thread for interactive UI and minimum orchestration only.

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

Never perform full serialization, PDF conversion or database vacuuming on the main thread.

Active stroke input must avoid unnecessary coroutine dispatching per pointer sample; prefer efficient batching and dedicated render/input APIs.

---

# 42. Memory Management

Rules:

1. Never keep every page decoded in memory.
2. Cache by size and viewport requirements.
3. Evict thumbnails aggressively.
4. Keep immutable stroke data shared where possible.
5. Avoid copying large stroke lists for every edit.
6. Use immutable snapshots carefully, not blindly.
7. Profile allocations during continuous writing.

Potential future optimization:

- persistent page caches
- compressed stroke pages
- tiled rendering
- native memory for extremely large geometry datasets

Do not prematurely introduce Rust/C++.

---

# 43. State Management

Use unidirectional flow.

```text
User action
   ↓
Intent
   ↓
ViewModel / editor coordinator
   ↓
Use case
   ↓
Domain operation
   ↓
Repository
   ↓
State update
   ↓
UI
```

The high-frequency active-stroke path is the exception: it should use a specialized low-latency pipeline rather than forcing every pointer sample through general application state.

---

# 44. Save Semantics

The user should never wonder whether a note is saved.

UI state may show:

```text
Saved
Saving…
Recovered
```

But the default should be silent autosave.

A completed stroke should become durable asynchronously.

On app termination/backgrounding:

- flush pending durable work
- persist editor checkpoint when necessary
- maintain recovery data if a clean flush is impossible

---

# 45. Export Strategy

v0.1 should have:

- PDF export
- PNG export for selected page
- native document export

Export must run as a background operation.

Use a temporary output file followed by an atomic rename when possible.

Never overwrite the only copy of a user file directly during export.

---

# 46. File Integrity

Large document payloads should have integrity metadata.

Use:

```text
size
checksum
formatVersion
```

The goal is detection of:

- interrupted writes
- missing files
- corrupted assets
- incompatible payloads

Integrity checks should be cheap enough for routine validation and deeper checks should run in maintenance tasks.

---

# 47. Migration Strategy

Every persisted schema/document version gets:

```text
migration N → N+1
```

Never alter existing stored meaning silently.

Maintain test fixtures for:

```text
v1 document
v2 document
...
```

Test:

```text
old document
 ↓
migrate
 ↓
open
 ↓
edit
 ↓
save
 ↓
reopen
```

The application should define a policy for documents newer than the current reader version:

> Preserve the original file, open read-only/limited mode where possible, and never silently overwrite unsupported content.

---

# 48. Upgrade Compatibility

Dependency upgrades must not define the app's public persisted behavior.

Create adapters around:

- AndroidX Ink
- PDF engine
- image decoding
- storage implementation

If AndroidX Ink changes in a later release:

```text
InkAdapter v1
     ↓
Ink API current
```

can be upgraded independently of the document schema.

This is a key reason not to leak `androidx.ink.*` types into every business-layer class.

---

# 49. Testing Pyramid

## Unit tests

Heavy coverage for:

- document model
- transformations
- geometry
- history
- serialization
- migration
- file store
- repository logic
- spatial index

## Integration tests

- Room + file store
- journal replay
- document save/load
- migration
- export

## UI tests

- create notebook
- create page
- change tool
- write/erase
- select/move
- save/reopen
- settings

## Device tests

Real stylus devices for:

- pressure
- palm rejection
- latency
- rotation
- background/resume
- large-screen layouts

---

# 50. Property-Based Testing Candidates

Strong candidates:

### Transform invariants

```text
apply(transform, point)
then inverse(transform)
≈ original point
```

### Serialization round-trip

```text
object
 → serialize
 → deserialize
≈ original object
```

### Undo/redo

```text
state
 → operations
 → undo all
≈ initial state
```

### Migration

```text
vN
 → migrate
 → serialize
 → deserialize
```

### Spatial index

Indexed query results should match brute-force query results for the same dataset.

---

# 51. Fuzz Testing

Fuzz:

- malformed document manifests
- corrupted stroke payloads
- truncated files
- invalid transforms
- unsupported brush IDs
- extreme coordinates
- NaN/infinite numeric values
- huge object counts
- malformed image/PDF assets

The app must fail gracefully instead of crashing the whole editor.

---

# 52. Security

Even an offline note app needs basic security discipline.

- Keep app-private storage private by default.
- Use Android's Storage Access Framework for user-selected external files.
- Do not request broad storage permissions unnecessarily.
- Treat imported documents as untrusted input.
- Validate all serialized data.
- Avoid path traversal from archive/package imports.
- Consider size limits before decoding imported content.
- Do not execute arbitrary embedded content.
- Protect future sync credentials with Android Keystore-backed storage.

---

# 53. Open-Source Repository Structure

```text
.github/
  workflows/
  ISSUE_TEMPLATE/
  pull_request_template.md

app/
core/
feature/
editor/
platform/
testing/

benchmarks/

docs/
  architecture/
  document-format/
  contributing/
  privacy/
  releases/

samples/
  ink-lab/

scripts/

LICENSE
README.md
CONTRIBUTING.md
CODE_OF_CONDUCT.md
SECURITY.md
CHANGELOG.md
```

---

# 54. Ink Lab Sample

Before building the full app, create a separate sample/module:

```text
samples/ink-lab
```

It should provide:

- pen
- pencil
- highlighter
- eraser
- pressure visualization
- pointer type visualization
- tilt visualization
- latency/debug overlay
- FPS/frame timing
- stroke count
- memory estimate

This becomes the regression playground for the ink engine.

---

# 55. Development Phases

## Phase 0 — Repository foundation

### Tasks

- initialize Git repository
- configure Gradle Kotlin DSL
- create version catalog
- configure build variants
- add lint/static analysis
- add formatting
- establish module graph
- configure CI
- add basic design tokens
- add test infrastructure

### Exit criteria

```text
./gradlew check
```

passes on a clean machine.

---

## Phase 1 — Ink Lab

### Tasks

- integrate stable AndroidX Ink 1.0.x
- implement stylus input
- implement pen/pencil/marker/highlighter
- implement eraser
- pressure
- pointer classification
- palm cancellation handling
- low-latency active stroke renderer
- zoom/pan
- performance overlay

### Exit criteria

The Ink Lab feels responsive on at least three representative devices.

No serious allocations or blocking storage work occur in the active stroke loop.

---

## Phase 2 — Document Core

### Tasks

- document IDs
- page IDs
- object IDs
- object model
- transforms
- revision numbers
- serialization
- native `.penly` logical format
- file store
- Room metadata

### Exit criteria

A handwritten page can be saved, closed, reopened and reproduced without meaningful visual drift.

---

## Phase 3 — Editor Core

### Tasks

- viewport
- page renderer
- tool controller
- command history
- selection
- lasso
- move/copy/delete
- image object
- basic text object

### Exit criteria

A real note can be created and edited without touching internal/debug APIs.

---

## Phase 4 — Crash Safety

### Tasks

- journal
- autosave
- startup replay
- atomic writes
- integrity metadata
- corrupted asset detection
- recovery UI

### Exit criteria

Automated fault injection demonstrates that common crashes/power-loss simulations do not lose committed content.

---

## Phase 5 — Library/UI

### Tasks

- home/library
- notebook browser
- sections
- recent documents
- thumbnails
- page creation
- templates
- empty states
- settings
- adaptive tablet UI

### Exit criteria

The app is visually coherent, keyboard/mouse usable and tablet layouts are polished.

---

## Phase 6 — Export

### Tasks

- PDF rendering/export
- PNG export
- native file export
- share integration
- export progress/errors

### Exit criteria

Exports are deterministic, non-destructive and do not block the editor.

---

## Phase 7 — Hardening

### Tasks

- benchmark suite
- macrobenchmarks
- memory profiling
- large notebook stress tests
- migration tests
- fuzz tests
- accessibility audit
- device compatibility matrix
- release signing
- release documentation

### Exit criteria

No known P0/P1 data-loss defects.

No critical performance regression against defined budgets.

---

## Phase 8 — PDF Annotation

### Tasks

- PDF import
- annotation layer (ink/shapes/text over PDF)
- annotation object model (non-destructive: original bytes + annotation objects)
- merged PDF export
- page-to-page annotation mapping

### Exit criteria

A 100-page PDF can be annotated, reloaded, and exported with annotations
losslessly; the original PDF bytes are unchanged.

---

## Phase 9 — Search + On-Device OCR

### Tasks

- FTS5 full-text index
- handwriting OCR provider (on-device)
- image OCR
- search UI + indexed highlights
- ink-to-text conversion for selected handwriting

### Exit criteria

"Search your handwriting" finds strokes across notebooks on three representative
devices, fully offline.

---

## Phase 10 — Kotlin Multiplatform Core + Desktop

### Tasks

- split every platform-free `core:*` module into KMP (`commonMain` /
  `androidMain` / `desktopMain`)
- Compose Multiplatform app shell
- Skiko/Skia ink renderer and authoring backend (mouse + pen-tablet pressure)
- desktop input (keyboard, mouse, pen)
- multi-window, system tray, global shortcuts
- native file dialogs, drag-drop import

### Exit criteria

The same document opens identically on Android and a desktop OS; ink authored
on one platform renders and edits correctly on the other.

---

## Phase 11 — Knowledge Layer

### Tasks

- rich typed blocks (headings, lists, checkboxes, code, tables, callouts)
- Markdown + wiki-links + backlinks
- tags + tag manager
- graph view (local + global)
- outline, daily notes, journal
- templates system
- quick switcher (⌘K)

### Exit criteria

Obsidian-style knowledge-management flows work on both Android and desktop.

---

## Phase 12 — Browser + Audio

### Tasks

- share-intent web clipper
- reader-mode extraction (HTML → Markdown / reader snapshot)
- live web embeds (WebView on Android, embedded Chromium on desktop, external
  browser fallback)
- link previews
- audio recording linked to writing position

### Exit criteria

A web page can be clipped and reopened as a note; a lecture can be recorded
while writing with position-linked playback.

---

## Phase 13 — Plugins

### Tasks

- QuickJS sandboxed runtime
- `penly-sdk` v1 JS API contract (versioned, deterministic)
- capability model (notes read/write, commands, custom views, network, AI)
- signed plugin bundles + verification
- plugin lifecycle (install/update/disable/uninstall)
- compatibility test suite

### Exit criteria

A third-party plugin installs, runs sandboxed, and uninstalls cleanly; a plugin
written against `penly-sdk` v1 passes the compatibility suite on Android and
desktop.

---

## Phase 14 — Sync

### Tasks

- operation log as the sync seam
- WebDAV transport (universal self-host)
- S3-compatible transport
- E2EE (XChaCha20-Poly1305, Argon2id-derived keys, encrypted metadata)
- conflict copies + resolution UI
- background sync (WorkManager / desktop service)

### Exit criteria

Two devices converge without data loss; a kill-one-device fault-injection test
shows no committed content lost; a passphrase-protected vault cannot be read
server-side.

---

## Phase 15 — AI + Agents

### Tasks

- on-device providers: embeddings, handwriting recognition, OCR, small LLM
  (ONNX/NNAPI, opt-in downloads)
- BYOK provider (OpenRouter gateway)
- assistant panel in the editor
- agent framework: tool-calling agents with note tools (search, read,
  create/update, selection-transform, browse URL)
- plugin-registered agent tools
- semantic search (on-device embeddings + hybrid FTS5)

### Exit criteria

An agent can find, summarize, and edit notes from a natural-language command
on-device and via a user-provided cloud key; no AI feature requires an account.

---

## Phase 16 — Transcription

### Tasks

- audio → text transcription
- position-linked transcripts
- transcript search

### Exit criteria

A recorded lecture is transcribed, position-linked, and searchable.

---

## Phase 17 — Collaboration

### Tasks

- CRDT document sync
- comments and mentions
- share links

### Exit criteria

Two users edit the same page live without loss or visible conflict artifacts.

---

# 56. Issue Priority System

Use:

```text
P0 — data loss / corruption / unrecoverable crash
P1 — core writing experience broken
P2 — major feature broken / severe performance issue
P3 — normal bug
P4 — polish / enhancement
```

A P0 or P1 should block a stable release.

---

# 57. Definition of “State of the Art” for This App

Do not define it by feature count.

The editor is state of the art when:

- the pen tracks naturally
- pressure behaves predictably
- palm rejection is reliable on tested devices
- tools change instantly
- zooming/panning are fluid
- pages open quickly
- large documents remain responsive
- autosave is invisible
- the app survives bad conditions
- the interface gets out of the way
- the page feels spatially stable

The goal is **premium interaction quality**, not visual excess.

---

# 58. Release Quality Gates

Before tagging `0.1.0-alpha.1`:

### Data integrity

- [ ] crash recovery tested
- [ ] migration tested
- [ ] malformed input tested
- [ ] save/load round-trip tested
- [ ] export tested

### Ink

- [ ] stylus input
- [ ] pressure
- [ ] pen
- [ ] pencil
- [ ] marker
- [ ] highlighter
- [ ] eraser
- [ ] palm handling
- [ ] undo/redo
- [ ] selection

### Performance

- [ ] no known active-stroke jank
- [ ] large-page stress test
- [ ] 500-page notebook test
- [ ] memory profiling
- [ ] startup macrobenchmark
- [ ] baseline profile test

### UI

- [ ] phone portrait
- [ ] phone landscape
- [ ] tablet portrait
- [ ] tablet landscape
- [ ] dark mode
- [ ] large text
- [ ] keyboard/mouse
- [ ] accessibility pass

### Engineering

- [ ] CI green
- [ ] dependency lock/update policy documented
- [ ] reproducible debug/release builds
- [ ] documentation updated
- [ ] changelog updated
- [ ] backup/export documented

---

# 59. Recommended v0.1 User Journey

The first-run journey should be extremely short.

```text
Install
  ↓
Open
  ↓
New notebook
  ↓
New page
  ↓
Write
```

Do not require:

```text
account
email
cloud setup
permissions unrelated to the immediate action
```

The user should encounter the pen experience within seconds.

---

# 60. Future Roadmap Compatibility

v0.1 must leave clear seams for the complete-product roadmap. The authoritative
master roadmap (Phases 8–17: PDF annotation, search/OCR, KMP + desktop,
knowledge layer, browser/audio, plugins, sync, AI/agents, collaboration) is
defined in [Part II — The Master Plan](#part-ii--the-master-plan).

The exact versioning can change. The important part is that none of these
should require replacing the foundational document model.

---

# 61. Architecture Decision Records

Start an ADR folder:

```text
docs/architecture/adr/
```

Every major choice gets an ADR.

Initial ADRs:

```text
ADR-001 Kotlin + Android native
ADR-002 AndroidX Ink as ink foundation
ADR-003 Compose + native performance canvas
ADR-004 Local-first storage
ADR-005 Room + file store split
ADR-006 Versioned native document format
ADR-007 Command-based undo/redo
ADR-008 Journal/recovery strategy
ADR-009 Stable IDs for persisted entities
ADR-010 Modular architecture
ADR-011 No mandatory cloud account
ADR-012 Experimental dependency isolation
ADR-013 Kotlin Multiplatform core (Android + Desktop)
ADR-014 Sandboxed QuickJS plugin runtime
ADR-015 Sync protocol + E2EE
ADR-016 AI provider abstraction (on-device + BYOK)
ADR-017 Desktop ink via Skiko
ADR-018 Plugin security/capability model
ADR-019 Feature-parity matrix
```

This prevents future contributors from accidentally undoing important architectural decisions.

---

# 62. Documentation Requirements

Write documentation as the system is built, not after it.

Required docs:

```text
Architecture overview
Document format specification
Ink pipeline
Storage/recovery model
Editor state model
Performance guide
Testing guide
Contribution guide
Release process
Migration policy
Privacy model
```

---

# 63. Git Workflow

Use short-lived feature branches.

Recommended commits:

```text
feat(ink): add stylus authoring pipeline
feat(editor): add lasso selection
fix(storage): recover interrupted page writes
perf(renderer): cache immutable stroke meshes
test(document): add format round-trip fixtures
```

Avoid giant commits containing unrelated UI + storage + renderer changes.

---

# 64. CI Pipeline

Every pull request:

```text
format check
lint
unit tests
serialization tests
migration tests
build debug APK
```

Nightly:

```text
instrumentation tests
macrobenchmarks
large document tests
fuzz corpus
```

Release candidate:

```text
full test matrix
release build
signing validation
installation/update test
backup restore test
```

---

# 65. Update and Migration Testing

For every app release that changes persistence:

```text
Install old APK
Create document
Populate content
Upgrade APK
Open document
Edit document
Save
Restart
Reopen
Export
```

This should become an automated upgrade test where possible.

Never assume an Android app's next release will run with a completely fresh database.

---

# 66. Failure Scenarios to Test Explicitly

- app killed during stroke
- app killed during save
- device restarted immediately after writing
- storage nearly full
- storage completely full
- malformed native document
- missing asset
- corrupt asset
- old document version
- future/unknown document version
- screen rotated during writing
- app backgrounded during writing
- process recreated
- low-memory process kill
- very long stroke
- extremely zoomed page
- extremely small zoom
- 50k+ stroke page
- 500-page notebook
- rapid undo/redo
- repeated imports/exports

---

# 67. Performance Anti-Patterns to Ban

Do not allow the following into production code without a measured justification:

- database writes on every pointer sample
- bitmap screenshot as the canonical page state
- loading every notebook page at launch
- serializing the entire notebook for every stroke
- creating thousands of Compose nodes for every stroke
- allocating a new list/object for every pointer event unnecessarily
- performing PDF/image decoding on the main thread
- synchronous file I/O during active writing
- global singleton editor state
- storing UI state as the document source of truth
- exposing AndroidX Ink classes through every domain interface

---

# 68. Architecture Boundary Summary

```text
                 ┌─────────────────────┐
                 │       Compose       │
                 │     UI / Shell      │
                 └──────────┬──────────┘
                            │
                 ┌──────────▼──────────┐
                 │   Editor Coordinator │
                 └──────────┬──────────┘
                            │
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

No downward layer may import a higher-level feature package.

---

# 69. First Implementation Order

The team should implement in exactly this dependency order unless an ADR changes it:

```text
1. Repository + CI
2. Version catalog + dependency policy
3. Core model
4. Ink Lab
5. Ink adapter
6. Document serialization
7. File store
8. Room metadata
9. Journal/recovery
10. Renderer
11. Editor surface
12. Commands/undo
13. Selection
14. Library
15. Templates
16. Image/text objects
17. Export
18. Performance hardening
19. Accessibility hardening
20. Release candidate
```

Do not reverse this and build the library/home experience first.

---

# 70. Suggested Initial Repository Milestones

```text
M0 — build foundation
M1 — Ink Lab usable
M2 — single persistent page
M3 — multi-page document
M4 — production editor
M5 — crash-safe storage
M6 — notebook library
M7 — export
M8 — performance hardening
M9 — release candidate
M10 — v0.1.0-alpha.1
```

Each milestone should be independently demonstrable.

---

# 71. Final Engineering Principle

The project should be built as if the v0.1 document created today may still be opened five years later.

That means:

```text
stable IDs
+
versioned format
+
explicit migrations
+
lossless content
+
transactional persistence
+
adapter boundaries
+
benchmarking
+
real-device testing
```

The app can become dramatically more sophisticated later without forcing users to migrate to a completely different internal representation.

---

# 72. Immediate Next Action

Before writing feature code, implement the following minimal vertical slice:

```text
Open app
  ↓
Create notebook
  ↓
Create page
  ↓
Stylus writes with pressure
  ↓
Stroke appears through low-latency path
  ↓
Stroke becomes immutable document object
  ↓
Command recorded
  ↓
Journal persisted
  ↓
Page saved
  ↓
App killed
  ↓
App reopened
  ↓
Page recovered exactly
```

Only after this loop is reliable should the project scale outward into library UI, advanced tools and secondary features.

---

# References / Current Platform Notes

The dependency recommendations in this document are based on the current Android/Jetpack release state checked on **August 14, 2026**.

- AndroidX Ink release notes: https://developer.android.com/jetpack/androidx/releases/ink
- Jetpack Compose release notes: https://developer.android.com/jetpack/androidx/releases/compose
- Jetpack stable release channel: https://developer.android.com/jetpack/androidx/versions/stable-channel
- Android advanced stylus support: https://developer.android.com/codelabs/large-screens/advanced-stylus-support
- Android stylus/palm rejection guidance: https://developer.android.com/develop/adaptive-apps/cookbook/stylus-palm-rejection
- AndroidX PDF: https://developer.android.com/develop/ui/compose/touch-input/stylus-input
- AndroidX Room: https://developer.android.com/jetpack/androidx/releases/room
- Android Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview

## Current versioning note

As of this plan's review date:

- AndroidX Ink `1.0.0` is stable; `1.1.0` is still alpha.
- Compose core artifacts are on the `1.11.4` stable line and Material 3 `1.4.0` is stable.

Do not hard-code these versions throughout the repository. Use the version catalog and update them through reviewed dependency upgrade PRs.

---

# v0.1 success definition

The project succeeds at v0.1 if a user can open it on a good Android tablet, create a page, write naturally for several minutes, edit the handwriting, close the application, reopen it later, and find the exact page intact — while the application remains visually polished, responsive and reliable.

Everything else is built on top of that foundation.

---

# Part II — The Master Plan

> Status: approved August 2026. Extends the v0.1 plan with the complete-product
> roadmap: cross-platform desktop, plugins, sync, AI/agents, and browser
> features. The v0.1 plan remains authoritative for Phases 0–7; Part II governs
> everything after v1.0. Part II's decisions are recorded in ADR-013 through
> ADR-019.

# 73. Master Plan — Product Vision

The master thesis builds on, but does not replace, the v0.1 thesis:

> Penly is the **open note platform** — handwriting-first, local-first, with a
> plugin ecosystem, native AI, and self-hostable sync — where notes remain the
> user's forever: open format, no lock-in, offline-capable, and running on
> desktop and Android.

The v0.1 promise ("open a page, write naturally with no friction") stays the
product's center. Everything in Part II must protect that experience, in
accordance with principles 11–16 (§1).

Decisions locked with the user (August 2026):

```text
platform    Android + Desktop (Kotlin Multiplatform core, Compose Multiplatform)
plugins     sandboxed JS runtime (QuickJS), penly-sdk, signed marketplace later
AI/agents   hybrid: on-device first (ONNX/NNAPI), BYOK cloud (OpenRouter),
            provider-abstracted, never required
browser     web clipper + in-app reader + live embeds + link previews (no full
            browser engine)
sync        self-hostable first (WebDAV/S3) with E2EE; official cloud optional
priority    handwriting tier first, knowledge-management tier second
```

---

# 74. Feature Matrix

The complete-product benchmark, detailed in
[`docs/features/feature-matrix.md`](features/feature-matrix.md):

```text
Tier A — Handwriting (GoodNotes/Samsung Notes/OneNote parity)
  PDF import + non-destructive annotation + merged export
  handwriting search (on-device OCR)
  ink-to-text conversion
  shape recognition
  audio recording linked to writing position
  three erasers (stroke, pixel, lasso)
  zoom magnifier + optional infinite canvas
  paper templates (ruled/grid/dots/music/engineering/planner)
  palm rejection + stylus button tools

Tier B — Knowledge (Obsidian/Notion/Logseq parity)
  rich typed blocks (headings, lists, checkboxes, code, tables, callouts)
  Markdown + wiki-links + backlinks + tags + outline
  graph view (local + global)
  daily notes/journal + templates
  quick switcher (⌘K) + full-text search
  attachments + file management
  databases/collections (later)

Tier C — Platform (differentiators)
  desktop + Android (KMP core, Compose Multiplatform)
  plugins: sandboxed JS runtime, SDK, capability model, signed marketplace
  AI: on-device OCR/embeddings/handwriting recognition; BYOK assistant;
      tool-calling agents over notes
  browser: web clipper + reader + live embeds + link previews
  sync: self-host (WebDAV/S3), E2EE, optional official cloud
  audio notes with transcription
  version history/snapshots; backup/restore

Tier D — Future (v5+)
  real-time collaboration (CRDT), comments/mentions, share links
```

Anti-goals (unchanged from §3, plus): not a full word processor, not a full
browser engine, no mandatory account/telemetry, no cloud-first storage.

---

# 75. Master-Plan Architecture Strategy

## 75.1 Kotlin Multiplatform core (Android + Desktop)

- Every platform-free `core:*` module becomes KMP: `commonMain` +
  `androidMain` + `desktopMain` (core-model, core-geometry, core-document,
  core-ink, core-search, core-export, core-sync, core-ai, core-plugin,
  core-crypto).
- The existing "core never imports Compose/Activity/ViewModel" rule (§6) is the
  precondition that makes this cheap — it is already satisfied.
- AndroidX Ink remains the Android ink engine behind `InkAdapter`. Desktop ink
  uses a Skiko/Skia backend (render + author with mouse and pen-tablet
  pressure). The persisted stroke format is platform-neutral and unchanged.
- Desktop shell: Compose Multiplatform — multi-window, system tray, global
  shortcuts, native dialogs, drag-drop import.

## 75.2 Plugin runtime — sandboxed QuickJS

- `core-plugin`: manifest, versioned `penly-sdk` JS API, QuickJS host,
  capability model, async JSON bridge, event hooks.
- Security: no direct filesystem/network access without capability, memory and
  execution time limits, signed bundles, deterministic API versioning.
- Marketplace (curated index → signed community registry) is Phase 13+, not
  v1.0.

## 75.3 AI + agents — hybrid, provider-abstracted

- `core-ai` exposes `AiProvider` (already planned in §34): recognizeHandwriting,
  embed, chat, summarize, transcribe, solveEquation, extract.
- Local providers (`platform-llm`, ONNX/NNAPI) with opt-in model downloads and
  size budgets; BYOK provider via OpenRouter gateway. No AI feature requires an
  account.
- `core-agent`: tool-calling agents with note tools (search, read,
  create/update, selection-transform, record/transcribe, browse URL), callable
  from assistant panel, selection menus, and slash commands; plugins can
  register tools.
- Semantic search: on-device embeddings, hybrid with FTS5.

## 75.4 Browser — clipper + reader + embeds

- `core-browser`: share-intent web clipper (reader-mode extraction to
  Markdown/reader snapshot), live in-page embeds (WebView on Android, embedded
  Chromium on desktop, external-browser fallback), link previews.

## 75.5 Sync — self-host first, E2EE

- The operation log (§33) is the sync seam. `core-sync` transports: WebDAV
  first (universal self-host), S3-compatible second, official server third.
- E2EE: client-side XChaCha20-Poly1305, Argon2id-derived keys, encrypted blobs
  and metadata.
- Conflicts: last-writer-wins + explicit conflict copies first; CRDT for ink
  strokes in the collaboration phase.

## 75.6 Module graph additions

```text
core:      +core-ai +core-agent +core-sync +core-plugin +core-crypto
           +core-browser +core-audio +core-knowledge
platform:  +platform-desktop +platform-llm +platform-plugin-host
           +platform-crypto
feature:   +feature-knowledge +feature-ai +feature-sync +feature-plugins
           +feature-browser
```

Existing empty scaffolds (`core-search`, `core-pdf`, `core-export`,
`core-settings`, `core-telemetry`, `feature-home`, `feature-notebook`,
`feature-settings`) receive their first real code in the phases below.

---

# 76. Master Roadmap (Phases 8–17)

Phases 0–7 (defined in §55) are unchanged and must complete before Phase 8
starts. Each phase is independently shippable and ends with `./gradlew check`
green.

```text
Phase 8   PDF annotation                  → v1.1
Phase 9   Search + on-device OCR          → v1.2
Phase 10  Kotlin Multiplatform + Desktop  → v2.0
Phase 11  Knowledge layer                 → v2.1
Phase 12  Browser + audio                 → v2.2
Phase 13  Plugins (QuickJS + penly-sdk)   → v3.0
Phase 14  Sync (WebDAV/S3, E2EE)          → v3.1
Phase 15  AI + agents                     → v4.0
Phase 16  Transcription                   → v4.1
Phase 17  Collaboration (CRDT)            → v5.0
```

Ordering rationale: v1.0 ships before any expansion (§3's "everything app"
trap). Plugins precede sync because the ecosystem drives adoption (Obsidian's
lesson); sync precedes AI because multi-device trust comes first. The operation
log keeps sync/collaboration/version history future-proof without building them
early. Full per-phase task lists and exit criteria are in §55 (Phases 8–17).

---

# 77. Master-Plan Risks

```text
risk                                    mitigation
AndroidX Ink is Android-only            InkAdapter boundary; Skiko desktop
                                        backend shares the same format (Ph. 10)
KMP migration cost                      core is already Compose-free; migrate
                                        module-by-module, Android green always
QuickJS sandbox escape                  capability model, no raw FS/net,
                                        signed bundles, fuzzed bridge,
                                        time/memory limits
on-device AI model size                 opt-in downloads, size budgets,
                                        hardware acceleration, BYOK fallback
solo-dev pace on a weak machine         vertical-slice phases with CI gates;
                                        nightly emulator + KVM working
sync data-loss (worst case)             E2EE + operation log + conflict copies
                                        + fault-injection tests before release
plugin API breakage kills ecosystem     penly-sdk v1 locked in Phase 13 with a
                                        compatibility test suite
```

---

# 78. Master-Plan Documentation

Docs required by the master plan (beyond §62):

```text
features/feature-matrix.md    — Tier A–D parity benchmark (written, Phase 0)
architecture/adr/ADR-013..019 — decisions locked in Part II (written)
plugin-sdk.md                 — penly-sdk v1 API contract (with Phase 13)
sync-protocol.md              — transports, E2EE, conflict policy (with Phase 14)
ai-providers.md               — provider interfaces, local + BYOK (with Phase 15)
```

