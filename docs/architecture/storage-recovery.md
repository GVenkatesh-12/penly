# Storage & Recovery Model

How Penly persists documents without losing user work — the crash-safety heart of the application.

Source of truth: [plan.md §11–13, §44, §46](../plan.md#11-database-schema).

---

## 1. Two-layer storage: Room + file store

Room stores **metadata, indexes and transactional state**, not every large binary payload directly ([plan §11](../plan.md#11-database-schema)).

```text
┌───────────────────────────────┐
│         Storage Core          │
│          Repositories         │
├───────────────┬───────────────┤
│ SQLite (Room) │  File Store   │
│ metadata/     │  large/opaque │
│ indexes/      │  payloads:    │
│ transactional │  pages,       │
│ state         │  assets,      │
│               │  thumbnails,  │
│               │  journal,     │
│               │  recovery     │
└───────────────┴───────────────┘
```

## 2. Suggested database tables

```text
workspaces
notebooks
sections
documents
pages
objects
assets
tags
document_tags
revisions
journal_entries
sync_state
settings_index
```

### Core relations

```text
workspace
  1 ─── N notebook
        1 ─── N section
              1 ─── N document
                    1 ─── N page
                          1 ─── N object
```

### Objects table

Lightweight metadata only ([plan §11](../plan.md#11-database-schema)):

```text
object_id
page_id
object_type
payload_ref
z_index
min_x
min_y
max_x
max_y
rotation
scale_x
scale_y
created_at
updated_at
revision
```

Bounding boxes are stored to enable future spatial indexing without decoding every stroke.

## 3. File store (ContentStore)

An application-private content store ([plan §12](../plan.md#12-file-store)):

```text
/files/
  /documents/
  /pages/
  /assets/
  /thumbnails/
  /exports/
  /journal/
  /recovery/
```

**Never construct file paths directly throughout the codebase.** Use the `ContentStore` abstraction:

```text
put()
open()
move()
delete()
exists()
checksum()
```

This permits future migration to another storage backend.

## 4. Crash-safe write pipeline

A handwriting app must assume crashes can happen at any point ([plan §13](../plan.md#13-crash-safe-write-pipeline)).

```text
Stylus event
    ↓
In-memory editor state
    ↓
Command
    ↓
Journal append
    ↓
Async durable commit
    ↓
Document state
```

**The UI must never wait for disk I/O for each input point.**

### Save strategy

During a stroke:

- Keep active stroke in memory.
- Render immediately.
- Do not write one database row per point.

On stroke completion:

1. Convert to immutable stroke data.
2. Append one logical command/journal record.
3. Persist asynchronously.
4. Update page revision.
5. Schedule thumbnail regeneration if necessary.

### Recovery

At startup:

```text
open document
    ↓
check journal
    ↓
replay incomplete operations
    ↓
validate content references
    ↓
repair if possible
    ↓
open document
```

**Never delete the journal before the corresponding durable state is known to be valid.**

### Journal implementation (Phase 4)

Implemented in `core:core-document` (`PenlyStore` + `JournalCommit`):

1. `save()` stages page and index copies under `<docId>/journal/`, then writes
   `commit.json` (the commit point: document id, timestamps, staged file list).
2. Main files are then written with atomic `put` (temp sibling + fsync +
   atomic rename, see below), followed by the manifest (which includes asset
   checksums).
3. The journal is deleted only after the manifest is durable.

On `load()`:

- If the journal exists and `commit.json` decodes and every listed staged copy
  exists with a matching checksum, the staged state is replayed and
  `LoadResult.Success.recovered` is `true` — the UI shows a "Recovered unsaved
  changes" banner.
- If the journal is missing, corrupt, or incomplete, it is ignored and the
  last committed state is loaded.
- Assets live in the manifest only (never the journal): a corrupt or missing
  asset degrades to a warning instead of blocking document recovery.

### Atomic writes

`FileContentStore.put` and `move` write to a temporary sibling file, fsync the
file, atomically rename over the target (with fallback for non-atomic
filesystems), and best-effort fsync the directory.

## 5. Save semantics

The user should never wonder whether a note is saved ([plan §44](../plan.md#44-save-semantics)). UI state may show:

```text
Saved
Saving…
Recovered
```

But the default is **silent autosave**:

- A completed stroke becomes durable asynchronously.
- On app termination/backgrounding: flush pending durable work, persist an editor checkpoint when necessary, and maintain recovery data if a clean flush is impossible.

## 6. File integrity

Large document payloads carry integrity metadata ([plan §46](../plan.md#46-file-integrity)):

```text
size
checksum
formatVersion
```

Goals — detection of:

- interrupted writes
- missing files
- corrupted assets
- incompatible payloads

Integrity checks are cheap enough for routine validation; deeper checks run in maintenance tasks (WorkManager, see [plan §4](../plan.md#4-recommended-technology-stack)).

## 7. Failure scenarios the storage layer must survive

Explicit test list ([plan §66](../plan.md#66-failure-scenarios-to-test-explicitly)):

- app killed during stroke
- app killed during save
- device restarted immediately after writing
- storage nearly full / completely full
- malformed native document
- missing asset / corrupt asset
- old document version / future unknown version
- screen rotated during writing
- app backgrounded during writing
- process recreated
- low-memory process kill
- very long stroke
- rapid undo/redo

## 8. Testing the storage layer

- Room + file store integration tests
- Journal replay tests
- Document save/load round-trips
- Migration tests for every persisted schema/document version ([migration-policy.md](../document-format/migration-policy.md))
- Fault injection: automated tests simulating common crashes/power-loss must demonstrate that committed content is never lost ([plan §55, Phase 4](../plan.md#phase-4--crash-safety))
- `PenlyStoreCrashSafetyTest` (Phase 4): a `CrashInjectingStore` kills the store after every save mutation (`crashAfter` sweep) and after each journal stage, verifying replay, state survival, and that `put`/`move` never leave temporary files behind.

See [testing-guide.md](../contributing/testing-guide.md) for the full matrix.