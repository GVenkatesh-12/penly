# Penly Document Format Specification (`.penly`)

> **Version 1 (draft)** — this is the compatibility contract for user data. Changes to this document require a new format version and a migration ([migration-policy.md](migration-policy.md)).

Source of truth: [plan.md §8–10](../plan.md#8-document-model).

---

## 1. Overview

Penly documents use a **versioned native package format** with extension `.penly`.

The **logical document specification** (this document) is independent of the physical packaging. The initial physical layout is a directory tree; it may later change to ZIP/container storage **without changing the logical specification**.

## 2. Physical package layout

```text
note.penly/
├── manifest.json
├── document.json
├── pages/
│   ├── page-01.bin
│   └── page-02.bin
├── assets/
│   ├── image-001.webp
│   └── attachment-001.pdf
└── thumbnails/
    ├── page-01.webp
    └── page-02.webp
```

| Path | Purpose |
|---|---|
| `manifest.json` | Format identity and compatibility metadata |
| `document.json` | Logical document metadata and page/object catalog |
| `pages/page-NN.bin` | Per-page payloads (ink/objects), binary, format-versioned |
| `assets/` | Opaque imported assets (images, attachments) |
| `thumbnails/` | Cached page previews (regenerable) |

## 3. Manifest

Conceptual fields:

```json
{
  "format": "penly",
  "formatVersion": 1,
  "minimumReaderVersion": 1,
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-08-14T09:00:00Z",
  "updatedAt": "2026-08-14T09:00:00Z"
}
```

- `format`: constant string identifying the format family.
- `formatVersion`: the version of this spec the document was written with.
- `minimumReaderVersion`: the minimum reader version able to open this document.
- `documentId`: stable UUID ([ADR-009](ADR-009.md)).

### Compatibility rules

Every format version must define:

- minimum reader version
- optional writer capabilities
- migration strategy
- unsupported-object behavior
- integrity checking

### Unknown fields

Readers **ignore unknown optional metadata fields**. This enables forward-compatible metadata additions.

### Unknown object types

A newer object type must **not corrupt the whole document**. The reader should:

1. Preserve unknown objects as opaque payloads where practical.
2. Show a compatibility notice only when editing is impossible.
3. Never silently overwrite unsupported content ([plan §47](../plan.md#47-migration-strategy)).

## 4. Document hierarchy

```text
Workspace
  └── Notebook
       └── Section
            └── Document
                 └── Page
                      ├── InkObject
                      ├── TextObject
                      ├── ImageObject
                      ├── ShapeObject
                      └── EmbeddedObject
```

A `Document` may be a multi-page note. A `Page` is the editable page coordinate space.

## 5. Stable IDs

Every persisted entity receives a stable ID, preferably UUID-based or another collision-resistant identifier ([ADR-009](ADR-009.md)).

**Never use list indexes as persistent identity.**

- `pageId = 550e8400-e29b-41d4-a716-446655440000` ✔
- `pageIndex = 4` ✘

## 6. Object model

Each page object conceptually contains:

```text
ObjectId
PageId
ObjectType
Transform
Bounds
ZIndex
Visibility
CreatedAt
UpdatedAt
Revision
PayloadRef
```

- `ObjectId` — stable ID of the object.
- `PageId` — stable ID of the containing page.
- `ObjectType` — ink / text / image / shape / embedded.
- `Transform` — explicit translation, scale, rotation (see §7).
- `Bounds` — object bounding box in page/world coordinates.
- `ZIndex` — stacking order within the page.
- `Visibility` — visible/hidden.
- `CreatedAt` / `UpdatedAt` — timestamps.
- `Revision` — per-object revision counter (monotonic).
- `PayloadRef` — reference into the page payload or assets.

### Transform

Store transforms explicitly rather than baking movement into raw geometry:

```text
translation
scale
rotation
```

This enables non-destructive manipulation.

## 7. Ink data model

A stroke is not a bitmap ([plan §9](../plan.md#9-ink-data-model)):

```text
InkObject
 ├── StrokeId
 ├── BrushId
 ├── Color
 ├── Size
 ├── Opacity
 ├── Transform
 └── StrokeInputBatch
      ├── x
      ├── y
      ├── timestamp
      ├── pressure
      ├── tilt
      └── orientation
```

Serialization rules:

- Prefer AndroidX Ink's stroke representation and serialization **at the storage boundary**.
- The `.penly` format must **not directly depend on an unstable Ink internal wire representation** — it goes through `InkAdapter` ([ADR-002](ADR-002.md), [plan §9](../plan.md#9-ink-data-model)).

## 8. Brushes

Brushes are configuration-driven and referenced by **stable brush IDs** in saved documents ([plan §18](../plan.md#18-brush-system)):

```text
BrushDefinition
├── id
├── displayName
├── family
├── sizeRange
├── opacityRange
├── pressureResponse
├── tiltResponse
├── taper
├── smoothing
└── textureConfig
```

v0.1 ships with `pen`, `pencil`, `marker`, `highlighter`.

- Brush IDs are stable: saved documents refer to them.
- If a brush is removed in a future version, its old definition must remain decodable or be migrated to a compatibility brush.

## 9. Page payloads

Page payloads (`.bin`) contain the serialized objects of a page:

```text
page payload
├── payloadFormatVersion
├── object list
│   ├── object metadata
│   └── object data (ink batches, text, image refs, ...)
└── integrity block
    ├── size
    ├── checksum
    └── formatVersion
```

Integrity metadata follows [plan §46](../plan.md#46-file-integrity): size + checksum + formatVersion, enabling detection of interrupted writes, missing files, corrupted assets, and incompatible payloads.

## 10. Database mirror (Room)

The `.penly` package is the portable form. On-device, Room mirrors metadata and indexes for fast queries ([ADR-005](ADR-005.md)):

```text
workspaces, notebooks, sections, documents, pages, objects,
assets, tags, document_tags, revisions, journal_entries,
sync_state, settings_index
```

The objects table stores bounding boxes for spatial indexing:

```text
object_id, page_id, object_type, payload_ref, z_index,
min_x, min_y, max_x, max_y, rotation, scale_x, scale_y,
created_at, updated_at, revision
```

## 11. Versioning & compatibility summary

| Concern | Rule |
|---|---|
| Format version | `formatVersion` in manifest; bump on any breaking change |
| Reader support | `minimumReaderVersion`; newer-than-reader documents open read-only/limited, never silently overwritten |
| Unknown metadata | ignored |
| Unknown objects | preserved as opaque payloads; notice only if editing impossible |
| Migrations | every `N → N+1` version has a migration + test ([migration-policy.md](migration-policy.md)) |
| Integrity | size + checksum + formatVersion on payloads |

## 12. Future-proofing

- The spec is written so the physical packaging can become ZIP/container storage later.
- Sync, revision history, and collaboration attach to stable IDs and operation records ([plan §33](../plan.md#33-sync-architecture-preparation)).
- The format must still be openable by the app five years from now ([plan §71](../plan.md#71-final-engineering-principle)).