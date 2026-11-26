# Scribe Firebase backend

This folder contains the Google Cloud backend foundation. See [the migration guide](../docs/google-cloud-migration.md) for project setup, deployment, client migration status and cutover safeguards.

The Functions backend uses Firebase Authentication ID tokens and calls Gemini through Vertex AI using the Cloud Functions service account. It does not accept a client-provided user ID and does not require a Gemini API key in the app.

Do not commit `google-services.json`, `GoogleService-Info.plist`, service-account JSON, API keys or other credentials.
