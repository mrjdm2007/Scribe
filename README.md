# Scribe

**A considered space for academic thinking.**

Scribe is an offline-first notebook project. Android remains the mature implementation. A native SwiftUI macOS app is being expanded toward feature parity; it is not yet a complete replacement for Android.

## Android capabilities
- Local-first Room storage; core notes work offline.
- Courses, chapters, topics and multiple ordered pages per topic.
- Rich content blocks: text, headings, LaTeX source, code, tables, lists, checklists, quotes, questions, flashcards, diagrams, links, images, PDFs and documents.
- Image, PDF and document imports copied into app-private storage.
- Global search, bookmarks, revision filtering, question bank, practice sessions and spaced-repetition flashcards.
- Local study reminders, focus timer and session history.
- Formula pad, engineering unit conversions, graph plotting, formula shelf, circuit sketch starter, scientific tables and concept-map outline.
- On-device OCR for rendered PDF pages.
- Markdown-style formatting controls, soft-delete trash, restore, snapshots, duplicate/move and ordered blocks.
- JSON backup and restore, including app-local attachment bytes and note-version history.
- Page split/merge, paginated PDF export and in-app PDF viewing.

## macOS implementation status
The `desktop/` directory now contains a native SwiftUI app foundation with:
- Ordered Course → Chapter → Topic → Page → Block hierarchy and local persistence.
- Note editing, search, bookmarks, soft-delete trash, restore, duplicate and reorder.
- File/image/PDF attachment import and native PDF viewing with text extraction.
- JSON backup/restore including attachment bytes and note snapshots.
- Dimension-aware engineering unit conversion, formula storage, basic function plotting, scientific table creation, and concept/circuit diagram starters.
- Markdown formatting controls and live formatted preview for desktop note blocks.
- Flashcard review with Again/Hard/Good/Easy scheduling, question-and-answer practice, foreground focus timer with local session history, and daily local study reminders.

This is a meaningful first parity slice, **not full Android parity**. Missing desktop capabilities include rich formatted block editing/rendering, study reminders, PDF export, OCR parity, persistent PDF annotations, full circuit tooling, and several Android-specific workflows. Desktop rich-text editing and rendering also remain simpler than Android. Google Drive sync and the AI client/backend integration are not implemented on either platform.

Build locally with Swift 5.9+ on macOS 13+:

```sh
cd desktop
swift build
swift run
```

A macOS CI workflow is configured, but its latest GitHub Actions runs were cancelled before a runner started. The app must not be considered build-verified until a run completes successfully.

## Build Android
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle and run:

```bash
gradle :app:assembleDebug
```

The main-branch GitHub Actions release workflow uses the permanent release keystore supplied through repository Actions secrets when available. Never commit the keystore, passwords, Base64 keystore data or signing credentials. Keep a secure offline backup of the release keystore; future releases must use the same key.

## Google Drive and AI roadmap
The intended Drive layout is one visible `My Drive/Scribe/` folder containing a versioned library manifest and attachments. Two-way sync, offline queueing, conflict preservation, OAuth token storage and cross-device verification remain unimplemented. OAuth client JSON files, tokens, API keys and client secrets must never be committed.

The intended AI assistant should reference note text and web sources, with citations and privacy controls. PDF handling is text extraction only; image recognition is not required. Provider age and account eligibility requirements must be respected; API keys must never be embedded in app binaries.

## Known Android limitations
Equation content is stored as LaTeX source but is not yet visually typeset. PDF OCR currently works page by page; persistent ink/highlight annotations and full-document indexed OCR remain outstanding. Graph plotting supports a limited set of functions rather than arbitrary expressions. The circuit tool is a component sketch starter, not a full schematic editor or SPICE simulator. Advanced stylus pressure/eraser support, automatic scheduled/cloud backups, and full-fidelity formatted PDF export remain future work. The focus timer does not yet provide background countdown notifications. Audio and video remain out of scope.

## Release quality rule
Do not treat a plan, schema, OAuth client or UI placeholder as a working integration. A feature is complete only after implementation, automated tests and relevant device/platform verification.
