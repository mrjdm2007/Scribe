import { GoogleGenAI } from "@google/genai";
import type { AIProvider, GenerateInput } from "./provider";

export class GeminiVertexProvider implements AIProvider {
  private readonly client: GoogleGenAI;
  private readonly model: string;

  constructor() {
    const project = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT;
    if (!project) {
      throw new Error("Cloud Functions did not provide a Google Cloud project ID.");
    }

    this.client = new GoogleGenAI({
      enterprise: true,
      apiVersion: "v1",
      project,
      location: process.env.GOOGLE_CLOUD_LOCATION ?? "asia-south1"
    });
    this.model = process.env.GEMINI_MODEL ?? "gemini-3.8-flash";
  }

  async generate(input: GenerateInput): Promise<string> {
    const response = await this.client.models.generateContent({
      model: this.model,
      contents: input.prompt,
      config: {
        systemInstruction: input.systemInstruction,
        temperature: 0.35,
        maxOutputTokens: input.maxOutputTokens
      }
    });

    const text = response.text?.trim();
    if (!text) throw new Error("Gemini returned an empty response.");
    return text;
  }
}
