# Scribe for macOS

A native SwiftUI macOS companion for the Scribe Android study notebook. Minimum deployment target: macOS 13. The current build targets Apple Silicon Macs (including M1).

## Build locally

```bash
cd macos
swift test
bash scripts/package-app.sh
open build/Scribe.app
```

The packaging script creates `build/Scribe.app` and `build/Scribe-macOS.zip`.

## Current macOS feature coverage

- Course → Chapter → Topic → Pages navigation.
- Local note editing, bookmark and trash actions.
- Search across titles, note bodies and tags.
- Flashcard review with Again / Hard / Good / Easy scheduling.
- Focus timer with 15 / 25 / 45 / 60-minute presets and saved sessions.
- Engineering formula shelf and electrical unit conversion.
- JSON backup import/export using the Android Scribe backup record shape.
- Native macOS navigation, resizable split view and keyboard shortcuts.

## Parity work still required

This is the first native macOS foundation, not yet a feature-for-feature port. The following Android capabilities still need native macOS implementations and cross-platform tests: PDF rendering and OCR, attachments with embedded payload transfer, page snapshots/history, full question bank and practice sessions, reminders/notification scheduling, graph plotting, circuit workbench, scientific tables, page split/merge/duplicate/move, trash restore/permanent deletion UI, and integrity checks. Rich-text editing and mathematical typesetting also need parity work.

The macOS app stores its own local library. Exchange JSON backups manually between devices; live sync is not implemented. Imported Android file paths are not guaranteed to resolve on macOS. Keep your original backup before importing.
