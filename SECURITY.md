# Security Policy

Penly is a local-first Android application. Even an offline note app needs basic security discipline — especially because it stores user handwriting, notes, and imported documents.

## Security model

The full security posture is described in [plan.md §52](docs/plan.md#52-security). The essentials:

- **App-private storage by default.** Notebooks, pages, assets, and journals live in the application's private storage; no broad storage permissions are requested.
- **User-selected files only.** Importing/exporting external files goes through Android's Storage Access Framework (SAF) — never through broad file-system permissions.
- **Untrusted input discipline.** Imported documents and packages are treated as untrusted input:
  - all serialized data is validated before use
  - path traversal from archive/package imports is prevented
  - size limits are enforced before decoding imported content
  - embedded content is never executed
- **Credentials.** Future sync credentials must be protected with Android Keystore-backed storage.
- **No telemetry.** There is no mandatory analytics; logging never includes note content. See [privacy-model.md](docs/privacy/privacy-model.md).

## Supported versions

| Version | Supported |
|---|---|
| `0.1.0-alpha.1` (current development line) | Yes — critical issues only |
| Earlier versions | No |

Because the project is in its foundational pre-1.0 phase, security fixes land on the current development line rather than backport branches.

## Reporting a vulnerability

Please **do not** open a public issue for security vulnerabilities.

Report privately to the project maintainers:

- Open a **private advisory** via GitHub: *Repository → Security → Report a vulnerability*, or
- Email the maintainers (address published on the project profile when available).

### What to include

- Affected component(s) and version
- Steps to reproduce (as concise as possible)
- Impact assessment (what an attacker could achieve)
- Any suggested fix, if you have one

### Response commitments

- **Acknowledgment** of the report within 5 business days.
- **Assessment** and a fix plan (or a clear "won't fix / not a vulnerability" rationale with justification) within 15 business days.
- **Coordination** before public disclosure: we will not announce a vulnerability before you have had reasonable time to evaluate the fix.

We operate a disclosure-friendly policy: researchers who report responsibly and follow this process are credited (with permission) in the advisory and changelog.

## Security-relevant areas of the codebase

- `core-storage` / `core-database` — path handling, ContentStore, journal/recovery
- Document/package import and export — validation, path traversal, size limits
- `core-pdf` — untrusted PDF parsing (when introduced)
- Logging and diagnostics — content redaction
- Any future networking/sync — credential handling, transport security

Changes in these areas must not bypass the validation and privacy rules above, and CI must run the full check suite on them.
