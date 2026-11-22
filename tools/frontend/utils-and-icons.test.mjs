import assert from "node:assert/strict";
import Module, { createRequire } from "node:module";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const ts = await import(pathToFileURL(join(repoRoot, "front/node_modules/typescript/lib/typescript.js")));
const frontRequire = createRequire(join(repoRoot, "front/package.json"));
const React = frontRequire("react");
const { renderToStaticMarkup } = frontRequire("react-dom/server");

const REQUIRED_ICONS = [
  "AlertCircle",
  "AlertTriangle",
  "ArrowLeft",
  "ArrowUpRight",
  "Bookmark",
  "Check",
  "CheckCircle2",
  "ChevronDown",
  "ChevronRight",
  "CircleDot",
  "Code2",
  "Columns2",
  "Copy",
  "Edit2",
  "ExternalLink",
  "FileCode",
  "FileText",
  "GitCommitHorizontal",
  "GitPullRequest",
  "Globe",
  "HelpCircle",
  "Home",
  "Keyboard",
  "LayoutList",
  "Loader2",
  "LogOut",
  "Menu",
  "PanelLeft",
  "PanelLeftClose",
  "PanelLeftOpen",
  "Plus",
  "RefreshCw",
  "Rows3",
  "Save",
  "Search",
  "Square",
  "Star",
  "Trash2",
  "User",
  "X",
];

const tsModuleCache = new Map();

function loadTsxModule(relativePath) {
  const fullPath = resolve(repoRoot, relativePath);
  if (tsModuleCache.has(fullPath)) {
    return tsModuleCache.get(fullPath);
  }
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
    if (request.startsWith("@/")) {
      const target = join("front/src", `${request.slice(2)}.ts`);
      if (existsSync(resolve(repoRoot, target))) {
        return loadTsxModule(target);
      }
      return loadTsxModule(join("front/src", `${request.slice(2)}.tsx`));
    }
    return localRequire(request);
  };
  mod._compile(compiled, fullPath);
  tsModuleCache.set(fullPath, mod.exports);
  return mod.exports;
}

function collectSourceFiles(directory, result = []) {
  for (const entry of readdirSync(directory)) {
    const fullPath = join(directory, entry);
    if (statSync(fullPath).isDirectory()) {
      collectSourceFiles(fullPath, result);
    } else if (/\.(ts|tsx|css|js|jsx)$/.test(entry)) {
      result.push(fullPath);
    }
  }
  return result;
}

describe("1-pass zero-allocation cn() utility", () => {
  it("concatenates truthy non-empty strings without regex or intermediate array helpers", () => {
    const utilsPath = join(repoRoot, "front/src/lib/utils.ts");
    const utilsSource = readFileSync(utilsPath, "utf8");
    assert.ok(!utilsSource.includes(".filter("), "utils.ts must not allocate intermediate arrays via .filter().");
    assert.ok(!utilsSource.includes(".map("), "utils.ts must not allocate intermediate arrays via .map().");
    assert.ok(!utilsSource.includes(".join("), "utils.ts must not allocate intermediate arrays via .join().");

    const { cn } = loadTsxModule("front/src/lib/utils.ts");
    assert.equal(cn(), "");
    assert.equal(cn("only-class"), "only-class");
    assert.equal(cn("a", "b", "c"), "a b c");
    assert.equal(cn("px-2", false, null, undefined, 0, true, "", "   ", "\t\n", "py-1"), "px-2 py-1");
    assert.equal(cn("  rounded-md  ", "  border "), "rounded-md border");
  });
});

describe("Cleanroom 24x24 SVG icon module", () => {
  it("exports all 40 required icons under 400 lines with zero lucide-react imports", () => {
    const iconsPath = join(repoRoot, "front/src/components/icons.tsx");
    const iconsSource = readFileSync(iconsPath, "utf8");
    const lineCount = iconsSource.split("\n").length;
    assert.ok(lineCount < 400, `icons.tsx must stay under 400 lines (got ${lineCount}).`);

    const srcFiles = collectSourceFiles(join(repoRoot, "front/src"));
    for (const file of srcFiles) {
      const text = readFileSync(file, "utf8");
      assert.ok(!text.includes("lucide-react"), `${file} must not import lucide-react.`);
    }

    const icons = loadTsxModule("front/src/components/icons.tsx");
    assert.equal(REQUIRED_ICONS.length, 40);

    for (const name of REQUIRED_ICONS) {
      const Icon = icons[name];
      assert.equal(typeof Icon, "function", `Missing icon export: ${name}`);

      const defaultMarkup = renderToStaticMarkup(React.createElement(Icon, { className: "h-4 w-4" }));
      assert.ok(defaultMarkup.includes('viewBox="0 0 24 24"'), `${name} missing 24x24 viewBox.`);
      assert.ok(defaultMarkup.includes('aria-hidden="true"'), `${name} must default to aria-hidden="true".`);
      assert.ok(defaultMarkup.includes('stroke="currentColor"'), `${name} must use currentColor stroke.`);

      const labeledMarkup = renderToStaticMarkup(React.createElement(Icon, { "aria-label": "라벨" }));
      assert.ok(!labeledMarkup.includes('aria-hidden="true"'), `${name} must omit aria-hidden when aria-label is set.`);
      assert.ok(labeledMarkup.includes('aria-label="라벨"'), `${name} must preserve aria-label.`);
    }

    const bookmarkMarkup = renderToStaticMarkup(
      React.createElement(icons.Bookmark, { className: "h-3.5 w-3.5 fill-[#0969da]" })
    );
    const starMarkup = renderToStaticMarkup(
      React.createElement(icons.Star, { className: "h-3 w-3 fill-[#0969da]" })
    );
    assert.ok(!/<path\b[^>]*fill="none"/.test(bookmarkMarkup), "Bookmark inner path must not block CSS fill.");
    assert.ok(!/<polygon\b[^>]*fill="none"/.test(starMarkup), "Star inner polygon must not block CSS fill.");

    const loaderMarkup = renderToStaticMarkup(React.createElement(icons.Loader2));
    assert.ok(loaderMarkup.includes("M21 12a9 9 0 1 1-6.22-8.56"), "Loader2 must render open spinning arc.");
  });
});

describe("Cleanroom React 19 UI primitives", () => {
  it("renders Badge, Button, Card, Kbd, and Tabs with required variants and accessibility attributes", () => {
    const { Badge } = loadTsxModule("front/src/components/ui/badge.tsx");
    const { Button } = loadTsxModule("front/src/components/ui/button.tsx");
    const {
      Card,
      CardAction,
      CardContent,
      CardDescription,
      CardFooter,
      CardHeader,
      CardTitle,
    } = loadTsxModule("front/src/components/ui/card.tsx");
    const { Kbd, KbdGroup } = loadTsxModule("front/src/components/ui/kbd.tsx");
    const { Tabs, TabsContent, TabsList, TabsTrigger } = loadTsxModule("front/src/components/ui/tabs.tsx");

    const doneBadge = renderToStaticMarkup(React.createElement(Badge, { variant: "done" }, "PR 초안"));
    assert.ok(doneBadge.includes("bg-[#f1e5ff]"));
    assert.ok(doneBadge.includes("text-[#8250df]"));

    const counterBadge = renderToStaticMarkup(React.createElement(Badge, { variant: "counter" }, "12"));
    assert.ok(counterBadge.includes("px-1.5"));
    assert.ok(counterBadge.includes("tabular-nums"));

    const defaultBtn = renderToStaticMarkup(React.createElement(Button, { variant: "primerGreen", size: "sm" }, "저장"));
    assert.ok(defaultBtn.includes('type="button"'));
    assert.ok(defaultBtn.includes("bg-[#1f883d]"));

    const submitBtn = renderToStaticMarkup(React.createElement(Button, { type: "submit" }, "제출"));
    assert.ok(submitBtn.includes('type="submit"'));

    const cardMarkup = renderToStaticMarkup(
      React.createElement(
        Card,
        null,
        React.createElement(
          CardHeader,
          null,
          React.createElement(CardTitle, null, "제목"),
          React.createElement(CardDescription, null, "설명"),
          React.createElement(CardAction, null, "액션")
        ),
        React.createElement(CardContent, null, "본문"),
        React.createElement(CardFooter, null, "하단")
      )
    );
    assert.ok(cardMarkup.includes("rounded-[6px]"));
    assert.ok(cardMarkup.includes("border-[#d1d9e0]"));

    const kbdMarkup = renderToStaticMarkup(
      React.createElement(KbdGroup, null, React.createElement(Kbd, null, "⌘K"))
    );
    assert.ok(kbdMarkup.includes("<kbd"));
    assert.ok(kbdMarkup.includes("tabular-nums"));

    const tabsMarkup = renderToStaticMarkup(
      React.createElement(
        Tabs,
        { defaultValue: "guide" },
        React.createElement(
          TabsList,
          null,
          React.createElement(TabsTrigger, { value: "guide" }, "수정 가이드"),
          React.createElement(TabsTrigger, { value: "pseudo" }, "의사 코드")
        ),
        React.createElement(TabsContent, { value: "guide" }, "가이드 본문"),
        React.createElement(TabsContent, { value: "pseudo" }, "코드 본문")
      )
    );
    assert.ok(tabsMarkup.includes('role="tablist"'));
    assert.ok(tabsMarkup.includes('role="tab"'));
    assert.ok(tabsMarkup.includes('aria-selected="true"'));
    assert.ok(tabsMarkup.includes('role="tabpanel"'));
  });
});
