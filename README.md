# Scribe

**A considered space for academic thinking.**

Scribe is an offline-first notebook project. The Android application is the mature implementation; a native SwiftUI macOS foundation is now included under `desktop/`. Cross-platform feature parity and cloud sync are still in progress.

## Android capabilities
- Local-first storage with Room; core notes work offline.
- Courses, chapters, topics and multiple ordered pages per topic.
- Content blocks for text, headings, LaTeX source, code, tables, lists, checklists, quotes, questions, flashcards, diagrams, links, images, PDFs and documents.
- Image, PDF and document imports copied into app-private storage.
- Global search, bookmarks, revision filtering, question bank, practice sessions and spaced-repetition flashcards.
- Local study reminders, focus timer and session history.
- Formula pad, engineering unit conversions, graph plotting, formula shelf, circuit sketch starter, scientific tables and concept-map outline.
- On-device OCR for rendered PDF pages.
- Markdown-style formatting controls, soft-delete trash, restore, snapshots, duplicate/move and ordered blocks.
- JSON backup and restore, including app-local attachment bytes and note-version history.
- Page split/merge, paginated PDF export and in-app PDF viewing.
- GitHub Actions release APK build and artifact publishing.

## macOS status
The `desktop/` directory contains an initial native SwiftUI application with a course list and atomic local JSON persistence. It is a buildable foundation only; it does **not** yet provide Android feature parity, PDF annotations, attachments, Drive sync or AI. Build locally with Swift 5.9+ on macOS 13+:

```sh
cd desktop
swift build
swift run
```

A macOS CI workflow builds this target on pushes and pull requests.

## Build Android
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle and run:

```bash
gradle :app:assembleDebug
```

The main-branch GitHub Actions release workflow uses the permanent release keystore supplied through repository Actions secrets when available. Never commit the keystore, passwords, Base64 keystore data or signing credentials. Keep a secure offline backup of the release keystore; future releases must use the same key.

## Cross-platform data and sync
The target portable library format is documented in `docs/sync/library-format.md`, with a versioned JSON Schema at `docs/sync/library-v1.schema.json`. This is currently a contract only, not an implemented sync engine.

The intended Google Drive layout is one visible `My Drive/Scribe/` folder containing a library manifest and attachment files. Two-way sync, conflict preservation, OAuth token storage and attachment integrity verification still need implementation and end-to-end testing. OAuth client JSON files, tokens, API keys and client secrets must never be committed.

## Known Android limitations
Equation content is stored as LaTeX source but is not yet visually typeset. PDF OCR currently works page by page; persistent ink/highlight annotations and full-document indexed OCR remain outstanding. Graph plotting supports a limited set of functions rather than arbitrary expressions. The circuit tool is a component sketch starter, not a full schematic editor or SPICE simulator. Advanced stylus pressure/eraser support, automatic scheduled/cloud backups, and full-fidelity formatted PDF export remain future work. The focus timer does not yet provide background countdown notifications. Audio and video remain out of scope.

## Current implementation status
Do not treat a planned feature, UI placeholder, schema or OAuth client as a working integration. A feature is complete only after implementation, automated tests and relevant device/platform verification.
