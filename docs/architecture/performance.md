# Performance Guide

Performance is a feature. The budgets below are **acceptance criteria**, not optional optimization work.

Source of truth: [plan.md §38–40, §67](../plan.md#38-performance-budget).

---

## 1. Performance budget

Engineering targets, not platform guarantees:

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

## 2. Performance test corpus

Deterministic datasets ([plan §39](../plan.md#39-performance-test-corpus)):

```text
Dataset A: 100 strokes
Dataset B: 1,000 strokes
Dataset C: 10,000 strokes
Dataset D: 50,000 strokes
Dataset E: mixed ink + images + text
Dataset F: 500-page notebook
Dataset G: PDF + annotations
```

Measure: first open, cached open, pan, zoom, drawing, selection, save, thumbnail generation, memory, GC pressure, battery impact.

Include real exported stroke samples from multiple devices where licensing/privacy permits.

## 3. Benchmark strategy

### Macrobenchmark (AndroidX) for user-visible flows

- cold start
- home-to-editor
- document open
- page navigation
- library scroll
- export startup

### Dedicated instrumentation for engine metrics

- stroke processing time
- renderer frame timing
- journal append time
- page load time
- object query time

### Android Studio profiling investigations

- allocations during active strokes
- main-thread work
- GPU frame cost
- bitmap memory
- cache behavior

Baseline Profiles are introduced once critical startup/navigation traces are stable ([plan §40](../plan.md#40-benchmark-strategy)).

### CI caveats (GitHub Actions emulators)

GitHub-hosted runners run emulators **without hardware acceleration** (software rendering). Consequences:

- Absolute latency/FPS numbers from CI are **not** comparable to real devices.
- Treat CI macrobenchmark results as **regression detectors** (compare relative deltas against the same environment), never as absolute scores.
- Real-device measurement (especially stylus latency, palm rejection, high-refresh rendering) requires physical devices — see [plan §49, Device tests](../plan.md#49-testing-pyramid).

See [ci-github-actions.md](../ci/ci-github-actions.md) for the CI setup details.

## 4. Banned anti-patterns

Do not allow the following into production code without a measured justification ([plan §67](../plan.md#67-performance-anti-patterns-to-ban)):

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

## 5. Threading & memory summary

- Main thread: input events, frame coordination, UI state only ([plan §41](../plan.md#41-threading-model)).
- Default/IO: database, file store, serialization, thumbnails, exports.
- Default/CPU: geometry, indexing, expensive transformations.
- Never every page decoded in memory; cache by size and viewport; evict thumbnails aggressively ([plan §42](../plan.md#42-memory-management)).

## 6. Performance engineering checklist

- [ ] Change measured against the budget before/after (microbenchmark or macrobenchmark run)
- [ ] No banned anti-pattern introduced
- [ ] No allocations added to the active-stroke loop
- [ ] CI performance jobs still green (relative regression check)
- [ ] Results recorded in the PR description when non-trivial