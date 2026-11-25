# Scribe

**A considered space for academic thinking.**

Scribe is a native, offline-first Android notebook designed around Course → Chapter → Topic → Notebook. It uses a graphite dark interface, warm champagne accents and editorial typography.

## Current capabilities
- Local-first storage with Room; no account or network connection required for core notes.
- Courses, chapters and topics; opening a topic opens a continuous notebook editor with no mandatory page-creation step. Existing legacy pages remain accessible.
- Content blocks for text, headings, LaTeX source, code, tables, lists, checklists, quotes, questions, flashcards, diagrams, links, images, PDFs and documents.
- Image, PDF and document imports copied into app-private storage. Audio and video features are intentionally excluded.
- Global search across titles, note text and tags, plus word search scoped to the open topic.
- Continuous topic notes with portable Markdown-style formatting markers, live local persistence, and orientation-safe navigation restoration.
- Vertical notebook scrolling, horizontal formatting-toolbar scrolling, and horizontal code-block scrolling.
- Bookmarks, revision-tag filtering, a searchable multiple-choice question bank, mixed practice sessions and offline flashcards with Again / Good / Easy spaced-repetition scheduling.
- Local study reminders with reboot rescheduling and automatic archival after notification delivery.
- A foreground focus timer with 15 / 25 / 45 / 60-minute sessions and locally saved session history.
- Tested spaced-repetition intervals: Again resets, Hard advances gently, Good doubles and Easy triples the interval (capped at one year).
- Quick capture notes routed into a visible Quick Notes course and Inbox chapter.
- Formula source pad with symbol keyboard and saved formula collection.
- Engineering unit conversions for length, electrical quantities, frequency, power, energy, time, speed, charge and inductance.
- Local graph plotting for a small built-in set of functions, with the graph expression saved into the library.
- Engineering formula shelf with common electronics equations and a personal saved collection.
- Circuit component sketch starter with reference/value notes and a simple schematic preview.
- Editable scientific tables with adjustable rows and columns, saved as structured tab-separated blocks.
- Concept-map outline starter.
- Text-first engineering tools for equations, formula collections, circuit concepts, graphing, scientific tables and unit conversion.
- On-device OCR for rendered PDF pages, with extracted-text filtering on the current page.
- Markdown-style bold, italic, underline, strikethrough, heading and list controls in the block editor.
- Soft-delete trash, restore, permanent delete, ordered blocks, duplicate/move actions and version snapshots.
- JSON backup and restore, including app-local attachment bytes and saved note-version history.
- Library integrity check for missing attachment files and likely duplicate entries.
- Redesigned adaptive open-notebook launcher icon with warm paper and bookmark accents.
- Duplicate a course, chapter or topic together with its complete nested contents.
- Split a page at its middle content block or merge it with the next page.
- Paginated page-to-PDF export with word wrapping, plus in-app PDF viewing with page navigation.
- GitHub Actions minified, resource-shrunk release APK build and artifact publishing.

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

GitHub Actions publishes a smaller `scribe-release-apk` artifact. It is signed with the temporary debug signing key for testing; create a private release keystore before publishing a Play Store build.

## Known limitations
The app is still evolving. Equation content is stored as LaTeX source but is not yet visually typeset. PDF OCR currently works page by page; persistent ink/highlight annotations and full-document indexed OCR remain outstanding. Graph plotting supports a small, explicitly listed set of functions rather than arbitrary expressions. The circuit tool is a component sketch starter, not a full schematic editor or SPICE simulator. Persistent PDF annotations, full-document indexed OCR, arbitrary-expression graphing, a full schematic editor/SPICE simulator, advanced stylus pressure/eraser support, automatic scheduled/cloud backups, and full-fidelity formatted PDF export remain future work. The focus timer runs only while the app is open; it does not yet provide background countdown notifications. Audio and video remain out of scope.
