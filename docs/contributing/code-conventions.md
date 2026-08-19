# Code Conventions

How Penly code is written, formatted, and kept consistent. Enforced by CI, reviewed by humans.

Source of truth: [plan.md §5, §63](../plan.md#5-dependency-policy).

---

## 1. Language & style

- **Kotlin**, following [kotlinlang.org style](https://kotlinlang.org/docs/coding-conventions.html) plus the project's [ktlint](https://ktlint.github.io/) configuration.
- Formatting is enforced by **ktlint** (`./gradlew ktlintCheck`) in CI. Run `./gradlew ktlintFormat` locally (if your machine allows) to auto-fix.
- **No comments unless they earn their place** — prefer expressive names and small functions. Comments explain *why*, not *what*.
- Use explicit typed domain errors, not bare exceptions, for expected failures ([plan §35](../plan.md#35-error-handling)): `StorageError`, `DocumentCorruptError`, `UnsupportedFormatError`, `PermissionError`, `ExportError`, `RenderingError`, `MigrationError`.

## 2. Static analysis

| Tool | Runs in CI | Purpose |
|---|---|---|
| ktlint | every PR | formatting enforcement |
| detekt | every PR | Kotlin static analysis (complexity, smells) |
| Android lint | every PR | Android-specific issues |

Configurations live at the repository root (`ktlint.yaml` / `detekt.yml` / lint config) and are changed only via PR review.

## 3. Version catalog & dependency policy

All versions are pinned centrally in `gradle/libs.versions.toml` ([plan §5](../plan.md#5-dependency-policy)):

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

Rules:

1. Never use an alpha dependency in the hot path merely because it has a desirable feature.
2. Experimental dependencies must be isolated behind an interface/adapter.
3. Every dependency needs an owner module and a reason to exist.
4. Prefer official AndroidX/Kotlin libraries for foundational platform behavior.
5. Do not build from scratch what a stable, maintained library already provides: if a module or library fits the workflow, use it. In-house implementation is justified only for core differentiators (ink pipeline, document format) or when no stable fit exists. The default bias is reuse, not reinvention (complements rule 6, the 100-line floor).
6. Do not add a library for functionality that requires fewer than roughly 100 lines of clear, well-tested code unless it solves a difficult platform compatibility problem.
7. Pin versions centrally.
8. Use dependency locking/verification where practical.
9. Review release notes before upgrades.
10. Upgrade one foundation family at a time when possible.
11. Every persistence dependency upgrade must run migration and recovery tests.

Never bump versions ad hoc in module build files. Dependency updates arrive as reviewed PRs (Renovate/Dependabot), never unattended.

## 4. Architecture rules (enforced)

- **No Compose/Android dependencies in core modules.** `core-*` never imports Compose, `Activity`, or `ViewModel` ([architecture-overview.md](../architecture/architecture-overview.md)).
- Dependency direction: `feature → editor/core/platform`, `editor → core + platform`, `core → core only`, `platform → Android-specific`, `app → feature`.
- Stable IDs for everything persisted; never list indexes as identity ([ADR-009](../architecture/adr/ADR-009.md)).
- AndroidX Ink types never leak through domain interfaces; always via `InkAdapter` ([ADR-002](../architecture/adr/ADR-002.md)).
- No user content in logs ([privacy-model.md](../privacy/privacy-model.md)).
- No banned performance anti-patterns ([plan §67](../plan.md#67-performance-anti-patterns-to-ban)).

## 5. Naming

- Module names: `core-*`, `feature-*`, `editor-*`, `platform-*`, `testing-*` ([plan §7](../plan.md#7-module-structure)).
- Files and packages: Kotlin conventions (`CamelCase` types, `camelCase` functions/values, `SCREAMING_SNAKE` constants).
- Domain concepts use the plan's vocabulary: `Document`, `Page`, `InkObject`, `InkAdapter`, `ContentStore`, `EditorSurface`, `ViewportController`.

## 6. Commit conventions

[Conventional Commits](https://www.conventionalcommits.org/), scoped:

```text
feat(ink): add stylus authoring pipeline
feat(editor): add lasso selection
fix(storage): recover interrupted page writes
perf(renderer): cache immutable stroke meshes
test(document): add format round-trip fixtures
docs(ci): document nightly emulator workflow
```

- Short-lived feature branches; never commit directly to `main`.
- Avoid giant commits mixing unrelated concerns (UI + storage + renderer).
- Write the commit message for the reviewer: state *what* and *why*.

## 7. Pull requests

- Target `main`. Reference the issue(s).
- Automated checks must pass; failing tests are fixed, never disabled.
- Human review focuses on architecture rules, data integrity, and performance.
- Keep diffs small and reviewable.

## 8. Documentation duty

- Public behavior changes → update the relevant doc under `docs/` ([index](../index.md)).
- Architecture decisions → add an ADR in `docs/architecture/adr/` ([plan §61](../plan.md#61-architecture-decision-records)).
- Format changes → update `format-spec.md` and add a migration + test.