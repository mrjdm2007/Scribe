# Scribe portable library format (v1)

Android currently stores its hierarchy as Room rows with numeric IDs and millisecond timestamps. The macOS foundation uses UUIDs and local JSON. This contract defines the cross-platform interchange target; it is **not yet wired into either app's sync path**.

## Required invariants
- `parentId` represents the Course → Chapter → Topic → Page → Block hierarchy.
- `position` is authoritative sibling order. Never infer order from timestamps or filenames.
- Preserve each item's `kind`, `title`, `body`, `uri`, `tags`, bookmark and soft-delete metadata.
- Attachment bytes are stored as separate Drive files; the manifest records relative path, MIME type, byte size and SHA-256.
- A receiver verifies every attachment digest before exposing the imported library.
- Imports are staged and validated before a single database transaction replaces or merges records.
- Unknown future fields should be retained when possible; unknown schema versions must not be silently imported.
- Conflict resolution must preserve both versions until the user chooses. Never silently overwrite newer local content.

## Google Drive layout

The intended visible root is `My Drive/Scribe/` with:
- `library.json` — portable manifest
- `attachments/<attachment-id>` — attachment bytes
- `conflicts/<timestamp>-<device-id>.json` — preserved conflict snapshots

Drive sync is not implemented by this contract. OAuth tokens must be stored using platform secure storage; OAuth client secrets, refresh tokens and API keys must never be committed or embedded in distributable builds.

See `library-v1.schema.json` for the JSON Schema.
