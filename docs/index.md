# Penly Documentation

Welcome to the Penly documentation. The master plan (`docs/plan.md`) is the **source of truth** for product decisions, scope, and engineering rules. All other documents describe a specific area in detail and are maintained as the system is built.

## Reading order

1. **New to the project?** Start with the [README](../README.md), then the [master plan](plan.md).
2. **Contributor?** Read [CONTRIBUTING.md](../CONTRIBUTING.md) and [getting-started.md](contributing/getting-started.md).
3. **Implementing a feature?** Read the [architecture overview](architecture/architecture-overview.md) plus the area-specific doc below.
4. **Changing persistence or the format?** Read [format-spec.md](document-format/format-spec.md) and [migration-policy.md](document-format/migration-policy.md) — this is the most compatibility-sensitive area.

## Documentation index

### Project

| Document | Purpose |
|---|---|
| [plan.md](plan.md) | Master plan: product thesis, scope, stack, architecture, phases, quality gates (source of truth) |
| [CHANGELOG.md](../CHANGELOG.md) | Version history |

### Architecture

| Document | Purpose |
|---|---|
| [architecture-overview.md](architecture/architecture-overview.md) | Layered modular architecture, module graph, dependency rules, threading & memory model |
| [ink-pipeline.md](architecture/ink-pipeline.md) | Stylus input pipeline, canvas render layers, palm rejection, brush system, eraser/highlighter rules |
| [editor-state-model.md](architecture/editor-state-model.md) | Editor coordinator, command-based undo/redo, viewport, spatial index, selection |
| [storage-recovery.md](architecture/storage-recovery.md) | Room schema, ContentStore, journal/autosave/crash recovery, save semantics, file integrity |
| [performance.md](architecture/performance.md) | Performance budget, benchmark corpus, benchmarking strategy, banned anti-patterns |
| [adr/](architecture/adr/) | Architecture Decision Records (ADR-001 … ADR-019) |

### Features

| Document | Purpose |
|---|---|
| [feature-matrix.md](features/feature-matrix.md) | Complete-product feature-parity benchmark (Tier A–D), mapped to phases (ADR-019) |

### Document format

| Document | Purpose |
|---|---|
| [format-spec.md](document-format/format-spec.md) | The versioned `.penly` document format: package layout, manifest, object model, transforms, compatibility rules |
| [migration-policy.md](document-format/migration-policy.md) | Migration strategy, upgrade compatibility, update/migration testing |

### CI & tooling

| Document | Purpose |
|---|---|
| [ci-github-actions.md](ci/ci-github-actions.md) | GitHub Actions workflows: PR checks, nightly emulator tests, release builds; artifact downloads |

### Contributing

| Document | Purpose |
|---|---|
| [getting-started.md](contributing/getting-started.md) | Minimal JDK-only setup and the CI-first development loop |
| [code-conventions.md](contributing/code-conventions.md) | Kotlin style, lint/detekt/ktlint, version catalog, dependency policy, commit conventions |
| [testing-guide.md](contributing/testing-guide.md) | Testing pyramid, property-based & fuzz testing candidates, CI test matrix |

### Privacy & releases

| Document | Purpose |
|---|---|
| [privacy-model.md](privacy/privacy-model.md) | Privacy-first philosophy, telemetry policy, logging redaction rules |
| [release-process.md](releases/release-process.md) | Development phases, milestones, release quality gates, issue priorities, distribution |

## Documentation conventions

- **Source of truth:** `docs/plan.md`. When another doc and the plan disagree, the plan wins — update the other doc.
- **Written as built:** documentation is written alongside implementation, not after ([plan §62](docs/plan.md#62-documentation-requirements)).
- **ADRs:** every major architectural decision gets an ADR under `docs/architecture/adr/` before or with the implementing change.
- **Format changes:** always update `format-spec.md` and add a migration + migration test together.