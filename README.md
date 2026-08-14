# Penly

> Digital paper with the reliability of a database.

Penly is a state-of-the-art open-source handwriting app for Android. It is built around a single product promise:

> Open a page, put a supported stylus on it, and write naturally with essentially no friction.

Penly treats handwriting as **data, not a screenshot**. Strokes are vector objects with pressure, saved in a versioned local-first format, recovered crash-safely, and never locked behind an account or a cloud service.

**Status:** `0.1.0-alpha.1` — foundational engineering release (see [plan.md](docs/plan.md)).

---

## Product principles

1. **Writing is the primary interaction.** UI never wins over pen latency or canvas stability.
2. **Local-first.** Creating and editing a note never requires an account, network connection, or cloud service.
3. **Lossless by default.** User-created content remains editable and recoverable.
4. **Vector/object based.** Handwriting is data, not a screenshot.
5. **Open format.** Users can export their work; the format is versioned.
6. **Crash-safe.** A process crash must not silently destroy recent work.
7. **Performance is a feature.** Performance budgets are acceptance criteria.
8. **Stable dependencies first.** Experimental APIs are isolated behind adapters.
9. **Modular architecture.** Core document, ink, storage and rendering code never depend on Compose UI.
10. **Future-compatible.** v0.1 provides stable seams for PDF, OCR, semantic search, sync, AI and desktop ports.

## v0.1 scope

- Stylus writing with pressure, pointer/palm differentiation
- Pen, pencil, marker and highlighter brushes
- Deterministic, fast undo/redo
- Fluid pan and zoom on a low-latency canvas
- Notebooks, sections, documents and pages with stable IDs
- Ruled, grid, dotted and blank page templates
- Lasso selection (move / copy / delete)
- Basic text and image objects alongside ink
- Crash-safe journaled persistence with autosave
- PDF / PNG / native document export
- Privacy-first: no mandatory accounts, no mandatory telemetry

Explicit non-goals for v0.1 are listed in [docs/plan.md](docs/plan.md#3-what-v01-will-not-build).

## Technology

Kotlin · Jetpack Compose (Material 3) · AndroidX Ink 1.0 stable · Room/SQLite · Coroutines · Hilt · Coil · WorkManager · GitHub Actions CI.

See the full stack and dependency policy in [docs/plan.md](docs/plan.md#4-recommended-technology-stack).

## Documentation

| Topic | Where |
|---|---|
| Master plan (source of truth) | [docs/plan.md](docs/plan.md) |
| All documentation index | [docs/index.md](docs/index.md) |
| Architecture overview | [docs/architecture/architecture-overview.md](docs/architecture/architecture-overview.md) |
| Ink pipeline | [docs/architecture/ink-pipeline.md](docs/architecture/ink-pipeline.md) |
| Editor state model | [docs/architecture/editor-state-model.md](docs/architecture/editor-state-model.md) |
| Storage & recovery | [docs/architecture/storage-recovery.md](docs/architecture/storage-recovery.md) |
| Performance budget | [docs/architecture/performance.md](docs/architecture/performance.md) |
| Architecture decisions | [docs/architecture/adr/](docs/architecture/adr/) |
| Document format (`.penly`) | [docs/document-format/format-spec.md](docs/document-format/format-spec.md) |
| Migration policy | [docs/document-format/migration-policy.md](docs/document-format/migration-policy.md) |
| CI on GitHub Actions | [docs/ci/ci-github-actions.md](docs/ci/ci-github-actions.md) |
| Contributing | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Privacy model | [docs/privacy/privacy-model.md](docs/privacy/privacy-model.md) |
| Release process | [docs/releases/release-process.md](docs/releases/release-process.md) |

## Building

Penly is a CI-first project. Contributors need only a JDK locally; all real verification happens on GitHub Actions.

- JDK: `17` (see [docs/contributing/getting-started.md](docs/contributing/getting-started.md))
- Local sanity check: `./gradlew compileDebugKotlin`
- Authoritative checks run in CI: `./gradlew check` on a clean machine
- APKs and test results are downloaded as artifacts from workflow runs

## Community

- Report bugs and request features via [GitHub Issues](../../issues)
- Security issues: see [SECURITY.md](SECURITY.md)
- Discuss and contribute: see [CONTRIBUTING.md](CONTRIBUTING.md)
- Code of conduct: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

## License

Apache License 2.0. See [LICENSE](LICENSE).
