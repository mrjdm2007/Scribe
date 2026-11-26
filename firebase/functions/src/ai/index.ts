import type { AIProvider } from "./provider";
import { GeminiVertexProvider } from "./gemini";

let cachedProvider: AIProvider | undefined;

export function getAIProvider(): AIProvider {
  if (cachedProvider) return cachedProvider;

  const provider = (process.env.AI_PROVIDER ?? "gemini").toLowerCase();
  switch (provider) {
    case "gemini":
      cachedProvider = new GeminiVertexProvider();
      return cachedProvider;
    default:
      throw new Error(`Unsupported AI_PROVIDER '${provider}'. Add a provider adapter before selecting it.`);
  }
}
