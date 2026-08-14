# Ink Pipeline

The ink pipeline is the heart of Penly. It turns stylus events into durable, editable vector strokes with the lowest possible latency.

Source of truth: [plan.md §15–20, §43](../plan.md#15-canvas-architecture).

---

## 1. Input state machine

Input processing is explicit and state-machine based:

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

The input layer must handle:

- `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`, `ACTION_CANCEL`
- pointer IDs
- tool types
- stylus button state
- pressure
- tilt/orientation when available
- palm cancellation signals

Android's stylus/palm guidance is required reading during implementation:

- Advanced stylus support codelab: https://developer.android.com/codelabs/large-screens/advanced-stylus-support
- Stylus/palm rejection guidance: https://developer.android.com/develop/adaptive-apps/cookbook/stylus-palm-rejection

## 2. Canvas architecture

The editor surface is composed of independent subsystems ([plan §15](../plan.md#15-canvas-architecture)):

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

## 3. Render layers

Layers are painted in this order (bottom → top):

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

**Active strokes must have the lowest latency path.** Nothing in the active-stroke layer may wait on application state, database, or Compose recomposition.

## 4. Pointer classification & palm rejection

Four-layer strategy ([plan §17](../plan.md#17-palm-rejection)):

| Layer | Strategy |
|---|---|
| 1 — Tool-aware classification | Prefer stylus input for drawing |
| 2 — Gesture disambiguation | Finger input becomes pan/zoom unless explicitly configured otherwise |
| 3 — Cancellation handling | Correctly handle system-reported canceled pointers and palm rejection signals |
| 4 — Device-specific testing | Test Samsung S Pen, USI stylus, generic active stylus, and devices with poor palm behavior |

Do not claim perfect universal palm rejection.

## 5. Brush system

Brushes are configuration-driven ([plan §18](../plan.md#18-brush-system)):

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

v0.1 ships with: **Pen, Pencil, Marker, Highlighter**.

- Brush IDs must be **stable** because saved documents refer to them.
- If a brush is removed in a future version, its old definition must remain decodable or be migrated to a compatibility brush.

## 6. Highlighter rules

The highlighter is a rendering semantic, not just "a transparent pen" ([plan §19](../plan.md#19-highlighter-rules)). It must have a consistent compositing strategy and must not create surprising darkness where strokes overlap.

Requirements:

- translucent
- stable appearance while drawing
- stable appearance after reload
- stable appearance at different zoom levels
- no flicker when active/final stroke transitions

## 7. Eraser strategy

v0.1 ships with ([plan §20](../plan.md#20-eraser-strategy)):

1. Stroke eraser
2. Optional area/lasso delete through selection

Partial/pixel erasing is only promoted into v0.1 if the selected persistence path is stable and serializable. **Never introduce an eraser that visually works but cannot be reliably saved/reloaded.**

## 8. Stroke data model

A stroke is not a bitmap ([plan §9](../plan.md#9-ink-data-model)):

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

Prefer AndroidX Ink's stroke representation and serialization at the storage boundary. However, the **own document format must not directly depend on an unstable Ink internal wire representation**:

```text
DocumentInk
   ↕
InkAdapter
   ↕
AndroidX Ink
```

This adapter is essential for future dependency upgrades ([plan §48](../plan.md#48-upgrade-compatibility)).

## 9. Active stroke lifecycle

During a stroke:

- Keep the active stroke in memory.
- Render immediately through the low-latency path.
- **Do not write one database row per point.**

On stroke completion ([plan §13](../plan.md#13-crash-safe-write-pipeline)):

1. Convert to immutable stroke data.
2. Append one logical command/journal record.
3. Persist asynchronously.
4. Update page revision.
5. Schedule thumbnail regeneration if necessary.

## 10. Tool flow

```text
Tool selection → ToolController → InputRouter
```

Tools are stateful and must survive appropriate configuration changes (see [editor-state-model.md](editor-state-model.md)).

## 11. Performance constraints

- No serious allocations or blocking storage work in the active stroke loop.
- Active stroke visual latency target: ideally < 20 ms ([performance.md](performance.md)).
- Never: database writes per pointer sample, synchronous file I/O during active writing, creating thousands of Compose nodes per stroke, allocating a new list/object per pointer event unnecessarily.

## 12. The Ink Lab

Before building the full app, a `samples/ink-lab` module serves as the regression playground for the ink engine ([plan §54](../plan.md#54-ink-lab-sample)). It provides:

- pen, pencil, highlighter, eraser
- pressure / pointer type / tilt visualization
- latency/debug overlay, FPS/frame timing
- stroke count, memory estimate

The Ink Lab is the first place new ink-engine work lands and is tested.