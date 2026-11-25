# Scribe

**A considered space for academic thinking.**

Scribe is an offline-first notebook for Android and macOS. The macOS app is being brought to functional parity with Android while keeping a desktop-native interface.

## Shared notebook features
- Local-first library with Courses → Chapters → Topics → Pages → ordered content blocks.
- Rich content blocks: text, headings, LaTeX source, code, tables, lists, checklists, quotes, questions, flashcards, diagrams and topic links.
- Photo and PDF attachments only. Photos are stored and previewed; PDFs are stored and previewed in read-only viewers.
- No audio or video import, storage or playback workflows.
- Global search, tags, bookmarks, quick capture, soft-delete trash, restore, duplicate, reorder, page split/merge and version history.
- JSON backup and restore, including attachment bytes and note-version history. The Android JSON-array backup format is supported on macOS, and macOS exports the same portable format for Android import.
- Page-to-PDF export for notes; this does not edit or modify attached PDFs.

## Study and academic tools
- Spaced-repetition flashcards.
- Multiple-choice question bank and local practice sessions with explanations and scoring.
- Local study reminders, focus timer and saved session history.
- Formula pad and searchable formula shelf.
- Engineering unit conversion across length, voltage, current, resistance, capacitance, frequency, power, energy, time, speed, charge and inductance.
- Function plotting for supported functions, scientific tables, circuit sketch starter and concept-map outline.
- Page-by-page PDF text extraction/OCR for scanned or image-based pages.

## macOS implementation
The desktop directory contains a native SwiftUI app with the shared notebook, study and workbench features above. Attachments are restricted to photos and PDFs. PDF viewing is read-only, with page navigation and page OCR; PDF export creates a new PDF from a Scribe note page.

The macOS app has not yet been verified by a successful CI build. Some platform-specific interactions and round-trip cases still need device testing; do not consider the app release-ready until the macOS and Android workflows pass.

## Build macOS

    cd desktop
    swift build
    swift run

Requires Swift 5.9+ and macOS 13+.

## Build Android

Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle and run:

    gradle :app:assembleDebug

The main-branch GitHub Actions release workflow uses the permanent release keystore supplied through repository Actions secrets when available. Never commit the keystore, passwords, Base64 keystore data or signing credentials. Keep a secure offline backup of the release keystore; future releases must use the same key.

## Still outstanding
- Google Drive two-way sync in the single visible My Drive/Scribe/ folder, including offline queueing, attachment integrity checks, conflict preservation and secure OAuth token storage.
- The note-aware AI assistant with web research, citations and text-only PDF extraction. Its client/backend integration is not implemented.
- Successful macOS build/test verification and full Android ↔ macOS round-trip testing on real app builds.

Provider age/account eligibility requirements for AI services must be respected. API keys and OAuth secrets must never be embedded in app binaries or committed to source control.

## Known Android limitations
Equation content is stored as LaTeX source but is not visually typeset. Graph plotting supports a limited set of functions rather than arbitrary expressions. The circuit tool is a component sketch starter, not a full schematic editor or SPICE simulator. The focus timer does not yet provide background countdown notifications.

## Release quality rule
Do not treat a plan, schema, OAuth client or UI placeholder as a working integration. A feature is complete only after implementation, automated tests and relevant device/platform verification.
