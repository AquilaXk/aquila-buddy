import assert from "node:assert/strict";
import { createRequire } from "node:module";
import Module from "node:module";
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const ts = await import(pathToFileURL(join(repoRoot, "front/node_modules/typescript/lib/typescript.js")));

async function importTypeScriptModule(filePath) {
  const source = readFileSync(filePath, "utf8");
  const transpiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  });
  const encodedSource = Buffer.from(transpiled.outputText, "utf8").toString("base64");
  return import(`data:text/javascript;base64,${encodedSource}`);
}

function loadTsCommonJsModule(relativePath, stubs = {}) {
  const fullPath = resolve(repoRoot, relativePath);
  const source = readFileSync(fullPath, "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
    },
    fileName: fullPath,
  }).outputText;

  const mod = new Module(fullPath);
  const localRequire = createRequire(fullPath);
  mod.filename = fullPath;
  mod.paths = Module._nodeModulePaths(dirname(fullPath));
  mod.require = (request) => {
    if (Object.hasOwn(stubs, request)) {
      return stubs[request];
    }
    return localRequire(request);
  };
  mod._compile(compiled, fullPath);
  return mod.exports;
}

describe("1-pass syntax highlighter tokenizer", () => {
  it("tokenizes keywords, strings, comments, numbers without HTML injection", () => {
    const { tokenizeCodeLine, highlightCodeLine } = loadTsCommonJsModule(
      "front/src/lib/highlightCodeLine.ts"
    );

    const tokens = tokenizeCodeLine('const count = 42; // <script>alert("xss")</script>');
    assert.deepEqual(
      tokens.filter((token) => token.kind !== "plain"),
      [
        { kind: "keyword", text: "const" },
        { kind: "number", text: "42" },
        { kind: "comment", text: '// <script>alert("xss")</script>' },
      ]
    );

    const nodes = highlightCodeLine('const x = 1; // safe');
    assert.ok(Array.isArray(nodes));
    assert.equal(nodes[0].type, "span");
    assert.equal(nodes[0].props.children, "const");
    assert.equal(nodes[0].props.dangerouslySetInnerHTML, undefined);

    const strTokens = tokenizeCodeLine('return "escaped \\"quote\\"" + `tmpl`;');
    assert.deepEqual(
      strTokens.filter((token) => token.kind !== "plain"),
      [
        { kind: "keyword", text: "return" },
        { kind: "string", text: '"escaped \\"quote\\""' },
        { kind: "string", text: "`tmpl`" },
      ]
    );
  });

  it("bounds highlighting work on adversarial long lines", () => {
    const { tokenizeCodeLine } = loadTsCommonJsModule(
      "front/src/lib/highlightCodeLine.ts"
    );

    const longLine = `const x = ${"1+".repeat(600)}0;`;
    const tokens = tokenizeCodeLine(longLine);
    assert.equal(tokens.map((token) => token.text).join(""), longLine);
    assert.equal(tokens.at(-1)?.kind, "plain");
  });
});

describe("Safe issue reason metadata parser", () => {
  it("parses repository stars and recent commit date badges cleanly", async () => {
    const { parseIssueReasonMetadata } = await importTypeScriptModule(
      join(repoRoot, "front/src/lib/issueMetadata.ts")
    );

    const parsed = parseIssueReasonMetadata(
      "GitHub GraphQL에서 수집된 미할당 이슈 후보입니다. [스타수: 1.4k, 최근 커밋: 2026-11-20, 라이선스: MIT]"
    );
    assert.equal(parsed.starsLabel, "1.4k");
    assert.equal(parsed.starsCount, 1400);
    assert.equal(parsed.recentCommitLabel, "2026-11-20");
    assert.equal(parsed.licenseLabel, "MIT");
    assert.equal(parsed.cleanReason, "GitHub GraphQL에서 수집된 미할당 이슈 후보입니다.");

    const noAssert = parseIssueReasonMetadata("후보 [스타수: 850, 라이선스: NOASSERTION]");
    assert.equal(noAssert.starsCount, 850);
    assert.equal(noAssert.licenseLabel, null);

    const plain = parseIssueReasonMetadata("TypeScript 기여 이력과 잘 맞습니다.");
    assert.equal(plain.starsLabel, null);
    assert.equal(plain.starsCount, null);
    assert.equal(plain.recentCommitLabel, null);
    assert.equal(plain.licenseLabel, null);
    assert.equal(plain.cleanReason, "TypeScript 기여 이력과 잘 맞습니다.");
  });
});

describe("streamIssueGuide SSE incremental stream and bookmark API", () => {
  it("streams ANALYZING, FILES, GUIDELINE, PSEUDO_CODE, SIDE_EFFECTS, and complete", async () => {
    process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
    const encoder = new TextEncoder();
    const sseChunks = [
      'event: chunk\ndata: {"step":"ANALYZING","message":"분석 시작"}\n\n',
      'event: chunk\ndata: {"step":"FILES","filePaths":["src/a.ts"]}\n\n',
      'event: chunk\ndata: {"step":"GUIDELINE","chunk":"첫 번째 "}\n\n',
      'event: chunk\ndata: {"step":"GUIDELINE","chunk":"가이드"}\n\n',
      'event: chunk\ndata: {"step":"PSEUDO_CODE","chunk":"IF ready THEN run\\n"}\n\n',
      'event: chunk\ndata: {"step":"SIDE_EFFECTS","sideEffects":["회귀 테스트 확인"]}\n\n',
      'event: complete\ndata: {"issueKey":"k1","filePaths":["src/a.ts"],"guideline":"첫 번째 가이드","pseudoCode":"IF ready THEN run","sideEffects":["회귀 테스트 확인"]}\n\n',
    ];

    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      body: new ReadableStream({
        start(controller) {
          for (const chunk of sseChunks) {
            controller.enqueue(encoder.encode(chunk));
          }
          controller.close();
        },
      }),
    });

    const api = loadTsCommonJsModule("front/src/lib/api.ts", {
      "./auth": {
        clearAuthState: () => {},
        getToken: () => null,
        markLoggedIn: () => {},
      },
    });
    const steps = [];
    const result = await api.streamIssueGuide("issue-1", {
      onChunk(ev) {
        steps.push(ev);
      },
    });

    assert.equal(steps.length, 6);
    assert.equal(steps[1].step, "FILES");
    assert.deepEqual(steps[1].filePaths, ["src/a.ts"]);
    assert.equal(steps[4].step, "PSEUDO_CODE");
    assert.equal(result.guideline, "첫 번째 가이드");
  });

  it("fails closed when stream emits an error event without falling back to fake data", async () => {
    process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
    const encoder = new TextEncoder();
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('event: error\ndata: {"message":"AI 생성 한도 초과"}\n\n'));
          controller.close();
        },
      }),
    });

    const api = loadTsCommonJsModule("front/src/lib/api.ts", {
      "./auth": {
        clearAuthState: () => {},
        getToken: () => null,
        markLoggedIn: () => {},
      },
    });
    let capturedError = null;
    await assert.rejects(
      () =>
        api.streamIssueGuide("issue-1", {
          onError(err) {
            capturedError = err;
          },
        }),
      /AI 생성 한도 초과/
    );
    assert.equal(capturedError?.message, "AI 생성 한도 초과");
  });

  it("calls bookmark list, add, and remove endpoints with CSRF/credentials", async () => {
    process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
    globalThis.window = { localStorage: { getItem: () => null } };
    globalThis.document = { cookie: "aquila_buddy_csrf=csrf-tok" };
    const requests = [];
    globalThis.fetch = async (url, init = {}) => {
      requests.push({ url: String(url), method: init.method ?? "GET" });
      if (String(url).endsWith("/api/v1/issues/bookmarks")) {
        return {
          ok: true,
          status: 200,
          json: async () => ({ success: true, status: "success", data: [], message: null }),
        };
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({ success: true, status: "success", data: null, message: null }),
      };
    };

    const api = loadTsCommonJsModule("front/src/lib/api.ts", {
      "./auth": {
        clearAuthState: () => {},
        getToken: () => null,
        markLoggedIn: () => {},
      },
    });
    assert.equal(typeof api.getIssueBookmarks, "function");
    assert.equal(typeof api.bookmarkIssue, "function");
    assert.equal(typeof api.unbookmarkIssue, "function");

    await api.getIssueBookmarks();
    await api.bookmarkIssue("issue-1");
    await api.unbookmarkIssue("issue-1");

    assert.deepEqual(requests, [
      { url: "https://api.example.test/api/v1/issues/bookmarks", method: "GET" },
      { url: "https://api.example.test/api/v1/issues/issue-1/bookmark", method: "POST" },
      { url: "https://api.example.test/api/v1/issues/issue-1/bookmark", method: "DELETE" },
    ]);
  });
});

describe("Split diff pairing helper", () => {
  it("pairs consecutive deletions and additions side-by-side cleanly", async () => {
    const { pairHunkLinesForSplit } = await importTypeScriptModule(
      join(repoRoot, "front/src/lib/parseUnifiedDiff.ts")
    );
    const rows = pairHunkLinesForSplit([
      { type: "normal", oldLine: 1, newLine: 1, content: "const a = 1;" },
      { type: "del", oldLine: 2, newLine: null, content: "const b = 2;" },
      { type: "add", oldLine: null, newLine: 2, content: "const b = 3;" },
      { type: "add", oldLine: null, newLine: 3, content: "const c = 4;" },
      { type: "normal", oldLine: 3, newLine: 4, content: "return a;" },
    ]);

    assert.equal(rows.length, 4);
    assert.equal(rows[0].left?.content, "const a = 1;");
    assert.equal(rows[0].right?.content, "const a = 1;");
    assert.equal(rows[1].left?.content, "const b = 2;");
    assert.equal(rows[1].right?.content, "const b = 3;");
    assert.equal(rows[2].left, null);
    assert.equal(rows[2].right?.content, "const c = 4;");
    assert.equal(rows[3].left?.content, "return a;");
  });
});

describe("1-pass word-level inline diff helper", () => {
  it("isolates changed tokens, ignores whitespace-only edits when toggled, and enforces 512-char bound", async () => {
    const { computeInlineDiffSegments, MAX_INLINE_DIFF_LINE_LENGTH } = await importTypeScriptModule(
      join(repoRoot, "front/src/lib/inlineDiff.ts")
    );

    const seg = computeInlineDiffSegments('const title = "안녕 세계";', 'const title = "반가운 세계";');
    assert.equal(seg?.whitespaceOnly, false);
    assert.equal(seg?.oldSegments.prefix, 'const title = "');
    assert.equal(seg?.oldSegments.changed, "안녕");
    assert.equal(seg?.newSegments.changed, "반가운");
    assert.equal(seg?.newSegments.suffix, ' 세계";');

    const wsOnly = computeInlineDiffSegments("  const x =  1; ", "const x = 1;", true);
    assert.equal(wsOnly?.whitespaceOnly, true);

    assert.equal(computeInlineDiffSegments("a".repeat(MAX_INLINE_DIFF_LINE_LENGTH + 1), "b"), null);
  });
});

describe("Zero-dependency GFM MarkdownPreview parser", () => {
  it("parses headings, task lists, tables, code blocks and neutralizes unsafe link schemes", () => {
    const md = loadTsCommonJsModule("front/src/components/MarkdownPreview.tsx", {
      "@/components/ExternalBrowserLink": { ExternalBrowserLink: () => null },
      "@/lib/highlightCodeLine": { highlightCodeLine: (l) => l },
      "@/lib/utils": { cn: (...xs) => xs.filter(Boolean).join(" ") },
    });

    const parsed = md.parseMarkdownBlocks([
      "## 변경 요약",
      "- [x] 완료 항목",
      "- [ ] 대기 항목",
      "| 컬럼 | 값 |",
      "| --- | --- |",
      "| A | 1 |",
      "```ts",
      "const ok = true;",
      "```",
    ].join("\n"));

    assert.equal(parsed.truncated, false);
    assert.equal(parsed.blocks[0].kind, "heading");
    assert.equal(parsed.blocks[1].kind, "list");
    assert.equal(parsed.blocks[1].items[0].checked, true);
    assert.equal(parsed.blocks[2].kind, "table");
    assert.equal(parsed.blocks[3].kind, "code");

    const safeInline = md.parseInlineMarkdown("[문서](https://example.com) 및 [차단](javascript:alert(1))");
    assert.equal(safeInline.some((t) => t.kind === "link" && t.href === "https://example.com"), true);
    assert.equal(safeInline.some((t) => t.kind === "link" && t.href.startsWith("javascript:")), false);

    const capped = md.parseMarkdownBlocks("a".repeat(md.MAX_MARKDOWN_CHARS + 50));
    assert.equal(capped.truncated, true);
  });
});

describe("Issue star and commit date sorting", () => {
  it("sorts filtered issues by STARS_DESC and RECENT_COMMIT", async () => {
    const { filterAndAggregateIssues } = await importTypeScriptModule(
      join(repoRoot, "front/src/lib/bitapSearch.ts")
    );
    const items = [
      {
        id: "1",
        repoFullName: "org/low",
        issueNumber: 1,
        title: "Alpha",
        labels: [],
        score: 0.9,
        reason: "추천 [스타수: 120, 최근 커밋: 2026-11-18]",
      },
      {
        id: "2",
        repoFullName: "org/mid",
        issueNumber: 2,
        title: "Beta",
        labels: [],
        score: 0.8,
        reason: "추천 [스타수: 2.5k, 최근 커밋: 2026-11-10]",
      },
      {
        id: "3",
        repoFullName: "org/new",
        issueNumber: 3,
        title: "Gamma",
        labels: [],
        score: 0.7,
        reason: "추천 [스타수: 900, 최근 커밋: 2026-11-22]",
      },
    ];

    const byStars = filterAndAggregateIssues(items, "ALL", "ALL", "", "STARS_DESC");
    assert.deepEqual(byStars.filtered.map((x) => x.id), ["2", "3", "1"]);

    const byCommit = filterAndAggregateIssues(items, "ALL", "ALL", "", "RECENT_COMMIT");
    assert.deepEqual(byCommit.filtered.map((x) => x.id), ["3", "1", "2"]);
  });
});
