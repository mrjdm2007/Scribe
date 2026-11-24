# Scribe

**Your knowledge. Beautifully organised.**

Scribe is a native, offline-first Android notebook for organising academic material as Course → Chapter → Topic → Pages → Content blocks.

## Design
- Dark-only graphite interface with warm champagne accents.
- Serif editorial display headings paired with clean sans-serif body text.
- Material 3 components, restrained surfaces and motion.
- No account or network connection required for core note-taking.

## Implemented foundation
- Kotlin, Jetpack Compose, Material 3 and Room.
- Create courses, chapters, topics and multiple pages per topic.
- Ordered content blocks: text, headings, LaTeX-source equations, code, tables, video/URL references and file attachments.
- Import images, PDFs, audio and video; files are copied into app-private storage.
- Autosave edits to the local Room database.
- Global search across titles, note text and tags.
- Bookmarks and a revision view driven by the revision tag.
- Formula library driven by Equation blocks.
- Soft-delete trash with restore and permanent-delete actions.
- Basic block version snapshots and restore.
- JSON backup and restore.
- PDF export of a page and its content blocks.
- Quick capture for standalone notes.
- GitHub Actions debug APK build workflow.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle and run. The command is: gradle :app:assembleDebug.

GitHub Actions publishes the debug APK as the scribe-debug-apk artifact.

## Current implementation notes
- Equation blocks retain LaTeX source; a full visual equation renderer is a future enhancement.
- Attachments are imported locally; richer inline previews and audio recording are future enhancements.
- Link blocks store URL references; an in-app cross-topic picker/navigation experience remains a follow-up.
- Export backups regularly before changing devices or reinstalling.
