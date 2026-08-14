# Release Process

How Penly ships: development phases, quality gates, and the checks that must pass before any tag exists.

Source of truth: [plan.md §55–58, §64, §70](../plan.md#55-development-phases).

---

## 1. Issue priority system

```text
P0 — data loss / corruption / unrecoverable crash
P1 — core writing experience broken
P2 — major feature broken / severe performance issue
P3 — normal bug
P4 — polish / enhancement
```

**A P0 or P1 blocks a stable release** ([plan §56](../plan.md#56-issue-priority-system)).

## 2. Development phases

The project proceeds through phases with explicit exit criteria ([plan §55](../plan.md#55-development-phases)):

| Phase | Focus | Exit criterion |
|---|---|---|
| 0 — Repository foundation | Git, Gradle KTS, version catalog, variants, lint, formatting, module graph, CI, design tokens, test infra | `./gradlew check` green on a clean machine (CI) |
| 1 — Ink Lab | AndroidX Ink 1.0.x, stylus input, brushes, eraser, pressure, pointer classification, palm handling, low-latency renderer, zoom/pan, perf overlay | responsive on ≥3 representative devices; no allocations/blocking I/O in active-stroke loop |
| 2 — Document Core | IDs, object model, transforms, revisions, serialization, `.penly` format, file store, Room metadata | page saves → closes → reopens without visual drift |
| 3 — Editor Core | viewport, page renderer, tool controller, history, selection, lasso, move/copy/delete, image + text objects | real note created/edited without internal APIs |
| 4 — Crash Safety | journal, autosave, startup replay, atomic writes, integrity, corruption detection, recovery UI | fault injection: no committed content lost |
| 5 — Library/UI | home/library, notebook browser, sections, recent, thumbnails, templates, empty states, settings, adaptive tablet UI | visually coherent, keyboard/mouse usable, tablet polished |
| 6 — Export | PDF, PNG, native export, share, progress/errors | exports deterministic, non-destructive, non-blocking |
| 7 — Hardening | benchmarks, macrobenchmarks, memory profiling, stress tests, migration tests, fuzz, accessibility audit, device matrix, signing, release docs | no known P0/P1 data-loss defects; no critical perf regression |

### Milestones

M0 … M10 ([plan §70](../plan.md#70-suggested-initial-repository-milestones)): build foundation → Ink Lab usable → single persistent page → multi-page document → production editor → crash-safe storage → notebook library → export → performance hardening → release candidate → **v0.1.0-alpha.1**.

Each milestone is independently demonstrable.

## 3. Release quality gates

Before tagging **any** release, run the full gate checklist ([plan §58](../plan.md#58-release-quality-gates)). For `0.1.0-alpha.1`:

### Data integrity
- [ ] crash recovery tested
- [ ] migration tested
- [ ] malformed input tested
- [ ] save/load round-trip tested
- [ ] export tested

### Ink
- [ ] stylus input, pressure, pen, pencil, marker, highlighter, eraser
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
- [ ] phone portrait/landscape
- [ ] tablet portrait/landscape
- [ ] dark mode, large text
- [ ] keyboard/mouse
- [ ] accessibility pass

### Engineering
- [ ] CI green
- [ ] dependency lock/update policy documented
- [ ] reproducible debug/release builds
- [ ] documentation updated
- [ ] changelog updated
- [ ] backup/export documented

## 4. Release candidate checks (CI)

The release candidate pipeline ([plan §64](../plan.md#64-ci-pipeline)):

```text
full test matrix
release build
signing validation
installation/update test (install old APK → upgrade → open → edit → save → reopen → export)
backup restore test
```

## 5. Versioning

- [Semantic Versioning](https://semver.org/), with the pre-1.0 convention: `0.1.0-alpha.1`.
- Version numbers are set once, at release time, in `gradle/libs.versions.toml` / version catalog plus the app module.
- Every release corresponds to a tag and a [CHANGELOG.md](../../CHANGELOG.md) entry.

## 6. Release mechanics (GitHub Actions)

1. Create the release branch / tag (e.g. `v0.1.0-alpha.1`) from `main` after gates pass.
2. `release.yml` runs on the tag: full test matrix, release build, signing via GitHub Secrets, install/update verification ([ci-github-actions.md](../ci/ci-github-actions.md)).
3. On success, publish the signed APK to **GitHub Releases** with the changelog entry attached.
4. Mark pre-release for alpha/beta tags.

**Never** release from a local machine — the release build must come from CI so it is reproducible and signed identically every time.

## 7. Post-release

- Update `CHANGELOG.md` with the release entry (already done before tag).
- Review open P0/P1 issues: any found means a hotfix release is required before the next milestone.
- Update documentation where behavior changed.

## 8. Documentation duty

Release documentation is written as the system is built ([plan §62](../plan.md#62-documentation-requirements)); a release is not complete until the docs that describe its behavior exist and are accurate.