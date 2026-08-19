# AGENTS.md

Build process and environment facts for agent sessions working in this repo.

## Project

Penly — an Android handwriting/notebook app (Compose, Room, Ink). CI-first:
full builds, lint, and tests run on GitHub Actions. The local dev machine only
runs JDK-only checks (`./gradlew check`); it is too weak for Android Studio.

- `docs/plan.md` is the source of truth for phases and quality gates.
- Phase 0 exit criterion: `./gradlew check` green on a clean machine (CI).
- Module graph: `settings.gradle.kts` (app + 13 `core/*` + 4 `feature/*` + 5
  `editor/*` + 4 `platform/*` + 3 `testing/*` = 29 modules).
- Version catalog: `gradle/libs.versions.toml` — update versions there.

## Local environment (user machine)

- OS: Ubuntu (Linux), user `gvenkatesh`. NOT a git repo (use git add/commit as usual).
- System Java is a **JRE only** (`/usr/lib/jvm/java-21-openjdk-amd64`, no javac).
  Use the JDK at `~/jdk/jdk-21.0.12+8` (Temurin 21). Export before every build:
  `export JAVA_HOME=~/jdk/jdk-21.0.12+8`
- No system Gradle. Use the wrapper: `./gradlew` (Gradle 9.7.0).
- Android SDK at `~/Android/Sdk` (cmdline-tools `latest`, platforms;android-36,
  build-tools;36.0.0, platform-tools). `local.properties` has `sdk.dir` set and
  is gitignored. `ANDROID_HOME=~/Android/Sdk`.
- Constraints: ~3.4 GiB free RAM (daemon `-Xmx2g`, see `gradle.properties`),
  4 cores, ~8 GB free disk. Use long timeouts for first-run builds (dependency
  download dominates). Keep SDK/install footprint lean; prefer user-dir
  installs over sudo (no passwordless sudo available).
- `org.gradle.configuration-cache=true` and `org.gradle.caching=true` are on.

## Commands

```bash
export JAVA_HOME=~/jdk/jdk-21.0.12+8
./gradlew check        # Phase-0 gate: all subproject checks (unit tests, lint, ktlint, detekt)
./gradlew :app:assembleDebug   # APK
./gradlew projects     # list modules
# Instrumented workflow/UX suites need a device/emulator (local machine too weak):
./gradlew connectedDebugAndroidTest   # runs on CI (API-34 emulator job) per push
```

First run downloads all dependencies — allow 10–20+ min. Configuration-cache
serialization warnings are logged but non-fatal unless the build fails.

## Build-system gotchas (learned the hard way)

- **AGP 9.3.0 has built-in Kotlin**: do NOT apply `org.jetbrains.kotlin.android`.
  Kotlin compiler plugins (compose, serialization) at 2.2.10 via
  `org.jetbrains.kotlin.plugin.*`; KSP 2.3.11; no `kotlinOptions` block.
- **`plugins {}` blocks must NOT contain commas** between `alias(...)` lines —
  script compilation fails.
- **Gradle 9.7 creates virtual container projects** (`:core`, `:editor`,
  `:feature`, `:platform`, `:testing`) that have no `check` task. The root
  `check` in `build.gradle.kts` filters them: `subprojects.filter { it.buildFile.exists() }`.
- **androidx.datastore:datastore-preferences stable is 1.2.1** (1.3.0 is
  alpha-only) — verified against maven.google.com. `androidx.test.ext:junit`
  1.3.0 and `espresso-core` 3.7.0 verified OK.
- Versions must be verified against maven.google.com metadata before use.

## Version stack (verified Aug 2026)

Gradle 9.7.0 wrapper · AGP 9.3.0 · Kotlin compiler plugins 2.2.10 · KSP 2.3.11 ·
Compose BOM 2026.06.01 (ui/foundation/runtime 1.11.4, material3 1.4.0) ·
AndroidX Ink 1.0.0 stable (plan mandates 1.0.0, not 1.1.0-alpha) · Room 2.8.4 ·
Hilt 2.60.1 · coroutines 1.11.0 · serialization-json 1.11.0 · core-ktx 1.18.0 ·
lifecycle 2.10.0 · navigation-compose 2.10.0 · work 2.12.0 ·
datastore-preferences 1.2.1 · activity-compose 1.13.0 · coil3 3.5.0 ·
androidxTestExt 1.3.0 · espresso 3.7.0 · ktlint plugin 14.2.0 · detekt 1.23.8 ·
junit 4.13.2 · androidx.test runner/ext via TestExt.

App: package `com.penly.app`, compileSdk 36, minSdk 26, targetSdk 36,
versionName `0.1.0-alpha.1`.

## Repo conventions

- Apache-2.0, app name "Penly" (committed docs + license already on `main`).
- Keep lines ≤ 100 chars where ktlint can see them; ktlint + detekt run as part
  of `check` (plugins configured in root `build.gradle.kts`).
- CI workflows (`docs/ci/ci-github-actions.md`): `ci.yml`, `nightly.yml`,
  `release.yml` (signing secrets `PENLY_KEYSTORE_BASE64`, `PENLY_KEYSTORE_PASSWORD`,
  `PENLY_KEY_ALIAS`, `PENLY_KEY_PASSWORD`) — to be created in
  `.github/workflows/` (Phase 0 remaining work).
- Commit style: imperative, concise, matching history (`docs: ...`, `build: ...`).
  Push to `origin main` after each green step.
- Dependency doctrine: reuse over reinvent — prefer a stable, verified library
  that fits the workflow over building the same capability in-house
  ([plan §5](docs/plan.md#5-dependency-policy)); in-house code only for core
  differentiators.

## Current state (Phase 1–4 — Ink Lab, objects, storage, crash safety)

Phase 0 done: docs + license (commit `556420e`); settings.gradle.kts; root
build.gradle.kts; gradle.properties; .gitignore; gradle wrapper 9.7.0;
libs.versions.toml; all 29 module build files; SDK + JDK installed locally;
core-common `PenlyIds` + unit test; ktlint + detekt applied to all modules;
aggregate root tasks (`ktlintCheck`, `detekt`, `lintDebug`,
`testDebugUnitTest`, `assembleDebug`); CI workflows + dependabot (commit
`8b21d3c`). CI actions bumped to current majors (checkout v7, setup-java v5,
setup-gradle v6, upload-artifact v7, gh-release v3, github-script v9, commit
`90d326f`).

Phase 1 (Ink Lab) in progress: core:core-ink (pure logic + unit tests:
`PenTool`, `CanvasViewport` with pan/zoom, `InkHistory` undo/redo cap 500,
`InputSanitizer`, `StrokeRecord`, `BrushFactory`); editor:editor-canvas
(`InkCanvasState`, `InkCanvas` + unified pointerInput gesture — 1 pointer
draws/erases, 2+ pinch-zoom + pan with stroke abort, `fpsOverlay`);
feature:feature-editor (`editorScreen` Scaffold + `brushBar` FilterChips);
app wired to editor screen. `./gradlew check` green locally; smoke
androidTest checks "Page 1" title.

AndroidX Ink 1.0.0 API constraints (learned the hard way):
- `StockBrushes.pencilUnstable` and `StockTextureBitmapStore` are
  `@RestrictTo(LIBRARY_GROUP)` — do NOT use them. Pencil maps to
  `StockBrushes.pressurePen()` for now; use `CanvasStrokeRenderer.create()`
  (public, default null texture store) instead of create(store).
- Public stock brushes: `pressurePen()`, `marker()`, `highlighter()`,
  `dashedLine()`.
- Compose PointerType has no `Pen` since 1.7 — use `Stylus`/`Eraser`.

Deferred version bumps (closed dependabot PRs, re-proposed on next weekly
run): kotlin 2.2.10 → 2.4.10 (needs review vs AGP 9.3.0 embedded KGP) and
agp 9.3.0 → 9.3.1. core-ktx 1.19.0 stays at 1.18.0 — 1.19.0 requires
compileSdk 37 and we target 36.

## Current state (Phase 4 Crash Safety — in progress)

Master plan (Phases 8–17, feature matrix, ADRs 013–019) committed in `84202b8`
(see `docs/plan.md` Part II).

Phase 4 implemented so far, `./gradlew check` green locally:
- Atomic writes in `FileContentStore.put`/`move` (temp sibling + fsync +
  atomic rename + best-effort dir fsync).
- Journal commit protocol in `PenlyStore`: `save()` stages page+index copies
  under `<docId>/journal/`, writes `commit.json` marker, writes main files +
  manifest, then deletes journal; `load()` replays the journal first and
  reports `LoadResult.Success.recovered`; corrupt/missing journal is ignored;
  assets live in manifest only (corrupt asset = warning, never blocks).
- Recovery UI: "Recovered unsaved changes" banner in `EditorScreen`.
- Fault-injection tests: `PenlyStoreCrashSafetyTest` (CrashInjectingStore
  `crashAfter` sweep through every save mutation + per-stage crash tests +
  journal-residue/corruption cases); `FileContentStoreTest` asserts no temp
  files survive.
- Workflow/UX suites (instrumented, run in CI on an API-34 emulator):
  `InkInputHandlerTest` (gesture layer), `EditorWorkflowUiTest` (draw/erase/
  undo/dialogs/selection/pinch flows against an in-memory `PenlyStore`),
  `PersistenceLifecycleUiTest` (process-death reopen on `MainActivity`),
  `RecoveryBannerUiTest` (journal-residue UX); `PenlyStoreWorkflowTest` is
  JVM and runs in `./gradlew check`.

ktlint quirks (learned the hard way): dot-chains containing a mid-chain call
must wrap every link — `json` alone on its line, `.encodeToString(...)` on the
next, and `).toByteArray(...)` glued to the closing paren; a trailing `.` link
after a call needs the chain hoisted into a local instead.

Next: push Phase 4, verify CI; remaining Phase 4 exit criteria per plan §55.
Any failing task — fix, don't silence.