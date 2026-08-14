# Editor State Model

How the editor coordinates tools, history, selection, and the viewport without letting UI concerns leak into the document model.

Source of truth: [plan.md §14, §21–24, §43](../plan.md#14-undo-redo-architecture).

---

## 1. Editor coordinator

The editor is coordinated by an `Editor Coordinator` (ViewModel-level) that sits between Compose UI and the editor subsystems ([plan §68](../plan.md#68-architecture-boundary-summary)). It owns the tool state, the command history, and the mapping from user intents to domain operations.

Normal operations follow the unidirectional flow ([plan §43](../plan.md#43-state-management)):

```text
User action → Intent → Editor coordinator → Use case
   → Domain operation → Repository → State update → UI
```

The **high-frequency active-stroke path is the exception**: it uses a specialized low-latency pipeline (see [ink-pipeline.md](ink-pipeline.md)) rather than forcing every pointer sample through general application state.

## 2. Command-based undo/redo

Undo is **never** "restore a previous bitmap" ([plan §14](../plan.md#14-undo-redo-architecture)).

Commands:

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

Each command has:

```text
execute()
undo()
serialize()
```

The history layer exposes:

```text
canUndo
canRedo
undo()
redo()
```

### Memory strategy

Avoid keeping unlimited raw snapshots. Use command records plus periodic state checkpoints for very large documents:

```text
Commands → Checkpoint → Commands → Checkpoint
```

This later maps naturally to revision history and sync (see [plan §33](../plan.md#33-sync-architecture-preparation)).

## 3. Viewport system

Use world coordinates ([plan §23](../plan.md#23-viewport-system)):

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

All document objects remain in document/world coordinates. **Zoom never mutates document geometry.**

## 4. Virtualized rendering

Rendering is based on visible bounds ([plan §24](../plan.md#24-virtualized-rendering)):

```text
viewport
   ↓
spatial query
   ↓
visible object list
   ↓
render
```

- Never decode every page object every frame.
- Cache immutable render-ready representations.
- Invalidate only affected regions where practical.

## 5. Spatial index

Do not wait for performance problems before adding the spatial abstraction ([plan §22](../plan.md#22-spatial-index)). Start with a simple uniform grid or equivalent:

```text
Page
 └── SpatialIndex
      ├── cell 0
      ├── cell 1
      ├── cell 2
      └── ...
```

Objects register their bounds. Queries:

```text
query(viewport)
query(selectionBounds)
query(lassoBounds)
```

The implementation may change later to an R-tree/quadtree without affecting the editor API.

## 6. Selection (lasso)

Use AndroidX Ink geometry APIs where appropriate, behind a `SelectionGeometry` boundary in core ([plan §21](../plan.md#21-lasso-selection)).

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

Resize/rotate follow once the geometry and transform system are proven.

## 7. Tool state & configuration changes

Tool state survives normal configuration changes where appropriate ([plan §2](../plan.md#2-v01-definition-of-done)). Examples:

- selected tool (pen vs. eraser vs. lasso)
- brush color/size
- viewport position/zoom

Transient in-progress state (an active stroke) is handled by the canvas's own lifecycle, not by general UI state restoration.

## 8. Accessibility bridge

The canvas exposes meaningful actions where feasible ([plan §27](../plan.md#27-accessibility)):

```text
Undo
Redo
Delete selection
Duplicate selection
```

Semantic controls must not be hidden inside a custom canvas when they need to be accessible. See [plan §27](../plan.md#27-accessibility) for the full v0.1 accessibility requirements.

## 9. Property-based testing candidates

The state model is designed to be testable ([plan §50](../plan.md#50-property-based-testing-candidates)):

```text
Transform invariants:
  apply(transform, point); inverse(transform) ≈ original point

Serialization round-trip:
  object → serialize → deserialize ≈ original object

Undo/redo:
  state → operations → undo all ≈ initial state

Spatial index:
  indexed query results == brute-force query results
```

See [testing-guide.md](../contributing/testing-guide.md) for where these tests live.