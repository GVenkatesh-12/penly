# Getting Started

Penly is a **CI-first project**: you do not need Android Studio or a powerful machine to contribute. You need only a JDK (17) for quick local syntax checks — everything else runs on GitHub Actions.

Also read: [CONTRIBUTING.md](../../CONTRIBUTING.md) and [ci-github-actions.md](../ci/ci-github-actions.md).

---

## 1. Requirements

| Requirement | Version | Why |
|---|---|---|
| JDK | 17 | local Kotlin compile checks; nothing else runs locally |
| Git | any | clone, branch, push |
| Editor | any | VS Code + Kotlin plugin recommended, but any editor works |

**You do not need:** Android Studio, the Android SDK, an emulator, or a large build machine.

## 2. One-time setup

### Install a JDK (example, Linux)

```bash
sudo apt install openjdk-17-jdk          # Debian/Ubuntu
# or via a JDK manager: sdkman, asdf, nix, ...
```

Verify:

```bash
java -version   # expect 17.x
```

### Clone

```bash
git clone <your-fork-url> penly
cd penly
```

## 3. Development loop

### 1. Pick or create an issue

Look for `good first issue` labels, or open an issue proposing your change before starting large work.

### 2. Branch

```bash
git checkout -b feat/my-change
```

### 3. Write code

- Keep changes small and focused ([commit conventions](code-conventions.md#commit-conventions)).
- Respect the architecture rules ([architecture-overview.md](../architecture/architecture-overview.md)).
- Add tests with your change where relevant ([testing-guide.md](testing-guide.md)).

### 4. Local sanity check (optional)

```bash
./gradlew compileDebugKotlin
```

This runs a quick Kotlin compile for the debug variant — the only locally practical check. If it is too slow or fails on your machine, **stop worrying**: CI is the authoritative gate.

### 5. Push and open a PR

```bash
git add -A && git commit -m "feat(ink): add stylus authoring pipeline"
git push -u origin feat/my-change
```

Open a pull request against `main`. GitHub Actions runs the full PR pipeline automatically ([ci-github-actions.md](../ci/ci-github-actions.md)).

### 6. Read CI results and iterate

1. Open the PR → **Checks** tab.
2. If a job fails, open it, read which step failed, and download the `test-reports` artifact for details.
3. Fix locally, push; CI re-runs.

### 7. Get the APK

From any successful run, open the workflow run and download the `penly-debug-apk` artifact ([ci-github-actions.md](../ci/ci-github-actions.md#6-getting-artifacts--results)).

## 4. What runs in CI on your PR

```text
format check (ktlint)
lint
static analysis (detekt)
unit tests
serialization tests
migration tests
debug APK build
```

Exit criterion for merge: `./gradlew check` green on a clean runner.

## 5. What runs nightly (not on PRs)

- instrumentation tests on an emulator (software rendering)
- macrobenchmark regression checks
- large-document stress tests (datasets A–G)
- fuzz corpus

## 6. Contributing without running Gradle at all

Completely fine. You can contribute:

- docs and README improvements
- issue triage and reproductions
- code review
- design discussion

For code, keep changes small and well-reasoned; CI verifies the rest.

## 7. Milestones orientation

The project builds toward these milestones ([plan §70](../plan.md#70-suggested-initial-repository-milestones)):

```text
M0 — build foundation          (current focus: repo, CI, version catalog, modules)
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

Good first contributions: M0 foundation tasks, docs, and test infrastructure.