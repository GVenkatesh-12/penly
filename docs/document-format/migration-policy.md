# Migration Policy

How Penly keeps user data safe across format versions, dependency upgrades, and app releases.

Source of truth: [plan.md §47–48, §65](../plan.md#47-migration-strategy).

---

## 1. Principles

1. **Never alter existing stored meaning silently.** Every persisted schema/document version gets an explicit `migration N → N+1`.
2. **A migration test exists for every persisted schema/document version** introduced by any release ([plan §2](../plan.md#2-v01-definition-of-done)).
3. **Dependency upgrades must not define the app's public persisted behavior** ([plan §48](../plan.md#48-upgrade-compatibility)).
4. **Preserve the original file** for documents newer than the current reader: open read-only/limited mode where possible, never silently overwrite unsupported content.

## 2. Document format migrations

The `.penly` format spec lives in [format-spec.md](format-spec.md). Any change to the logical document model is a format version change and requires:

1. A new `formatVersion` in the manifest.
2. A `migration N → N+1` implementation.
3. A migration test fixture (`v1 document`, `v2 document`, ...).
4. Updated compatibility rules for the new version.

### Required migration test flow

```text
old document
 ↓
migrate
 ↓
open
 ↓
edit
 ↓
save
 ↓
reopen
```

## 3. Database migrations (Room)

- Room migrations use the standard `Migration` API; one migration object per version step.
- Never skip versions; chain `N → N+1 → N+2` rather than one giant jump.
- Keep old versions' fixtures so migrations can be tested from every release line.

## 4. Dependency upgrade compatibility

Adapters isolate the persisted format from dependency internals ([ADR-012](ADR-012.md)):

- `InkAdapter`: `InkAdapter v1 → Ink API current` upgrades independently of the document schema.
- AndroidX Ink, PDF engine, image decoding, and storage implementation each have an adapter boundary.

Every persistence-relevant dependency upgrade must run:

- migration tests
- recovery tests
- save/load round-trip tests
- update tests (§6)

## 5. Newer-than-reader documents policy

When a document's `formatVersion` is newer than the current reader supports:

1. **Preserve the original file** — never rewrite or overwrite it.
2. Open in **read-only/limited mode** where possible (render what can be understood).
3. Preserve unknown objects as opaque payloads where practical ([format-spec.md](format-spec.md#3-manifest)).
4. Show a compatibility notice only when editing is impossible; never silently drop content.

## 6. App update testing

For every app release that changes persistence ([plan §65](../plan.md#65-update-and-migration-testing)):

```text
Install old APK
Create document
Populate content
Upgrade APK
Open document
Edit document
Save
Restart
Reopen
Export
```

This should become an **automated upgrade test** where possible. Never assume the next release runs with a completely fresh database.

## 7. Upgrade strategy rules

- Upgrade **one foundation family at a time** when possible ([plan §5](../plan.md#5-dependency-policy)).
- Review release notes before upgrades.
- Use dependency locking/verification where practical.
- Use Renovate/Dependabot only for pull requests — never unattended production upgrades.

## 8. Compatibility test matrix

| Scenario | Must behave |
|---|---|
| vN document opens in vN app | full editability |
| vN document migrates to vN+1 | lossless migration, round-trip verified |
| vN+1 document opened by vN app | preserved read-only/limited, no silent overwrite |
| Corrupt/truncated payload | graceful failure, repair attempt, no crash |
| Missing asset | graceful degradation with notice |
| Unknown object type | preserved as opaque, rest of document intact |

## 9. CI enforcement

- Serialization and migration tests run on **every pull request** ([plan §64](../plan.md#64-ci-pipeline)).
- Nightly runs add large-document and fuzz-corpus migration coverage.
- Release candidates run the full update-test sequence including install-old → upgrade → open → edit → save → reopen → export.