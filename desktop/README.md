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
- Note title/body editing, search, bookmarks, trash/restore, duplicate, reorder, page split/merge and version snapshots.
- Attachment import, local attachment storage, image preview, PDFKit preview and PDF text extraction.
- JSON backup/restore including attachment bytes and snapshots.
- Dimension-aware engineering unit conversion, formula shelf, basic graph plotter, scientific table creation, and concept/circuit diagram starters.
- Flashcard review with Again/Hard/Good/Easy scheduling, question-and-answer practice, and a foreground focus timer with local session history.

## Not yet at Android parity
Full rich formatted block editing/rendering parity, study reminders, PDF export, OCR parity, persistent PDF annotations, and complete Android engineering tools are still outstanding. Google Drive sync and the AI client/backend integration are not implemented. The cross-platform JSON schema is a design contract and is not yet used by the Android Room database or a Drive sync engine.

Backups are local JSON exports; large attachment collections can create large backup files. Keep independent copies of important notes.

No OAuth credentials or API keys belong in this source tree. The macOS OAuth client must use the installed-app authorization flow and secure token storage; never ship a confidential client secret.
