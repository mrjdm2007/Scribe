export interface GenerateInput {
  systemInstruction: string;
  prompt: string;
  maxOutputTokens: number;
  responseMimeType?: string;
}

export interface AIProvider {
  generate(input: GenerateInput): Promise<string>;
}

export function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Missing required environment setting: ${name}`);
  return value;
}
