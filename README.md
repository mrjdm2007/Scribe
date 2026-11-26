# Scribe

**A considered space for academic thinking.**

Scribe is a native, offline-first Android notebook designed around Course → Chapter → Topic → Notebook. It uses a graphite dark interface, warm champagne accents and editorial typography.

## Current capabilities
- Local-first storage with Room; no account or network connection required for core notes.
- Courses, chapters and topics; opening a topic opens a continuous notebook editor with no mandatory page-creation step. Existing legacy pages remain accessible.
- Content blocks for text, headings, LaTeX source, code, tables, lists, checklists, quotes, questions, flashcards, diagrams, links, images, PDFs and documents.
- Image, PDF and document imports copied into app-private storage. Android automatically builds an offline searchable text index for imported PDFs using on-device OCR. Audio and video features are intentionally excluded.
- Global search across titles, note text and tags, plus word search scoped to the open topic.
- Continuous topic notes with portable Markdown-style formatting markers, live local persistence, and orientation-safe navigation restoration.
- Adaptive Android layout: portrait uses bottom navigation; wider landscape/tablet layouts switch to a navigation rail and a centered, width-constrained workspace. macOS uses a resizable split-view layout.
- Vertical notebook scrolling, horizontal formatting-toolbar scrolling, and horizontal code-block scrolling; editor and PDF preview areas resize with the available viewport.
- Shared Markdown conventions across Android and macOS for headings, emphasis, strikethrough, code, links, images, nested lists and tables, including table-cell inline formatting and alignment preservation during editing.
- Bookmarks, revision-tag filtering, a searchable multiple-choice question bank, mixed practice sessions and offline flashcards with Again / Good / Easy spaced-repetition scheduling.
- Local study reminders with reboot rescheduling and automatic archival after notification delivery.
- A foreground focus timer with 15 / 25 / 45 / 60-minute sessions, weekly study totals, a 60-minute daily goal, consecutive-day study streaks and recent-session history on Android and macOS.\n- Native Study Workspace screens on Android and macOS for exam planning, editable syllabus checklists, subject-specific focus logs, bookmark folders and weekly reflections. Android additionally exposes learning-notification preferences; AI tip generation and Firebase-backed cross-device workspace sync are not yet connected.
- Tested spaced-repetition intervals: Again resets, Hard advances gently, Good doubles and Easy triples the interval (capped at one year).
- Quick capture notes routed into a visible Quick Notes course and Inbox chapter.
- Formula source pad with symbol keyboard and saved formula collection.
- Engineering unit conversions for length, electrical quantities, frequency, power, energy, time, speed, charge and inductance.
- Local graph plotting with a bounded expression evaluator supporting arithmetic, powers, parentheses, x, constants, and common trigonometric/logarithmic functions; graph expressions are saved into the library.
- Engineering formula shelf with common electronics equations and a personal saved collection.
- Editable scientific tables with adjustable rows and columns on Android and macOS, saved as structured tab-separated blocks.
- Concept-map outline starter.
- Text-first engineering tools for equations, formula collections, graphing, scientific tables and unit conversion.
- Full-document PDF text extraction on macOS (using embedded text where available and on-device Vision OCR for scanned pages); extracted text is saved with the attachment for global search. PDF highlights and note annotations can be embedded in the PDF on macOS.
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
- Rounded Material 3 surfaces, translucent glass panels, fine reflective edge highlights and layered soft shadows; effects remain subtle to preserve text contrast.
- Dark system status/navigation bars to match the application.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle and run:

```bash
gradle :app:assembleDebug
```

GitHub Actions publishes a smaller `scribe-release-apk` artifact. It is signed with the temporary debug signing key for testing; create a private release keystore before publishing a Play Store build.

## Google Cloud backend migration

A Firebase / Google Cloud backend foundation is now available in [firebase/](firebase/README.md), with the staged migration plan in [docs/google-cloud-migration.md](docs/google-cloud-migration.md). It includes owner-scoped Firestore and Storage rules, an authenticated Cloud Functions study-assistant endpoint, Gemini through Vertex AI, per-user AI usage limits and CI typechecking.

**Important:** the Android and macOS clients still use the existing Supabase authentication and sync implementation at this stage. The new Firebase backend is not yet deployed or connected to the clients. Keep the Supabase project and its data until Firebase sign-in, sync, attachments, AI requests and two-device round trips have all been verified. No existing user data or Supabase resources have been removed.

## Legacy Supabase cloud sync (temporary during migration)

**This section describes the currently connected legacy backend only.** Do not create a new Supabase project for a fresh Scribe setup if you are starting the Google Cloud migration. Existing installs continue to use Supabase until Firebase authentication and sync are integrated into both clients and verified. See [the Google Cloud migration guide](docs/google-cloud-migration.md).

1. Create a Supabase project and enable email/password authentication.
2. Run [supabase/schema.sql](supabase/schema.sql) in the Supabase SQL editor.
3. Open Scribe Settings on Android or macOS.
4. Enter the project URL, anon/publishable key, account email and password, then choose **Sync now**.
5. Repeat on the other device using the same Supabase account.

Scribe uses stable cloud IDs to keep Android's Room IDs and macOS's local IDs independent. Sync uses updated-time last-writer-wins merging. Android stores refresh sessions with encrypted preferences and schedules network-constrained WorkManager sync; macOS stores refresh sessions in Keychain and syncs every six hours while the app is open. Passwords are not stored. Attachments up to 8 MB are embedded in record payloads; larger files use the Supabase Storage bucket. The anon key is a public client key; protect user data with the included row-level security policies. A real two-device round-trip still needs to be verified against your own Supabase project.

## Known limitations

- Supabase setup requires the included SQL migration and a Supabase account. Automatic sync is periodic rather than instantaneous: Android uses WorkManager when network is available, while macOS syncs every six hours while the app is open. Version history is merged across devices. Conflict resolution is updated-time last-writer-wins; edits with identical timestamps can still require manual review. Live sync cannot be considered verified until both clients have completed a round-trip against the same configured Supabase project.
- Markdown editing controls and preview are available on both platforms, including tables, nested lists, code, strikethrough and LaTeX. The macOS and Android serializers now share the same conventions for common Markdown structures, but complex documents should still be round-trip tested before relying on cross-platform edits. The Markdown and MathJax renderers are loaded from CDNs; previewing formatted content and equations requires internet access. Source text remains editable offline; the editors are Markdown-source editors rather than full WYSIWYG word processors.
- Android annotations are stored as editable notebook annotation records and exported as native PDF annotation objects with PDFBox; macOS uses PDFKit annotations embedded in the PDF. The source PDF is updated on macOS, while Android writes an annotated export to the chosen destination.
- The graphing tool uses a bounded expression grammar rather than arbitrary programming-language expressions. The circuit tool remains a schematic starter, not a full CAD editor or SPICE simulator. Audio and video remain out of scope.
- The smaller Android release APK is generated by CI and signed with a temporary debug key for testing; create a private release keystore before Play Store distribution.

- macOS PDF imports now automatically extract selectable text and run on-device Vision OCR for scanned pages; extracted text is indexed in the library search. OCR status is retained in attachment tags.
