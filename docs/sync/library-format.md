# Scribe portable data and future Drive layout

Scribe currently supports a portable **manual backup interchange** between Android and macOS. The two platforms use different local identifiers (Android numeric IDs, macOS UUIDs), so the importer maps IDs while preserving parent relationships and sibling positions.

## Manual backup interchange

- The interchange file is a JSON array compatible with the Android backup format.
- Each row preserves id, parentId, kind, title, body, uri, tags, bookmarked, position, timestamps and soft-delete state.
- Timestamps are Unix epoch milliseconds.
- Attachment bytes are embedded in attachmentBase64 for portable backup/restore.
- Per-item note snapshots are stored in the history array.
- macOS maps Android numeric IDs to UUIDs on import and maps UUIDs back to portable numeric IDs on export. Parent references and topic cross-links are translated with those mappings.
- Only photos and PDFs are accepted as attachments. Audio and video are not supported.
- Imports validate IDs and parent references before replacing the local library. Attachment bytes are copied into the receiving app's private attachment folder.

This manual backup path is implemented. It is not automatic cloud sync and does not merge two live libraries.

## Required invariants for future sync

- parentId and position preserve hierarchy and exact sibling order.
- Preserve each item's kind, title, body, tags, bookmark and soft-delete metadata.
- Verify attachment size and SHA-256 before exposing a downloaded attachment.
- Stage and validate imports before applying changes.
- Preserve both sides of a conflict until the user chooses; never silently overwrite newer local content.
- Unknown future schema versions must not be silently imported.

## Intended Google Drive layout

The intended visible root is My Drive/Scribe/ with:
- library.json — versioned portable manifest
- attachments/<attachment-id> — photo and PDF bytes
- conflicts/<timestamp>-<device-id>.json — preserved conflict snapshots

Google Drive sync is not implemented yet. OAuth tokens must be stored using platform secure storage; OAuth client secrets, refresh tokens and API keys must never be committed or embedded in distributable builds.

See library-v1.schema.json for the future Drive manifest contract.
