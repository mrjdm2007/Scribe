# Scribe

**A considered space for academic thinking.**

Scribe is a native, offline-first Android notebook designed around Course → Chapter → Topic → Pages → Content blocks. It uses a graphite dark interface, warm champagne accents and editorial typography.

## Current capabilities
- Local-first storage with Room; no account or network connection required for core notes.
- Courses, chapters, topics and multiple ordered pages per topic.
- Content blocks for text, headings, LaTeX source, code, tables, lists, checklists, quotes, questions, flashcards, diagrams, links, images, PDFs and documents.
- Image, PDF and document imports copied into app-private storage. Audio and video features are intentionally excluded.
- Global search across titles, note text and tags.
- Bookmarks, revision-tag filtering, flashcards and an interval-based review queue.
- Formula source pad with symbol keyboard and saved formula collection.
- Engineering unit conversions for length, electrical quantities, frequency, power, energy, time, speed, charge and inductance.
- Local graph plotting for a small built-in set of functions, with the graph expression saved into the library.
- Engineering formula shelf with common electronics equations and a personal saved collection.
- Circuit component sketch starter with reference/value notes and a simple schematic preview.
- Editable scientific tables with adjustable rows and columns, saved as structured tab-separated blocks.
- A searchable question bank with four-option multiple-choice questions, explanations and offline practice sessions with scoring.\n- Local study reminders with notification scheduling and reboot rescheduling (notification permission required on Android 13+).
- Concept-map outline starter.
- Soft-delete trash, restore, permanent delete, ordered blocks, full subtree duplication, move actions and version snapshots.
- JSON backup and restore, including app-local attachment bytes and version-history snapshots (legacy v1 backups remain supported).
- Paginated page-to-PDF export with width-aware word wrapping and attachment references.
- GitHub Actions debug APK build and artifact publishing.

## Design
- Dark-only graphite surfaces with champagne highlights.
- Serif editorial display type paired with readable sans-serif content typography.
- Rounded Material 3 surfaces and restrained contrast.
- Dark system status/navigation bars to match the application.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle and run:

```bash
gradle :app:assembleDebug
```

GitHub Actions publishes a `scribe-debug-apk` artifact.

## Known limitations
The app is still evolving. Equation content is stored as LaTeX source but is not yet visually typeset. PDF import is currently an attachment rather than an in-app annotated PDF reader; OCR, PDF text search and highlights are not yet implemented. Graph plotting currently supports only the explicitly listed built-in expressions and is not a general-purpose expression parser. The diagram tool saves concept-map outlines and circuit component sketches, but is not a freeform circuit editor or SPICE simulator. Handwriting/stylus canvas, full rich-text styling, reminders/calendar, practice tests/question banks, page merge/split, automatic scheduled/cloud backups, and full-fidelity formatted PDF export remain future work. Audio and video remain out of scope.
