import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

const fixtureUrl = new URL("./ai-prompt-evaluation-fixtures.json", import.meta.url);
const fixtureSet = JSON.parse(await readFile(fixtureUrl, "utf8"));
const evidenceFile = process.env.AI_PROMPT_EVAL_EVIDENCE_FILE;

if (process.env.AI_PROMPT_EVAL_ENABLED !== "true") {
  console.log("AI prompt evaluation smoke skipped because AI_PROMPT_EVAL_ENABLED is not true.");
  await writeEvidence("skipped", rowsFor("skipped", "disabled"));
  process.exit(0);
}

const mode = process.env.AI_PROMPT_EVAL_MODE ?? "dry-run";
if (!["dry-run", "live"].includes(mode)) {
  console.error("AI_PROMPT_EVAL_MODE must be dry-run or live.");
  process.exit(1);
}

const provider = process.env.AI_PROVIDER ?? "vertex-ai";
const model = process.env.AI_CHAT_MODEL ?? process.env.VERTEX_AI_MODEL ?? "unknown";

printPlan();

if (mode === "dry-run") {
  const rows = rowsFor("planned", "dry-run");
  await writeEvidence("dry-run", rows);
  console.log(`AI prompt evaluation dry-run completed for provider=${provider} model=${model}`);
  process.exit(0);
}

if (provider !== "openai-compatible") {
  const rows = rowsFor("failed", "live prompt evaluation currently requires openai-compatible provider");
  await writeEvidence("failed", rows);
  console.error("AI prompt evaluation live mode currently requires AI_PROVIDER=openai-compatible.");
  process.exit(2);
}

const missing = ["AI_API_BASE_URL", "AI_CHAT_MODEL"].filter((name) => !process.env[name]?.trim());
if (!process.env.AI_API_KEY?.trim() && !process.env.OPENAI_API_KEY?.trim()) {
  missing.push("AI_API_KEY or OPENAI_API_KEY");
}
if (missing.length > 0) {
  const reason = `missing required env: ${missing.join(", ")}`;
  const rows = rowsFor("failed", reason);
  await writeEvidence("failed", rows);
  console.error(reason);
  process.exit(1);
}

const rows = [];
for (const sample of fixtureSet.samples) {
  if (sample.operation === "parser-fallback" || sample.operation === "provider-failure") {
    rows.push(rowFor(sample, {
      result: "planned",
      parserResult: "mock-only",
      failureReason: "covered by backend fixture regression tests",
    }));
    continue;
  }

  const startedAt = Date.now();
  try {
    const response = await callOpenAiCompatible(sample);
    const latencyMillis = Date.now() - startedAt;
    const content = response.choices?.[0]?.message?.content ?? "";
    const parserResult = evaluateContent(content, sample);
    rows.push(rowFor(sample, {
      result: parserResult.ok ? "pass" : "failed",
      latencyMillis,
      inputTokens: response.usage?.prompt_tokens ?? "",
      outputTokens: response.usage?.completion_tokens ?? "",
      parserResult: parserResult.ok ? "pass" : "failed",
      failureReason: parserResult.reason,
    }));
  } catch (error) {
    rows.push(rowFor(sample, {
      result: "failed",
      latencyMillis: Date.now() - startedAt,
      parserResult: "provider-error",
      failureReason: error.message,
    }));
  }
}

const failed = rows.filter((row) => row.result === "failed");
await writeEvidence(failed.length === 0 ? "pass" : "failed", rows);
if (failed.length > 0) {
  console.error(`AI prompt evaluation failed for ${failed.length} fixture(s).`);
  process.exit(1);
}

console.log(`AI prompt evaluation live checks completed for provider=${provider} model=${model}`);

function printPlan() {
  console.log("AI prompt evaluation path:");
  for (const sample of fixtureSet.samples) {
    console.log(`- ${sample.fixture}: ${sample.operation}`);
  }
}

async function callOpenAiCompatible(sample) {
  const baseUrl = process.env.AI_API_BASE_URL.replace(/\/+$/, "");
  const apiKey = process.env.AI_API_KEY || process.env.OPENAI_API_KEY;
  const timeoutMillis = Number.parseInt(process.env.AI_REQUEST_TIMEOUT_MILLIS ?? "30000", 10);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), Number.isInteger(timeoutMillis) ? timeoutMillis : 30000);
  try {
    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: sample.systemPrompt },
          { role: "user", content: sample.userPrompt },
        ],
        temperature: Number.parseFloat(process.env.AI_TEMPERATURE ?? "0.2"),
      }),
    });
    if (!response.ok) {
      throw new Error(`provider response status ${response.status}`);
    }
    return response.json();
  } finally {
    clearTimeout(timeout);
  }
}

function evaluateContent(content, sample) {
  if (!content.trim()) {
    return { ok: false, reason: "empty provider response" };
  }
  const missing = (sample.requires ?? []).filter((token) => !content.includes(token));
  if (missing.length > 0) {
    return { ok: false, reason: `missing required output token(s): ${missing.join(", ")}` };
  }
  const missingRegex = (sample.requiresRegex ?? []).filter((pattern) => !new RegExp(pattern, "u").test(content));
  if (missingRegex.length > 0) {
    return { ok: false, reason: `missing required output pattern(s): ${missingRegex.join(", ")}` };
  }
  const forbidden = (sample.forbids ?? []).filter((token) => content.includes(token));
  if (forbidden.length > 0) {
    return { ok: false, reason: `forbidden output token(s): ${forbidden.join(", ")}` };
  }
  const forbiddenRegex = (sample.forbidsRegex ?? []).filter((pattern) => new RegExp(pattern, "u").test(content));
  if (forbiddenRegex.length > 0) {
    return { ok: false, reason: `forbidden output pattern(s): ${forbiddenRegex.join(", ")}` };
  }
  return { ok: true, reason: "none" };
}

function rowsFor(result, reason) {
  return fixtureSet.samples.map((sample) => rowFor(sample, {
    result,
    parserResult: result === "planned" ? "planned" : result,
    failureReason: reason,
  }));
}

function rowFor(sample, overrides = {}) {
  return {
    fixture: sample.fixture,
    operation: sample.operation,
    result: overrides.result ?? "planned",
    promptVersion: fixtureSet.promptVersion,
    model,
    latencyMillis: overrides.latencyMillis ?? "",
    inputTokens: overrides.inputTokens ?? "",
    outputTokens: overrides.outputTokens ?? "",
    parserResult: overrides.parserResult ?? "planned",
    failureReason: overrides.failureReason ?? "none",
  };
}

async function writeEvidence(result, rows) {
  if (!evidenceFile) {
    return;
  }
  const lines = [
    "# AI Prompt Evaluation Evidence",
    "",
    `- executed_at: ${new Date().toISOString()}`,
    `- commit: ${process.env.GITHUB_SHA ?? "unknown"}`,
    `- github_run_id: ${process.env.GITHUB_RUN_ID ?? "local"}`,
    `- prompt_version: ${fixtureSet.promptVersion}`,
    `- provider: ${provider}`,
    `- model: ${model}`,
    `- mode: ${mode}`,
    `- result: ${result}`,
    "",
    "## smoke 결과",
    "",
    "| fixture | result | prompt_version | model | latency_ms | input_tokens | output_tokens | parser_result | failure_reason |",
    "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ...rows.map((row) => [
      row.fixture,
      row.result,
      row.promptVersion,
      row.model,
      row.latencyMillis,
      row.inputTokens,
      row.outputTokens,
      row.parserResult,
      sanitize(row.failureReason),
    ].join(" | ")).map((line) => `| ${line} |`),
    "",
  ];
  await mkdir(dirname(evidenceFile), { recursive: true });
  await writeFile(evidenceFile, lines.join("\n"), "utf8");
  console.log(`AI prompt evaluation evidence written to ${evidenceFile}`);
}

function sanitize(value) {
  return String(value ?? "none").replace(/\|/g, "/").replace(/\s+/g, " ").trim();
}
