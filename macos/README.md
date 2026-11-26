# Scribe for macOS

A native SwiftUI macOS companion for the Scribe Android study notebook. Minimum deployment target: macOS 13. The CI records its runner architecture; confirm the resulting artifact architecture before distributing on Apple Silicon or Intel.

## Build locally

```bash
cd macos
swift test
bash scripts/package-app.sh
open build/Scribe.app
```

The packaging script creates `build/Scribe.app` and `build/Scribe-macOS.zip`.

## Current macOS feature coverage

- Course → Chapter → Topic → direct notebook editing (no page-name step for new notes).
- Local note editing, bookmark and trash actions.
- Search across titles, note bodies and tags, plus word search inside the selected topic.
- Portable Markdown-style formatting markers and resizable note editor.
- Flashcard review with Again / Hard / Good / Easy scheduling.
- Focus timer with 15 / 25 / 45 / 60-minute presets and saved sessions.\n- Native Study Workspace for exam planning, syllabus checklists, focus-session logging, bookmark folders and weekly reflections. Workspace records are currently stored locally on this Mac.
- Engineering formula shelf and electrical unit conversion.
- JSON backup import/export using the Android Scribe backup record shape, including embedded attachment bytes.
- Native macOS navigation, resizable split view, keyboard shortcuts and a custom generated notebook app icon.

## Parity work still required

This is the first native macOS foundation, not yet a feature-for-feature port. The following Android capabilities still need native macOS implementations and cross-platform tests: PDF annotations and searchable OCR indexing, page snapshots/history, full question bank and practice sessions, reminders/notification scheduling, graph plotting, circuit workbench, scientific tables, page split/merge/duplicate/move, trash restore/permanent deletion UI, and integrity checks. Full visual rich-text rendering and mathematical typesetting still need parity work. The notebook stores portable Markdown text so its source formatting and line breaks remain compatible between Android and macOS.

The macOS app stores its own local library. Exchange JSON backups manually between devices; live sync is not implemented. Imported Android file paths are not guaranteed to resolve on macOS. Keep your original backup before importing.
