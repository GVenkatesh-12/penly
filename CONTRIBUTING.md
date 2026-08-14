# Contributing to Penly

Thank you for contributing to Penly — an open-source Android handwriting app.

> **Penly is a CI-first project.** You do not need Android Studio, a full Gradle build environment, or a powerful machine to contribute. You need only a JDK for quick local syntax checks; all real verification (lint, tests, APK builds) runs automatically on GitHub Actions when you push a branch or open a pull request.

---

## Code of conduct

By participating, you agree to uphold the [Code of Conduct](CODE_OF_CONDUCT.md). Be respectful, constructive, and inclusive.

---

## What kind of contributions help?

- Bug reports with clear reproduction steps (see [SECURITY.md](SECURITY.md) for security issues)
- Fixes for P3/P4 bugs (see issue priority system below)
- Unit / integration / UI test additions
- Documentation improvements
- Performance work that is measured against the [performance budget](docs/architecture/performance.md)
- Reviewing open pull requests

Before starting a larger change, open an issue or comment on an existing one so the work is visible and not duplicated.

## Issue priority system

Used across the issue tracker and [release process](docs/releases/release-process.md):

```text
P0 — data loss / corruption / unrecoverable crash
P1 — core writing experience broken
P2 — major feature broken / severe performance issue
P3 — normal bug
P4 — polish / enhancement
```

A P0 or P1 issue blocks a stable release.

---

## Development environment (minimal)

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 | Only requirement for local work; no Android SDK needed locally |
| Editor | any | VS Code + Kotlin plugin, IntelliJ, or any text editor |
| Git | any | Git LFS not required |

There is **no requirement** to install Android Studio, the Android SDK, or run full builds locally. The project is configured so that CI produces everything (test results, APKs, reports).

### Install a JDK (example, Linux)

```bash
sudo apt install openjdk-17-jdk   # Debian/Ubuntu
# or use a JDK manager: sdkman, asdf, etc.
```

### Verify the toolchain

```bash
java -version   # expect 17.x
```

### Local sanity check (optional but recommended)

Quick syntax / Kotlin compile check without a full build:

```bash
./gradlew compileDebugKotlin
```

This only requires the JDK plus network access on first run (Gradle wrapper downloads). If it takes too long or fails on your machine, do not worry — **CI is the authoritative gate** (`./gradlew check` on a clean machine).

---

## The CI-first contribution loop

1. **Fork** the repository (or create a branch if you have push access).
2. **Write code** locally. Edit with any editor. Use short, focused commits (see [Commit conventions](#commit-conventions)).
3. **Push** your branch.
4. **Open a pull request.** GitHub Actions runs the full PR pipeline automatically: format check, lint, detekt, unit tests, serialization and migration tests, and a debug APK build. See [ci-github-actions.md](docs/ci/ci-github-actions.md) for details.
5. **Read CI results** on the PR's "Checks" tab. If something fails, download the failing test reports from the workflow run artifacts and fix locally.
6. **Iterate.** Push new commits; CI re-runs.

### Getting APKs from CI

APKs are uploaded as workflow artifacts. From any successful run:

1. Open the workflow run (PR → Checks → workflow → details).
2. Scroll to the **Artifacts** section at the bottom.
3. Download `penly-debug-apk` (or the release APK from release runs).

### If you cannot run Gradle at all

You can still contribute meaningfully: docs, issue triage, review, design discussions, and test descriptions. For code, keep changes small and clearly reasoned; CI will verify the rest.

---

## Branch and PR conventions

- Work on short-lived feature branches, not directly on `main`.
- Branch naming is free-form, but descriptive: `feat/lasso-selection`, `fix/stroke-recovery`, `docs/adr-013`.
- Open the PR against `main`.
- Reference the issue(s) the PR addresses in the description.
- Keep PRs focused. Avoid giant commits mixing UI + storage + renderer changes.

### Commit conventions

Conventional Commits style, scoped to the module or concern:

```text
feat(ink): add stylus authoring pipeline
feat(editor): add lasso selection
fix(storage): recover interrupted page writes
perf(renderer): cache immutable stroke meshes
test(document): add format round-trip fixtures
docs(ci): document nightly emulator workflow
```

## Code review expectations

- Automated checks must pass before merge (CI is the gate).
- Manual review focuses on architectural rules (see below), data integrity, and performance.
- Prefer small, reviewable diffs.
- Do not merge your own PR unless it is a trivial doc change and no one else is available.

---

## Architecture rules every change must respect

The full rules live in [architecture-overview.md](docs/architecture/architecture-overview.md). The non-negotiable ones:

1. **No Compose/Android dependencies in core modules.** `core-model`, `core-document`, `core-ink`, `core-geometry`, `core-renderer`, `core-storage`, `core-database`, `core-search`, `core-export`, `core-settings`, `core-telemetry` never import Compose, Activity, or ViewModel.
2. **Dependency direction is one-way:** `feature → editor/core/platform`, `editor → core + platform`, `core → core only`, `platform → Android-specific APIs`, `app → feature modules`.
3. **Stable IDs everywhere.** Never use list indexes as persistent identity.
4. **No AndroidX Ink types leaking through domain interfaces.** Always go through the `InkAdapter` boundary.
5. **No logging of user content.** See [privacy-model.md](docs/privacy/privacy-model.md).

## Dependency policy

Treat dependencies as part of the architecture ([plan §5](docs/plan.md#5-dependency-policy)):

1. Never use an alpha dependency in the hot path merely because it has a desirable feature.
2. Experimental dependencies must be isolated behind an interface/adapter.
3. Every dependency needs an owner module and a reason to exist.
4. Prefer official AndroidX/Kotlin libraries for foundational platform behavior.
5. Do not add a library for functionality requiring fewer than roughly 100 lines of clear, well-tested code unless it solves a difficult platform compatibility problem.
6. Pin versions centrally in `gradle/libs.versions.toml`.
7. Use dependency locking/verification where practical.
8. Review release notes before upgrades.
9. Upgrade one foundation family at a time when possible.
10. Every persistence dependency upgrade must run migration and recovery tests.

Never bump versions ad hoc in module build files. Update the version catalog and let CI verify.

## Performance rules

The [performance budget](docs/architecture/performance.md) is acceptance criteria. In particular, never introduce the banned anti-patterns from [plan §67](docs/plan.md#67-performance-anti-patterns-to-ban), e.g.:

- database writes on every pointer sample
- bitmap screenshot as canonical page state
- serializing the entire notebook for every stroke
- synchronous file I/O during active writing
- AndroidX Ink classes exposed through every domain interface

## Testing expectations

- New logic in core modules should come with unit tests.
- Serialization, migration, journal replay, and file-store logic need round-trip or integration tests.
- UI changes that affect core flows (write, erase, select, save/reopen) should include Compose UI tests where feasible.
- Never disable a failing test to merge; fix the root cause.
- See the [testing guide](docs/contributing/testing-guide.md).

## Documentation

Write documentation as the system is built, not after it ([plan §62](docs/plan.md#62-documentation-requirements)):

- Public behavior changes → update the relevant doc under `docs/`.
- Architecture decisions → add an ADR under `docs/architecture/adr/`.
- Format changes → update `docs/document-format/format-spec.md` and add a migration.

## Git history

- Use short-lived feature branches.
- Squash-and-merge or rebase to keep `main` history clean.
- Never force-push to `main`.
- Tagged releases match [CHANGELOG.md](CHANGELOG.md) entries.

## Getting help

- Open an issue for questions about direction.
- PR comments are the best place for code-level questions.
- Review the documentation index at [docs/index.md](docs/index.md) before asking — it likely answers the question.
