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
- Local study reminders, focus timer, saved session history, configurable daily goals, study streaks and weekly study statistics on both platforms.
- Formula pad and searchable formula shelf, with MathJax-rendered equation previews on Android and macOS (source fallback when offline).
- Engineering unit conversion across length, voltage, current, resistance, capacitance, frequency, power, energy, time, speed, charge and inductance.
- Function plotting for supported functions, scientific tables, circuit sketch starter and concept-map outline.
- Page-by-page PDF text extraction/OCR for viewing and searching scanned or image-based pages. This extracted text is never supplied to AI.

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
- A note-aware AI assistant with internet research and citations. Only user-authored notebook text may enter AI context; PDF text/OCR and attachment data are explicitly excluded. The provider/backend and UI are not implemented.
- Successful macOS build/test verification and full Android ↔ macOS round-trip testing on real app builds.

Provider age/account eligibility requirements for AI services must be respected. API keys and OAuth secrets must never be embedded in app binaries or committed to source control.

## Known platform limitations
The focus timer currently runs while the app is open; background countdown notifications and recovery of an interrupted session are not implemented. Graph plotting supports a limited set of functions rather than arbitrary expressions. The circuit tool is a component sketch starter, not a full schematic editor or SPICE simulator.

## Release quality rule
Do not treat a plan, schema, OAuth client or UI placeholder as a working integration. A feature is complete only after implementation, automated tests and relevant device/platform verification.
