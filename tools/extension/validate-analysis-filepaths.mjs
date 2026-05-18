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

console.log("extension analysis file paths validation passed");
