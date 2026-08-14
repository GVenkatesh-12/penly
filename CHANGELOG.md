# Changelog

All notable changes to Penly are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Repository foundation documentation:
  - Master plan ([docs/plan.md](docs/plan.md))
  - Architecture overview, ink pipeline, editor state model, storage/recovery, and performance docs
  - 12 initial Architecture Decision Records ([docs/architecture/adr/](docs/architecture/adr/))
  - `.penly` document format specification and migration policy
  - GitHub Actions CI guide
  - Contributing, privacy, and release-process documentation

### Changed

- Working name `PaperForge` superseded by **Penly**.

### Fixed

- Nothing yet (no release code).

### Security

- Security reporting policy established ([SECURITY.md](SECURITY.md)).

## [0.1.0-alpha.1] — Not yet released

### Planned scope

Foundational engineering release. See [docs/plan.md](docs/plan.md) for the full
definition of done and [docs/releases/release-process.md](docs/releases/release-process.md)
for the quality gates required before this tag exists.

Key planned capabilities:

- Stylus writing with pressure; pen, pencil, marker, highlighter brushes
- Low-latency canvas with pan/zoom
- Notebooks / sections / documents / pages with stable IDs
- Crash-safe journaled persistence and autosave
- Lasso selection with move/copy/delete
- Blank, ruled, grid, dotted, Cornell page templates
- Basic text and image objects
- PDF / PNG / native document export
- Privacy-first: no accounts, no mandatory telemetry

<!--
Template for future releases:

## [x.y.z] - YYYY-MM-DD

### Added
### Changed
### Deprecated
### Removed
### Fixed
### Security
-->
