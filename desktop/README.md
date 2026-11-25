# Scribe for macOS

Native SwiftUI desktop application, targeting macOS 13+ with Swift 5.9+.

## Build

```sh
cd desktop
swift build
swift run
```

## Implemented in this foundation
- Course → Chapter → Topic → Page → Block hierarchy with ordered local JSON persistence.
- Note title/body editing, search, bookmarks, trash/restore, duplicate, reorder and version snapshots.
- Attachment import, local attachment storage, image preview, PDFKit preview and PDF text extraction.
- JSON backup/restore including attachment bytes and snapshots.
- Engineering unit conversion, formula shelf, basic graph plotter, question/flashcard entry, scientific table creation, and concept/circuit diagram starters.

## Not yet at Android parity
Rich text block types/rendering, full flashcard and question practice/review scheduling, study reminders, focus session history, PDF export, OCR parity, persistent PDF annotations, and complete Android engineering tools are still outstanding. Google Drive sync and AI are not implemented. The cross-platform JSON schema is a design contract and is not yet used by the Android Room database or a Drive sync engine.

Backups are local JSON exports; large attachment collections can create large backup files. Keep independent copies of important notes.

No OAuth credentials or API keys belong in this source tree. The macOS OAuth client must use the installed-app authorization flow and secure token storage; never ship a confidential client secret.
