# Scribe for macOS

Native SwiftUI desktop application, targeting macOS 13+ with Swift 5.9+.

## Build

    cd desktop
    swift build
    swift run

## Implemented

- Course → Chapter → Topic → Page → Block hierarchy with ordered local persistence.
- Rich content block types: text, headings, LaTeX source, code, tables, lists, checklists, quotes, links, questions, flashcards and diagrams; Markdown controls and live type-aware previews.
- Search, tags, bookmarks, quick capture, trash/restore/permanent delete, duplicate, reorder, page split/merge, topic cross-links and version snapshots.
- Photo and PDF imports only. Photos are previewed; PDFs use a read-only PDFKit viewer with page navigation and page-by-page Vision OCR. There are no PDF editing or annotation tools, and no audio/video import workflows.
- Page-to-PDF export, without modifying attached PDF files.
- Portable JSON backup/restore using the Android-compatible JSON-array interchange format, including photo/PDF bytes and note history.
- Dimension-aware engineering unit conversion, formula shelf and equation symbol pad, supported-function graph plotter, scientific tables, circuit sketch starter and concept-map starter.
- Flashcard review with Android-compatible due timestamps and Again/Good/Easy scheduling, multiple-choice question bank and practice sessions, local reminders, focus timer and saved session history.

## Still outstanding

- Google Drive two-way sync in the visible My Drive/Scribe/ folder, offline queueing, conflict copies, attachment integrity verification and secure OAuth token storage.
- Note-aware AI with internet research and citations; the client/backend integration is not implemented.
- Successful macOS CI build and complete Android ↔ macOS backup round-trip verification on packaged builds.

Backups are local JSON exports; large attachment collections can create large files. Keep independent copies of important notes.

No OAuth credentials or API keys belong in this source tree. The macOS OAuth client must use the installed-app authorization flow and secure token storage; never ship a confidential client secret.
