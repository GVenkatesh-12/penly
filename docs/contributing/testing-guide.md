# Testing Guide

How Penly is tested — and how CI makes testing available to everyone, regardless of local machine power.

Source of truth: [plan.md §49–51, §64–66](../plan.md#49-testing-pyramid).

---

## 1. Testing pyramid

```text
        ▲  Device tests (real stylus devices)
       / \  — few, expensive, critical
      /   \
     / UI  \  Compose UI tests (emulator)
    /--------\
   / Integration \  Room + file store, journal, migration, export
  /----------------\
 /   Unit tests     \  heavy coverage: model, geometry, history,
/--------------------\  serialization, migration, file store, index
```

### Unit tests (JVM — run everywhere, including CI)

Heavy coverage for:

- document model
- transformations
- geometry
- history (undo/redo)
- serialization
- migration
- file store
- repository logic
- spatial index

### Integration tests

- Room + file store
- journal replay
- document save/load
- migration
- export

### UI tests (Compose, emulator)

- create notebook
- create page
- change tool
- write/erase
- select/move
- save/reopen
- settings

### Device tests (physical hardware)

Real stylus devices for:

- pressure
- palm rejection
- latency
- rotation
- background/resume
- large-screen layouts

Physical device testing is the **only** way to validate stylus feel — emulators cannot measure this.

## 2. Where tests run

| Layer | When | Environment |
|---|---|---|
| Unit / serialization / migration | every PR (ci.yml) | JVM on GitHub runners |
| Instrumentation / Compose UI | nightly (nightly.yml) | emulator, software rendering |
| Macrobenchmarks | nightly | emulator — relative regression only ([performance.md](../architecture/performance.md)) |
| Large-doc & fuzz corpus | nightly | JVM on GitHub runners |
| Device tests | manual/release candidate | physical devices |

See [ci-github-actions.md](../ci/ci-github-actions.md) for workflow details and artifact download instructions.

## 3. Test infrastructure

- **JUnit** for unit tests
- **AndroidX Test** (Espresso/Compose UI test) for instrumented tests
- **Macrobenchmark** for user-visible flow performance
- **testing-fakes** / **testing-fixtures** modules provide shared test doubles and deterministic datasets (datasets A–G, [plan §39](../plan.md#39-performance-test-corpus))
- **testing-benchmarks** hosts benchmark suites

## 4. Property-based testing candidates

Strong candidates ([plan §50](../plan.md#50-property-based-testing-candidates)):

### Transform invariants

```text
apply(transform, point)
then inverse(transform)
≈ original point
```

### Serialization round-trip

```text
object → serialize → deserialize ≈ original object
```

### Undo/redo

```text
state → operations → undo all ≈ initial state
```

### Migration

```text
vN → migrate → serialize → deserialize
```

### Spatial index

```text
indexed query results == brute-force query results (same dataset)
```

## 5. Fuzz testing

Fuzz ([plan §51](../plan.md#51-fuzz-testing)):

- malformed document manifests
- corrupted stroke payloads
- truncated files
- invalid transforms
- unsupported brush IDs
- extreme coordinates
- NaN/infinite numeric values
- huge object counts
- malformed image/PDF assets

The app must **fail gracefully instead of crashing the whole editor**.

## 6. Explicit failure scenarios

Tested explicitly ([plan §66](../plan.md#66-failure-scenarios-to-test-explicitly)):

- app killed during stroke / during save
- device restarted immediately after writing
- storage nearly full / completely full
- malformed native document
- missing asset / corrupt asset
- old document version / future unknown version
- screen rotated during writing
- app backgrounded during writing
- process recreated / low-memory kill
- very long stroke
- extremely zoomed / tiny zoom
- 50k+ stroke page
- 500-page notebook
- rapid undo/redo
- repeated imports/exports

## 7. Update & migration testing

For every app release that changes persistence ([plan §65](../plan.md#65-update-and-migration-testing)):

```text
Install old APK → create document → populate content
→ upgrade APK → open → edit → save → restart → reopen → export
```

Automated as an upgrade test where possible; also see [migration-policy.md](../document-format/migration-policy.md).

## 8. Rules

- New logic in core modules ships with unit tests.
- Serialization, migration, journal replay, and file-store logic need round-trip or integration tests.
- UI changes affecting core flows include Compose UI tests where feasible.
- **Never disable a failing test to merge**; fix the root cause.
- Performance-sensitive changes reference before/after measurements ([performance.md](../architecture/performance.md)).

## 9. Contributing tests without a build machine

- Tests you write are verified by CI on your PR — you never need to run them locally.
- Write tests as code in the normal PR flow; CI executes them and reports failures with full stack traces in the `test-reports` artifact.
- For complex scenarios you cannot verify locally, describe expected behavior in the PR; reviewers/CI will validate.