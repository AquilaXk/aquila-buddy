import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import vm from "node:vm";

const repoRoot = resolve(import.meta.dirname, "../..");
const contentScript = readFileSync(resolve(repoRoot, "extension/content.js"), "utf8");

const context = {
  chrome: {
    runtime: {
      onMessage: {
        addListener() {},
      },
      sendMessage() {},
    },
  },
  document: {
    body: {
      appendChild() {},
    },
    addEventListener() {},
    createElement() {
      return {
        addEventListener() {},
        classList: {
          add() {},
          contains() {
            return false;
          },
          toggle() {},
        },
        innerHTML: "",
        textContent: "",
        title: "",
      };
    },
    getElementById() {
      return null;
    },
  },
  location: {
    pathname: "/settings",
  },
  window: {
    addEventListener() {},
  },
};

vm.createContext(context);
vm.runInContext(contentScript, context);

assert.equal(typeof context.renderAnalysisResult, "function");

const html = context.renderAnalysisResult({
  filePaths: ["front/src/app/issues/[id]/page.tsx", "<script>alert(1)</script>"],
  guideline: "Use the selected file.",
  sideEffects: ["Check related UI."],
});

assert.match(html, /수정 대상 파일/);
assert.match(html, /front\/src\/app\/issues\/\[id\]\/page\.tsx/);
assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
assert.doesNotMatch(html, /<script>alert\(1\)<\/script>/);

assert.doesNotMatch(contentScript, /✦/, "content.js must not include decorative sparkle symbol");
assert.doesNotMatch(contentScript, /📄/, "content.js must not include file emoji injection");
assert.doesNotMatch(contentScript, /style="/, "content.js must not include hardcoded inline styles");
assert.doesNotMatch(contentScript, /#0d1117|#161b22|#238636/, "content.js must not use legacy dark theme colors");
assert.match(contentScript, /#ffffff/, "content.js must include Primer primary surface");
assert.match(contentScript, /#f6f8fa/, "content.js must include Primer muted surface");
assert.match(contentScript, /#d1d9e0/, "content.js must include Primer hairline border");
assert.match(contentScript, /#1f2328/, "content.js must include Primer primary ink");
assert.match(contentScript, /#dafbe1/, "content.js must include Primer success muted background");
assert.match(contentScript, /#1a7f37/, "content.js must include Primer success ink");
assert.match(contentScript, /tabular-nums/, "content.js must include tabular-nums");
assert.doesNotMatch(contentScript, /text-transform:\s*uppercase/, "content.js must not use uppercase micro-label cliché");
assert.doesNotMatch(contentScript, /letter-spacing:\s*0\.06em/, "content.js must not use wide uppercase tracking");
assert.match(contentScript, /\.aquila-buddy-issue-card:last-child/, "content.js must style issue cards as flush hairline rows");
assert.match(contentScript, /\.aquila-buddy-result-section:last-child/, "content.js must style result sections as flush hairline rows");

assert.equal(typeof context.renderRecommendationReason, "function");

const bothHtml = context.renderRecommendationReason({
  summary: "온보딩 문구를 정리하는 이슈입니다.",
  reason: "TypeScript 및 프론트엔드 기여 이력과 높은 유사도를 보입니다.",
});
assert.match(bothHtml, /이슈 요약/);
assert.match(bothHtml, /온보딩 문구를 정리하는 이슈입니다\./);
assert.match(bothHtml, /추천 이유/);
assert.match(bothHtml, /TypeScript 및 프론트엔드 기여 이력과 높은 유사도를 보입니다\./);

const reasonOnlyHtml = context.renderRecommendationReason({
  summary: "",
  reason: "Java 21 백엔드 경험과 일치합니다.",
});
assert.doesNotMatch(reasonOnlyHtml, /이슈 요약/);
assert.match(reasonOnlyHtml, /추천 이유/);
assert.match(reasonOnlyHtml, /Java 21 백엔드 경험과 일치합니다\./);

const parsedFork = context.parseCompareBranches("/AquilaXk/aquila-buddy/compare/main...contributor:fix-bug");
assert.equal(parsedFork.baseBranch, "main");
assert.equal(parsedFork.forkOwner, "contributor");
assert.equal(parsedFork.headBranch, "fix-bug");
const parsedThreePartFork = context.parseCompareBranches("/AquilaXk/aquila-buddy/compare/main...contributor:renamed-repo:fix-bug");
assert.equal(parsedThreePartFork.forkOwner, "contributor");
assert.equal(parsedThreePartFork.headBranch, "fix-bug");
assert.match(contentScript, /headBranch:\s*resolvedHeadBranch/);

assert.equal(context.applyDraftToPrForm("PR Title", "PR Body"), false);
assert.equal(context.isEditableEventTarget({ target: { tagName: "TEXTAREA" } }), true);
assert.equal(context.isEditableEventTarget({ target: { tagName: "DIV", isContentEditable: true } }), true);
assert.equal(context.isEditableEventTarget({ target: { tagName: "DIV" } }), false);

const fallbackSummaryHtml = context.renderRecommendationReason({
  summary: "문서 기여로 시작하기 좋습니다.",
});
assert.doesNotMatch(fallbackSummaryHtml, /이슈 요약/);
assert.match(fallbackSummaryHtml, /추천 이유/);
assert.match(fallbackSummaryHtml, /문서 기여로 시작하기 좋습니다\./);

const emptyFallbackHtml = context.renderRecommendationReason({});
assert.match(emptyFallbackHtml, /추천 이유/);
assert.match(emptyFallbackHtml, /추천 사유 정보가 없습니다\./);

assert.equal(context.renderRecommendationReason(null), "");

console.log("extension analysis file paths validation passed");
