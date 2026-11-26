# Scribe Google Cloud migration

## Migration strategy

Scribe is being moved to Firebase and Google Cloud in stages. This is intentionally additive: the existing Supabase backend remains available until both native clients have been migrated, real data has been copied and verified, and Android/macOS round-trip tests pass.

The new backend foundation lives in `firebase/`:

- Firebase Authentication is the identity provider. Every backend request must carry a Firebase ID token.
- Cloud Firestore is the planned home for user-owned structured data.
- Cloud Storage for Firebase is the planned home for uploaded PDFs and images.
- Cloud Functions for Firebase (2nd gen) hosts the AI endpoint.
- Gemini is called through the Google Gen AI SDK using Vertex AI and the function's Google service account. No Gemini API key is placed in either app.
- The AI provider is behind a small interface so another server-side provider can be added without changing the Android or macOS request contract.

## Current implementation status

Implemented in this branch:
- Structured JSON response mode for AI-generated flashcards, mind maps and hourly learning tips. The function validates and bounds generated fields before returning them; raw answer text remains present for compatibility.
- Generated structured artifacts are automatically saved under the signed-in user's `users/{uid}/study_artifacts` Firestore collection.
- `studyArtifacts` HTTPS endpoint lists the latest 50 saved artifacts and allows an authenticated owner to delete an artifact by ID.
- The AI response includes an `artifact_id` when a structured artifact is persisted.

- Firebase project configuration and owner-scoped Firestore/Storage rules.
- Node.js 22 Cloud Functions project.
- Authenticated `studyAssistant` HTTPS function. It verifies Firebase ID tokens, validates study modes and input sizes, caps note context, and enforces a per-user daily request limit.
- Gemini Vertex AI provider adapter using Google Cloud service identity (Application Default Credentials).
- Provider interface and environment-based provider selection.
- Firebase Functions typecheck workflow.

Not yet migrated:

- Android Supabase email/password authentication and encrypted refresh-session handling.
- Android Supabase PostgREST/Storage sync implementation.
- macOS Supabase authentication, Keychain session and cloud sync.
- macOS AI assistant client request path.
- Import of existing Supabase users, notes, snapshots, tombstones and attachments.
- Firebase App Check registration and enforcement.
- Live Android/macOS two-device sync tests.

Do not remove the Supabase project, SQL schema, Edge Function, or secrets while these items remain. The current app builds still use the legacy backend.

## Google Cloud project setup

1. Create or choose one Google Cloud project for Scribe.
2. Add a Firebase project to that same Google Cloud project.
3. Enable Firebase Authentication and choose the sign-in providers to use. Email/password is the simplest initial migration path; Google sign-in can be added after both native clients are registered.
4. Create the default Cloud Firestore database in a location you can keep long-term. Choose the location deliberately because Firestore location is not easily changed later.
5. Enable Cloud Storage for Firebase.
6. Enable the Vertex AI API in Google Cloud.
7. Enable billing for Cloud Functions deployment. Cloud Functions for Firebase requires the Blaze pay-as-you-go plan. Set a small budget alert before deploying.
8. Ensure the Cloud Functions runtime service account can invoke Vertex AI and access Firestore/Storage through the required IAM roles. Prefer the narrowest roles needed.
9. Register the Android and macOS apps in Firebase. Keep each platform's Firebase configuration private to its build/configuration process; never commit service-account JSON files or private keys.

## Configure and deploy the AI function

From the repository root:

```sh
cp firebase/functions/.env.example firebase/functions/.env.<FIREBASE_PROJECT_ID>
```

Set the project and location in the environment file. The function uses the Cloud Functions service identity to authenticate to Vertex AI; do not add a Gemini API key.

Install and build:

```sh
npm --prefix firebase/functions install
npm --prefix firebase/functions run typecheck
npm --prefix firebase/functions run build
```

Select the Firebase project and deploy:

```sh
firebase login
firebase use --add
firebase deploy --only firestore:rules,storage,functions
```

The deployed endpoint is:

```
https://asia-south1-<FIREBASE_PROJECT_ID>.cloudfunctions.net/studyAssistant
```

### Saved study artifact API

The `studyArtifacts` endpoint accepts the same Firebase ID-token bearer authentication:

- `GET /studyArtifacts` returns the latest 50 saved artifacts for the signed-in user.
- `DELETE /studyArtifacts?id=<artifact_id>` deletes only that user's artifact.

The Firestore rules also permit the authenticated owner to read and delete their own `users/{uid}/study_artifacts` documents directly. These endpoints are ready for client integration; the Android and macOS UIs do not call them yet.

Clients must send:

```
Authorization: Bearer <FIREBASE_ID_TOKEN>
Content-Type: application/json
```

The request body retains the existing Scribe study-assistant contract:

```json
{
  "question": "Explain this topic",
  "mode": "explain",
  "note_context": [
    { "title": "Topic", "path": "Course / Chapter", "content": "..." }
  ]
}
```

The response retains the existing `{ "answer": "...", "citations": [], "response_id": "..." }` shape so the UI can be migrated without redesigning its study-mode experience.

## Cost and abuse controls

- The AI endpoint accepts only Firebase-authenticated requests.
- The default limit is 40 AI requests per user per UTC day; set `SCRIBE_AI_DAILY_LIMIT` to a lower value if desired.
- Requests are throttled to at least 1.5 seconds apart per user.
- Cloud Functions is capped at three instances in this initial configuration.
- Note context and question lengths are bounded before model invocation.
- Add Google Cloud budget alerts and monitor Vertex AI usage before inviting more users.
- Firebase App Check should be enabled and enforced after Android and macOS clients are registered and have App Check providers configured.

## Data migration and cutover

The migration must preserve stable `cloud_id` values and parent references. Export the Supabase tables and Storage objects first; import into a staging Firebase project or staging collections; verify item counts, parent relationships, deleted records, version history and attachment checksums; then test both clients with the same Firebase account.

Only after a successful two-device round trip should the app's settings stop offering Supabase and the old Supabase resources be retired. Keep a verified export backup before cutover.

## Rollback

Until cutover is explicitly completed, the current Supabase implementation remains the rollback path. A failed Firebase login, sync or AI request must not delete or overwrite local Room/Core Data records. Keep local backups before running any import or migration.
