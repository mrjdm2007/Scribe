# Scribe Intelligence setup

Scribe Intelligence is backed by the Supabase Edge Function in `supabase/functions/study-assistant`.
The Android and macOS clients send only locally retrieved note excerpts for the current question.
The OpenAI credential stays on the server and is never embedded in either app.

## One-time setup

1. Configure the same Supabase project URL and public anon key in Scribe Settings on both devices.
2. Sign in to Scribe Cloud and complete one successful sync. The clients use the encrypted Android session store / macOS Keychain refresh session to authenticate AI requests.
3. Add the OpenAI API credential as a Supabase Edge Function secret (never commit it):
   ```sh
   supabase secrets set OPENAI_API_KEY=YOUR_SECRET_VALUE
   ```
4. Deploy the function:
   ```sh
   supabase functions deploy study-assistant
   ```

Supabase supplies `SUPABASE_URL` and `SUPABASE_ANON_KEY` to deployed Edge Functions. The function validates the bearer token against Supabase Auth and rejects anonymous requests before calling the model.

## Modes

- `notes`: answer from retrieved notebook context
- `explain`: tutor-style explanation
- `exam`: exam questions and model answers
- `quiz`: interactive quiz generation
- `summary`: concise revision summary
- `research`: Responses API web search with source citations

The Edge Function uses the OpenAI Responses API and the `web_search` tool only in research mode. See the [OpenAI web search guide](https://developers.openai.com/api/docs/guides/tools-web-search?api-mode=responses).

## Privacy and cost

- Requests use `store: false`.
- Note excerpts are sent only when the user submits a question.
- The server secret is not shipped in the APK or macOS app.
- Model and web-search usage may incur OpenAI API charges.
- Configure Supabase Auth rate limits and monitor Edge Function usage before wider distribution.
