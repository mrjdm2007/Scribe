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

Native client integration now present in this branch:

- Android Firebase email/password account creation and sign-in through Firebase Identity Toolkit.
- Android encrypted refresh-token storage, token rotation and six-hour WorkManager library sync.
- macOS Firebase email/password account creation and sign-in, with refresh tokens stored in Keychain.
- macOS six-hour sync while the app is open.
- Shared `studyLibrary` Cloud Function: paginated Firestore pull, stable Firebase item IDs, parent hierarchy, soft-delete state, version history and last-writer-wins updates.
- Shared `studyAttachments` Cloud Function: authenticated Firebase Storage upload/download for attachments up to 20 MB.
- Android and macOS Scribe Intelligence now call the authenticated Firebase `studyAssistant` function; Gemini credentials remain server-side.
- Android learning-tip notifications now request tips from Firebase and require a connected Firebase session.
- Android has a Firebase-powered AI assistant screen; macOS AI assistant uses the same backend.

Still pending before production cutover:

- Set the real Firebase Web API key and project ID in each app's Cloud Sync settings.
- Enable Email/Password Authentication, create Firestore and Storage, enable Vertex AI, configure billing and deploy the functions/rules.
- Migrate any records that exist only in Supabase and verify attachment checksums; Firebase sync uploads the local library but does not import remote-only Supabase data automatically.
- Attachments larger than 20 MB remain local. Firebase App Check registration/enforcement is also still pending.
- Run live Android/macOS two-device tests with the actual Firebase project and inspect deployed function logs.

Keep Supabase available until the live round-trip and remote-data migration checks pass. The Firebase Functions typecheck/build workflow has passed; the native app CI results must be checked against the latest commit before release.

## Native client configuration

Both native apps use Firebase Authentication REST endpoints and authenticated Cloud Functions, so no service-account file or client-side Gemini key is needed.

1. In Firebase Console, open **Project settings → General** and copy the **Project ID** and **Web API key**.
2. Under **Authentication → Sign-in method**, enable **Email/Password**.
3. Create the Firestore database and the default Cloud Storage bucket.
4. Enable Vertex AI API and grant the Cloud Functions runtime service account permission to invoke Vertex AI.
5. Deploy the functions and rules:
   ```sh
   firebase deploy --project <FIREBASE_PROJECT_ID> --only firestore:rules,storage:rules,functions
   ```
6. On Android and macOS, open **Settings → Firebase cloud sync**, enter the Web API key and Project ID, then create an account or sign in with the same Firebase account on both devices.

The function URLs use the `asia-south1` region. The Android library worker syncs periodically while online; macOS syncs periodically while the app is open. Sync remains local-first and does not delete local records when a cloud request fails.

Attachments up to 20 MB are uploaded through the authenticated `studyAttachments` function into Firebase Storage. Larger attachments remain local and should be copied using the full Scribe backup workflow until larger-file support is added.

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

### Persistent AI conversation API

The authenticated `studyConversations` endpoint stores chat history inside the signed-in user's private Firestore namespace:

- `GET /studyConversations` returns conversation summaries (default 30, maximum 50); optional `search=<text>` filters titles, and `archived=true` lists archived conversations.
- `GET /studyConversations?id=<conversation_id>` returns one conversation and its messages.
- `POST /studyConversations` appends one message. Send `message.role` as `user` or `assistant`, `message.content`, and optionally `conversationId`, `mode`, and `activeDocumentId`. Omit `conversationId` to create a new conversation.
- `PATCH /studyConversations?id=<conversation_id>` updates its `title`, `pinned`, `archived` or `activeDocumentId` fields.
- `DELETE /studyConversations?id=<conversation_id>` deletes only that user's conversation.

Each conversation retains the newest 80 messages; message content is limited to 4,000 characters. The endpoint validates the mode and ID, verifies the Firebase ID token, and never accepts a user ID from the request body. The endpoint is implemented in the backend foundation; native Android/macOS clients still need integration before users see synced history in the apps.


### Learning progress, mastery and revision API

The authenticated `studyProgress` endpoint adds five connected learning features:

1. **Study-session history:** `POST` with `action: "session"`, subject, durationMinutes (1–600), and optional topic records a session and updates that day's study-minute/session totals.
2. **Quiz analytics:** `POST` with `action: "quiz"`, topic, correct and total updates cumulative accuracy and topic mastery.
3. **Topic mastery tracking:** quiz results and review ratings update a bounded 0–100 mastery estimate for each topic.
4. **Spaced-repetition queue:** quiz outcomes and `action: "review"` ratings (`again`, `hard`, `good`, `easy`) calculate the next review date and interval.
5. **Study goals:** `POST` with `action: "goal"` and goalType (`daily_minutes`, `weekly_sessions`, or `target_topics`), target and optional label stores an owner-scoped goal.

`GET /studyProgress` returns topic progress, active goals, recent sessions, due reviews and the weakest topics. All records are stored under the authenticated user's Firestore namespace. The endpoint is backend groundwork: Android/macOS dashboard screens and automatic event wiring still need client integration. The mastery score and review intervals are lightweight scheduling heuristics, not a validated educational assessment.


### Study workspace API

The authenticated `studyWorkspace` endpoint adds five further capabilities:

1. **Exam planner:** create, edit and delete exams with subject, date, notes and a bounded syllabus checklist.
2. **Bookmarks:** save document-linked reading positions with excerpts, personal notes and tags; list, edit and delete them.
3. **Study streaks:** dashboard derives consecutive study days from daily session aggregates.
4. **Achievements:** milestone badges are calculated from session count, study minutes, streak length and mastered-topic count.
5. **Android learning-notification preferences:** stores enable/disable, 1–4 hour interval and quiet-hour settings. This is preference storage only; the Android app must still schedule local notifications, and macOS notifications remain out of scope.

Additional workspace tools:
- **Syllabus checklist:** `POST action: "syllabus_item"` with `examId` and `title`; `PATCH/DELETE type=checklist&id=<item_id>` updates completion, title or notes.
- **Focus-session log:** `POST action: "focus_session"` records planned/actual minutes, completion, subject, topic and interruption count. It is separate from study-session totals to avoid double-counting.
- **Notification no-repeat history:** `POST action: "notification_history"` records a topic for the current UTC day and rejects a duplicate topic key with HTTP 409. Android still needs to use this endpoint when selecting/delivering tips; this does not schedule notifications.
- **Bookmark folders:** `POST action: "bookmark_folder"` creates a folder. Bookmarks may be created/updated with `folderId`; `PATCH/DELETE type=folder&id=<folder_id>` renames or deletes a folder.
- **Weekly reflections:** `POST action: "weekly_reflection"` saves wins, challenges and next-week priorities for a YYYY-MM-DD week start.

`GET /studyWorkspace` returns exams, bookmarks, notification preferences, streak/weekly analytics, achievement states, syllabus checklist items, focus-session history, notification history, bookmark folders and recent weekly reflections. `POST` accepts `action: "exam"`, `"bookmark"`, `"notification_preferences"`, `"syllabus_item"`, `"focus_session"`, `"notification_history"`, `"bookmark_folder"` or `"weekly_reflection"`. `PATCH` and `DELETE` accept `type=exam|bookmark|checklist|folder` and an item `id`. All data is scoped to the authenticated Firebase user. The native clients have not yet been wired to these endpoints.

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
