# Scribe Firebase backend

This folder contains the Google Cloud backend foundation. See [the migration guide](../docs/google-cloud-migration.md) for project setup, deployment, client migration status and cutover safeguards.

The Functions backend uses Firebase Authentication ID tokens and calls Gemini through Vertex AI using the Cloud Functions service account. It does not accept a client-provided user ID and does not require a Gemini API key in the app.

Do not commit `google-services.json`, `GoogleService-Info.plist`, service-account JSON, API keys or other credentials.

## Native client endpoints

The Android and macOS clients authenticate with Firebase Email/Password through Identity Toolkit REST and store refresh tokens in encrypted preferences / macOS Keychain. They do not require `google-services.json`, `GoogleService-Info.plist`, or a service-account file.

Cloud Functions exposed to the clients:

- `studyAssistant`: authenticated Gemini through Vertex AI; rate-limited per user.
- `studyLibrary`: authenticated, paginated notebook sync in `users/{uid}/library_items`.
- `studyAttachments`: authenticated Firebase Storage upload/download, maximum 20 MB per file.
- `studyConversations`: authenticated conversation history.
- `studyArtifacts`, `studyProgress`, and `studyWorkspace`: structured study artifacts, learning progress and workspace APIs.

Enter the Firebase Web API key and project ID in **Settings → Firebase cloud sync** on both devices. Use the same Firebase account on both. Deploy the backend and rules before attempting to sync. The old Supabase client remains as a migration fallback; do not delete its data before checking for cloud-only records and completing a two-device Firebase round trip.
