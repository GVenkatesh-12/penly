# Penly Feature-Parity Matrix

> Authoritative benchmark for the master plan (ADR-019). Tracks every feature
> the complete product must ship, mapped to the phase that delivers it.
> Status legend: `planned` → `in-progress` → `done` (with linked verification).
> A release is not "feature-complete" for a tier while rows remain unchecked.

## Tier A — Handwriting (GoodNotes / Samsung Notes / OneNote parity)

| Feature | Phase | Status | Verification / note |
|---|---|---|---|
| Stylus writing with pressure | 1 | done | `core-ink` tests + device matrix |
| Pen / pencil / marker / highlighter | 1 | done | brush tests |
| Eraser (stroke) | 1 | done | gesture tests |
| Eraser (pixel) | 3 | done | pixel-erase tests |
| Lasso selection + move/copy/delete | 3 | done | `editor-selection` tests |
| Palm rejection + stylus button tools | 1 | done | device-only criteria |
| Zoom magnifier | — | planned | — |
| Optional infinite canvas | — | planned | — |
| PDF import + annotation (non-destructive) | 8 | planned | 100-page PDF round-trip |
| Merged PDF export | 8 | planned | lossless merge |
| Handwriting search (on-device OCR) | 9 | planned | "search your handwriting" |
| Ink-to-text conversion | 9 | planned | selected strokes → text |
| Shape recognition (circle/arrow/line) | — | planned | — |
| Audio recording linked to writing position | 12 | planned | position-linked playback |
| Paper templates (ruled/grid/dots/music/engineering/planner) | 5 | planned | config-driven |
| Multi-device palm rejection | 10 | planned | desktop pen-tablet |

## Tier B — Knowledge (Obsidian / Notion / Logseq parity)

| Feature | Phase | Status | Verification / note |
|---|---|---|---|
| Rich typed blocks (headings, lists, checkboxes, code, tables, callouts) | 11 | planned | — |
| Markdown + wiki-links + backlinks | 11 | planned | link graph tests |
| Tags + tag manager | 11 | planned | — |
| Graph view (local + global) | 11 | planned | — |
| Outline + daily notes/journal | 11 | planned | — |
| Templates system | 11 | planned | — |
| Quick switcher (⌘K) + full-text search | 9/11 | planned | FTS5 index |
| Attachments + file management | 11 | planned | — |
| Databases/collections (Notion-style) | — | planned | deferred tier |

## Tier C — Platform (differentiators)

| Feature | Phase | Status | Verification / note |
|---|---|---|---|
| Desktop + Android (KMP core) | 10 | planned | same document both platforms |
| Desktop shell (multi-window, tray, shortcuts) | 10 | planned | — |
| Native dialogs + drag-drop import | 10 | planned | — |
| Plugins: sandboxed QuickJS runtime | 13 | planned | third-party install/uninstall |
| Plugins: `penly-sdk` v1 + compatibility suite | 13 | planned | SDK stability promise |
| Plugins: signed marketplace | 13+ | planned | curated → community |
| AI: on-device OCR/embeddings/handwriting | 15 | planned | opt-in downloads |
| AI: BYOK assistant (OpenRouter) | 15 | planned | user's own key |
| AI: agents (tool-calling over notes) | 15 | planned | NL command → find/summarize/edit |
| Semantic search (hybrid FTS5 + embeddings) | 15 | planned | — |
| Browser: web clipper + reader mode | 12 | planned | clip → note round-trip |
| Browser: live embeds + link previews | 12 | planned | WebView/Chromium/fallback |
| Sync: WebDAV + S3 transports | 14 | planned | two-device convergence |
| Sync: E2EE (XChaCha20-Poly1305) | 14 | planned | server cannot read vault |
| Sync: conflict copies + resolution UI | 14 | planned | — |
| Audio notes with transcription | 16 | planned | recorded lecture searchable |
| Version history / snapshots | 14 | planned | via operation log |
| Backup / restore | 5 | planned | — |
| Import from other note apps | — | planned | OneNote/GoodNotes/PDF |

## Tier D — Future (v5+)

| Feature | Phase | Status | Verification / note |
|---|---|---|---|
| Real-time collaboration (CRDT) | 17 | planned | two live editors, no loss |
| Comments + mentions | 17 | planned | — |
| Share links | 17 | planned | — |

## Explicit non-features (anti-goals)

Full word processor · full browser engine · mandatory account/telemetry ·
cloud-first storage · dozens of brushes · fancy animation-heavy home screens.

## Maintenance rule

Update this matrix in the same commit as the phase work that touches it. A row
is `done` only with a linked verification (test, device criterion, or CI job).