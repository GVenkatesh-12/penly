# Privacy Model

Penly is a local-first, privacy-first application. This document states the rules the project commits to.

Source of truth: [plan.md §36–37, §52](../plan.md#36-logging-and-diagnostics).

---

## 1. Philosophy

> The default app should be privacy-first. No mandatory analytics service.

- Penly is fully usable without an account, a network connection, or any telemetry.
- All note content lives on-device in app-private storage ([plan §12](../plan.md#12-file-store), [SECURITY.md](../../SECURITY.md)).
- Privacy is a **default**, not a setting users must hunt for.

## 2. Telemetry policy

- **No mandatory analytics service.**
- Optional anonymous crash reporting may be considered later, but it must be:
  - opt-in **or** clearly disclosed
  - privacy-preserving
  - disabled for note contents
  - removable
  - open about what is collected

Any future telemetry ships behind `core-telemetry`, isolated from the document and ink cores ([plan §7](../plan.md#7-module-structure)), and is documented here before release.

## 3. Logging redaction rules

Never log ([plan §36](../plan.md#36-logging-and-diagnostics)):

- handwriting payloads
- note text
- file contents
- access tokens
- credentials

…unless the user explicitly exports diagnostic data and the export is clearly described.

Levels: TRACE / DEBUG / INFO / WARN / ERROR.

- Debug builds may expose verbose logs.
- Release builds **redact personal/document contents** at the logger boundary.
- User-facing errors show friendly messages; diagnostic details go to logs ([plan §35](../plan.md#35-error-handling)).

## 4. Data practices

| Data | Where it lives | What the app does with it |
|---|---|---|
| Notebooks, pages, ink | app-private storage | never transmitted; exportable by the user (`.penly`, PDF, PNG) |
| Imported assets | app-private storage | never transmitted; validated as untrusted input |
| Preferences | DataStore (on-device) | never transmitted |
| Crash diagnostics (if any, future) | opt-in only | anonymized, no note contents, disclosed |

## 5. Permissions

- No broad storage permissions; user-selected files go through the Storage Access Framework ([plan §52](../plan.md#52-security)).
- No permissions unrelated to the immediate user action ([plan §59](../plan.md#59-recommended-v01-user-journey)).

## 6. Security alignment

- App-private storage by default.
- Imported documents treated as untrusted input (validation, size limits, no path traversal, no execution of embedded content).
- Future sync credentials protected with Android Keystore-backed storage.

See [SECURITY.md](../../SECURITY.md) for the full security policy.

## 7. Accountability

- This document is updated **before** any privacy-relevant feature ships.
- Privacy-relevant PRs (telemetry, networking, permissions) require explicit review of this policy.
- Users must be able to verify the claims here from the source code — it is open source, and the format is documented ([format-spec.md](../document-format/format-spec.md)).