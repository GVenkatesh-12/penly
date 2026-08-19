# CI on GitHub Actions

Penly is a **CI-first project**: GitHub Actions is the primary — and for most contributors, the only — build and test environment. Local machines need only a JDK for syntax checks; every real verification (lint, tests, APK builds, emulator runs) happens in CI.

Source of truth: [plan.md §64](../plan.md#64-ci-pipeline).

---

## 1. Workflow overview

Three workflows under `.github/workflows/`:

| Workflow | File | Trigger | Purpose |
|---|---|---|---|
| CI (PR checks) | `ci.yml` | every push / PR | fast feedback: format, lint, static analysis, unit + serialization + migration tests, debug APK, instrumented workflow tests on emulator |
| Nightly | `nightly.yml` | scheduled (`cron`), manual dispatch | heavy verification: instrumentation on emulator, large-document tests, fuzz corpus |
| Release | `release.yml` | tag push or manual dispatch | release build, signing, install/update verification, GitHub Release |

### Per-PR checks (ci.yml)

```text
format check (ktlint)
lint (Android lint)
static analysis (detekt)
unit tests
compile instrumentation tests (androidTest sources — catches missing imports)
serialization tests
migration tests
build debug APK
instrumentation tests (emulator, API 34): workflow + recovery + persistence suites
```

Exit criterion: `./gradlew check` passes on a clean runner ([plan §55, Phase 0](../plan.md#phase-0--repository-foundation)).

### Nightly (nightly.yml)

```text
instrumentation tests (emulator)
macrobenchmarks (emulator, regression-only)
large document tests (datasets A–G, [plan §39](../plan.md#39-performance-test-corpus))
fuzz corpus
```

### Release candidate (release.yml)

```text
full test matrix
release build
signing validation
installation/update test (install old → upgrade → open → edit → save → reopen → export)
backup/restore test
```

## 2. Suggested job layout (ci.yml)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  checks:
    name: Checks
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Format check
        run: ./gradlew ktlintCheck
      - name: Static analysis
        run: ./gradlew detekt
      - name: Lint
        run: ./gradlew lintDebug
      - name: Unit tests
        run: ./gradlew testDebugUnitTest
      - name: Serialization + migration tests
        run: ./gradlew :core-document:test :core-database:test

  instrumentation:
    name: Instrumentation (API 34)
    runs-on: ubuntu-latest
    needs: checks
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Enable KVM group permissions
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Instrumentation tests
        uses: ReactiveCircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          profile: pixel_7
          disable-animations: true
          force-avd-creation: false
          script: ./gradlew connectedDebugAndroidTest

  build:
    name: Build debug APK
    runs-on: ubuntu-latest
    needs: checks
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Build debug APK
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: penly-debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

## 3. Emulator strategy (instrumentation)

GitHub-hosted Linux runners expose `/dev/kvm`, but the runner user needs group
permissions — grant them with a udev rule before launching the emulator,
otherwise the emulator falls back to software emulation (`-accel off`) and is
too slow to boot/respond (CI fails with "No compatible devices connected"):

```yaml
- name: Enable KVM group permissions
  run: |
    echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
    sudo udevadm control --reload-rules
    sudo udevadm trigger --name-match=kvm
- uses: ReactiveCircus/android-emulator-runner@v2
  with:
    api-level: 34
    arch: x86_64
    profile: pixel_7
    disable-animations: true
    force-avd-creation: false
    script: ./gradlew connectedDebugAndroidTest
```

Operational notes:

- Use `android-emulator-runner` (handles AVD setup, waits for boot, hardware-acceleration detection).
- The KVM udev step above is required on Linux runners — without it the action detects no accel and runs `-accel off`.
- Add `disable-animations: true` for stable instrumentation.
- Pick **one representative API level** (34) for instrumentation on every push/PR; keep the suite focused on workflow/recovery/persistence tests so per-PR emulator time stays reasonable.
- Split test classes across parallel runners if the suite grows too slow.

### Macrobenchmarks on emulators — caveat

Software-rendered emulators cannot produce meaningful absolute latency/FPS numbers. CI macrobenchmark results are used **only as relative regression detectors** (same environment, compare deltas). Real stylus latency and palm-rejection measurement requires physical devices ([performance.md](../architecture/performance.md)).

## 4. Release builds & signing

- The keystore and its passwords are stored as **GitHub repository secrets** (`PENLY_KEYSTORE_BASE64`, `PENLY_KEYSTORE_PASSWORD`, `PENLY_KEY_ALIAS`, `PENLY_KEY_PASSWORD`).
- The release job decodes the keystore into a temp file, signs, then wipes it.
- **Never commit keystores or signing material to the repository.**
- Release artifacts are published to GitHub Releases; `CHANGELOG.md` is updated as part of the release ([release-process.md](../releases/release-process.md)).

```yaml
- name: Decode keystore
  run: echo "${{ secrets.PENLY_KEYSTORE_BASE64 }}" | base64 -d > $RUNNER_TEMP/penly.keystore
- name: Assemble release
  run: ./gradlew assembleRelease
    -PkeystoreFile=$RUNNER_TEMP/penly.keystore
    -PkeystorePassword=${{ secrets.PENLY_KEYSTORE_PASSWORD }}
    ...
```

## 5. Build caching & speed

- `gradle/actions/setup-gradle@v4` enables the Gradle build cache and configuration cache across runs.
- Enable Gradle **configuration cache** (`org.gradle.configuration-cache=true`) once the build is stable; it cuts cold config time substantially.
- Enable **dependency verification** (`dependencyVerification`) per [plan §5 rule 7](../plan.md#5-dependency-policy); update verification metadata only through reviewed dependency-update PRs.
- Keep dependency updates automated via Renovate/Dependabot **PRs only** — never unattended production upgrades ([plan §5](../plan.md#5-dependency-policy)).
- Artifact retention: keep debug APKs for ~14 days, release APKs permanently.

## 6. Getting artifacts & results

Everyone (especially contributors without local build capability) consumes CI through the GitHub UI:

1. Open the workflow run (PR → Checks → details, or Actions tab).
2. Read the job steps; failed steps link to the exact failing test/log.
3. Download test reports and APKs from the **Artifacts** section at the bottom of the run.

Test report artifact example:

```yaml
- name: Upload test reports
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-reports
    path: |
      **/build/reports/tests/**
      **/build/reports/lint-results-*.html
```

## 7. Secrets & permissions reference

| Secret | Used by | Notes |
|---|---|---|
| `PENLY_KEYSTORE_BASE64` | release.yml | base64 of release keystore |
| `PENLY_KEYSTORE_PASSWORD` | release.yml | keystore password |
| `PENLY_KEY_ALIAS` | release.yml | signing key alias |
| `PENLY_KEY_PASSWORD` | release.yml | signing key password |
| `GITHUB_TOKEN` | all | automatic; used for GitHub Releases upload |

Prefer `permissions: { contents: read }` (write only in release.yml where needed) — least-privilege for workflows.

## 8. Failure handling rules

- A red `checks` job blocks merge (branch protection on `main`).
- Nightly failures open an issue automatically (or are reported in the run summary) — they don't block PRs.
- Never "fix" a red CI by disabling the failing test; fix the root cause.
- Failing migration/serialization tests are **P0-level** (data integrity): fix immediately ([SECURITY.md](../../SECURITY.md)).

## 9. Adding or changing workflows

- Workflow changes go through the same PR review as code.
- Test workflow syntax locally with `act` when available, but the authoritative test is a real GitHub run.
- Keep jobs idempotent and cache-friendly; every workflow change must not degrade the per-PR feedback time below ~10–15 minutes.