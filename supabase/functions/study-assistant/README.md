# Scribe Intelligence deployment

Scribe Intelligence uses the Supabase project already configured in Scribe Cloud Sync. The Android and macOS clients send the question and locally retrieved note excerpts to this function. The OpenAI credential stays in Supabase Edge Function secrets and is never shipped in either app.

## One-time setup

1. Configure Supabase Cloud Sync in Scribe and sign in. The assistant requires a valid signed-in Supabase session.
2. From the repository root, link the Supabase CLI to your project:
   `supabase link --project-ref YOUR_PROJECT_REF`
3. Set the server-side OpenAI secret:
   `supabase secrets set OPENAI_API_KEY=YOUR_OPENAI_API_KEY`
4. Optionally select a model enabled for your OpenAI project:
   `supabase secrets set OPENAI_MODEL=gpt-6-mini`
   The function defaults to `gpt-6-mini` if this secret is omitted.
5. Deploy:
   `supabase functions deploy study-assistant`

The function verifies the bearer token against Supabase Auth before it can call the model. Do not disable JWT verification for this function.

## Modes

- Notes: answer from matching notebook excerpts and identify when they do not contain the answer.
- Explain: teach a topic step by step.
- Exam: generate exam-style questions and model answers.
- Quiz: conduct an interactive question-at-a-time quiz.
- Research: use web search and distinguish external sources from notebook evidence.
- Summary: produce a concise study summary.

## Privacy and limits

- The app retrieves up to 24 relevant note records locally and sends excerpts of at most 12,000 characters per record with the user's request.
- The function does not request persistent response storage.
- AI provider usage is billed to the OpenAI account whose key is configured in Supabase.
- Notes remain in local storage and the user's configured sync provider unless the user asks Scribe Intelligence to use them.
- Large PDFs or image-only attachments need OCR/text extraction before their contents can be retrieved as note context; a filename alone is not treated as file content.
