import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const packageJsonPath = join(repoRoot, "front/package.json");
const tailwindConfig = readFileSync(join(repoRoot, "front/tailwind.config.ts"), "utf8");
const globalsCss = readFileSync(join(repoRoot, "front/src/app/globals.css"), "utf8");
const layoutPath = join(repoRoot, "front/src/app/layout.tsx");
const manifestPath = join(repoRoot, "front/src/app/manifest.ts");
const navbarPath = join(repoRoot, "front/src/components/Navbar.tsx");
const protectedLayoutPath = join(repoRoot, "front/src/components/ProtectedLayout.tsx");
const iconSvgPath = join(repoRoot, "front/public/icon.svg");
const apiPath = join(repoRoot, "front/src/lib/api.ts");

const packageJson = JSON.parse(readFileSync(packageJsonPath, "utf8"));
const layoutSource = readFileSync(layoutPath, "utf8");
const manifestSource = existsSync(manifestPath) ? readFileSync(manifestPath, "utf8") : "";
const navbarSource = readFileSync(navbarPath, "utf8");
const protectedLayoutSource = readFileSync(protectedLayoutPath, "utf8");
const iconSvgSource = existsSync(iconSvgPath) ? readFileSync(iconSvgPath, "utf8") : "";
const apiSource = readFileSync(apiPath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(source, needle, message) {
  if (source.includes(needle)) {
    fail(message);
  }
}

function collectFrontendSourceFiles(directory, files = []) {
  for (const entry of readdirSync(directory)) {
    const fullPath = join(directory, entry);
    if (statSync(fullPath).isDirectory()) {
      collectFrontendSourceFiles(fullPath, files);
    } else if (/\.(ts|tsx|css|js|jsx)$/.test(entry)) {
      files.push(fullPath);
    }
  }
  return files;
}

// 0. Zero third-party UI/icon/webfont dependencies
const removedPackages = [
  "@primer/primitives",
  "class-variance-authority",
  "clsx",
  "tailwind-merge",
  "lucide-react",
  "geist",
  "pretendard",
];
const actualDeps = Object.keys(packageJson.dependencies ?? {}).sort();
if (JSON.stringify(actualDeps) !== JSON.stringify(["next", "react", "react-dom"])) {
  fail(`front/package.json dependencies must contain only next, react, react-dom (got: ${actualDeps.join(", ")}).`);
}
for (const pkg of removedPackages) {
  if (packageJson.dependencies?.[pkg] || packageJson.devDependencies?.[pkg]) {
    fail(`Removed dependency ${pkg} must not appear in front/package.json.`);
  }
}
for (const sourceFile of collectFrontendSourceFiles(join(repoRoot, "front/src"))) {
  const sourceText = readFileSync(sourceFile, "utf8");
  for (const pkg of removedPackages) {
    if (
      sourceText.includes(`"${pkg}"`) ||
      sourceText.includes(`'${pkg}'`) ||
      sourceText.includes(`"${pkg}/`) ||
      sourceText.includes(`'${pkg}/`)
    ) {
      fail(`${relative(repoRoot, sourceFile)} must not import removed package ${pkg}.`);
    }
  }
}

// 1. Tailwind config light palette & system font stack tokens
assertIncludes(tailwindConfig.toLowerCase(), "#ffffff", "Tailwind theme must expose #ffffff canvas/surface color.");
assertIncludes(tailwindConfig.toLowerCase(), "#f6f8fa", "Tailwind theme must expose #f6f8fa muted surface color.");
assertIncludes(tailwindConfig.toLowerCase(), "#eff2f5", "Tailwind theme must expose #eff2f5 hover surface color.");
assertIncludes(tailwindConfig.toLowerCase(), "#1f2328", "Tailwind theme must expose #1f2328 primary ink color.");
assertIncludes(tailwindConfig.toLowerCase(), "#59636e", "Tailwind theme must expose #59636e muted ink color.");
assertIncludes(tailwindConfig.toLowerCase(), "#d1d9e0", "Tailwind theme must expose #d1d9e0 hairline border color.");
assertIncludes(tailwindConfig.toLowerCase(), "#0969da", "Tailwind theme must expose #0969da accent color.");
assertIncludes(tailwindConfig.toLowerCase(), "#1f883d", "Tailwind theme must expose #1f883d green emphasis color.");
assertIncludes(tailwindConfig.toLowerCase(), "#dafbe1", "Tailwind theme must expose #dafbe1 success muted color.");
assertIncludes(tailwindConfig.toLowerCase(), "#ddf4ff", "Tailwind theme must expose #ddf4ff accent muted color.");
assertIncludes(tailwindConfig, "var(--font-sans)", "Tailwind sans font stack must reference var(--font-sans).");
assertIncludes(tailwindConfig, "var(--font-mono)", "Tailwind mono font stack must reference var(--font-mono).");
assertNotIncludes(tailwindConfig, "--font-geist-sans", "Tailwind config must remove Geist sans font variable.");
assertNotIncludes(tailwindConfig, "--font-geist-mono", "Tailwind config must remove Geist mono font variable.");
assertNotIncludes(tailwindConfig, "Pretendard Variable", "Tailwind config must remove Pretendard Variable webfont reference.");
assertNotIncludes(tailwindConfig, "github:", "Tailwind config must remove duplicate github-* color namespace.");
assertNotIncludes(tailwindConfig, "dark:", "Tailwind config must remove legacy dark-* color namespace.");
assertNotIncludes(tailwindConfig.toLowerCase(), "#5c6bc0", "Tailwind config must purge old lavender #5C6BC0 token.");
assertNotIncludes(tailwindConfig.toLowerCase(), "#e7eaf4", "Tailwind config must purge legacy #e7eaf4 surface token.");
assertNotIncludes(tailwindConfig.toLowerCase(), "#335095", "Tailwind config must purge legacy #335095 accent token.");

// 2. Global CSS variables & self-hosted system typography + light tokens
assertIncludes(globalsCss, "color-scheme: light;", "Global CSS must declare color-scheme: light;.");
assertIncludes(globalsCss, "--font-sans:", "Global CSS must define --font-sans system font stack.");
assertIncludes(globalsCss, "--font-mono:", "Global CSS must define --font-mono system font stack.");
assertIncludes(globalsCss, "Apple SD Gothic Neo", "Global CSS must include Apple SD Gothic Neo system font.");
assertIncludes(globalsCss, "Malgun Gothic", "Global CSS must include Malgun Gothic system font.");
assertNotIncludes(globalsCss, "--font-geist-sans", "Global CSS must remove Geist sans font variable.");
assertNotIncludes(globalsCss, "--font-geist-mono", "Global CSS must remove Geist mono font variable.");
assertNotIncludes(globalsCss, "Pretendard Variable", "Global CSS must remove Pretendard webfont reference.");
assertIncludes(globalsCss, "--bgColor-default", "Global CSS must define --bgColor-default.");
assertIncludes(globalsCss, "--bgColor-muted", "Global CSS must define --bgColor-muted.");
assertIncludes(globalsCss, "--borderColor-default", "Global CSS must define --borderColor-default.");
assertIncludes(globalsCss, "--fgColor-default", "Global CSS must define --fgColor-default.");
assertIncludes(globalsCss, "--fgColor-muted", "Global CSS must define --fgColor-muted.");
assertIncludes(globalsCss, "--fgColor-accent", "Global CSS must define --fgColor-accent.");
assertIncludes(globalsCss, "--background:", "Global CSS must expose --background variable.");
assertIncludes(globalsCss, "--foreground:", "Global CSS must expose --foreground variable.");
assertIncludes(globalsCss, "--border:", "Global CSS must expose --border variable.");
assertIncludes(globalsCss, "--ring:", "Global CSS must expose --ring variable.");
assertIncludes(globalsCss, ".primer-box", "Global CSS must define .primer-box utility.");
assertIncludes(globalsCss, ".primer-box-header", "Global CSS must define .primer-box-header utility.");
assertIncludes(globalsCss, ".primer-subnav", "Global CSS must define .primer-subnav utility.");
assertIncludes(globalsCss, ".primer-underline-nav", "Global CSS must define .primer-underline-nav utility.");
assertIncludes(globalsCss, ".primer-branch-pill", "Global CSS must define .primer-branch-pill utility.");
assertIncludes(globalsCss, ".btn-primary", "Global CSS must define .btn-primary utility.");
assertIncludes(globalsCss, ".btn-primer-green", "Global CSS must define .btn-primer-green utility.");
assertIncludes(globalsCss, ".btn-secondary", "Global CSS must define .btn-secondary utility.");
assertIncludes(globalsCss, "text-wrap: balance;", "Global CSS must apply text-wrap: balance to headings.");
assertIncludes(globalsCss, "text-wrap: pretty;", "Global CSS must apply text-wrap: pretty to body copy.");
assertIncludes(globalsCss, "font-variant-numeric: tabular-nums;", "Global CSS must define tabular-nums utility.");
assertIncludes(globalsCss, "text-box: trim-both cap alphabetic;", "Global CSS must define .badge-trim optical alignment.");
assertNotIncludes(globalsCss, ".sheet-header", "Global CSS must remove legacy .sheet-header.");
assertNotIncludes(globalsCss, ".glassmorphism", "Global CSS must remove .glassmorphism.");
assertNotIncludes(globalsCss, ".lavender-glow", "Global CSS must remove .lavender-glow.");
assertNotIncludes(globalsCss, ".lavender-glow-sm", "Global CSS must remove .lavender-glow-sm.");

// 3. Dead frontend files & cross-project bank/blog assets must stay deleted
const deletedPaths = [
  "front/src/lib/serviceBrandAssets.ts",
  "front/src/lib/theme.ts",
  "front/public/bank-apple-touch-icon.png",
  "front/public/bank-favicon.ico",
  "front/public/blog-apple-touch-icon.png",
  "front/public/blog-favicon.ico",
  "front/public/brand-mascot-bank.png",
  "front/public/brand-mascot-blog.png",
  "front/src/app/dashboard/DashboardActivityPanel.tsx",
  "front/src/app/dashboard/DashboardWorkflowPanel.tsx",
  "front/src/app/dashboard/dashboardData.ts",
  "front/src/app/dashboard/dashboardWorkflow.ts",
  "front/src/app/issues/[id]/IssueGuideWorkflowPanel.tsx",
  "front/src/app/issues/[id]/IssuePseudoCodePanel.tsx",
  "front/src/app/issues/[id]/issueDetailData.ts",
  "front/src/app/pr/[id]/prDetailData.ts",
  "front/src/app/pr/[id]/PrDraftContributionReportPanel.tsx",
  "front/src/app/pr/[id]/PrDraftEditPanel.tsx",
  "front/src/app/pr/[id]/PrDraftReadinessPanel.tsx",
  "front/src/app/pr/[id]/PrDraftSubmissionPanel.tsx",
  "front/src/app/pr/[id]/PrDraftTimelinePanel.tsx",
  "front/src/app/pr/[id]/TranslationPanel.tsx",
  "front/src/app/pr/[id]/TranslationReadinessPanel.tsx",
  "front/src/app/pr/[id]/TranslationSubmissionBundle.tsx",
  "front/src/app/pr/[id]/TranslationTimelinePanel.tsx",
];

for (const deletedPath of deletedPaths) {
  if (existsSync(join(repoRoot, deletedPath))) {
    fail(`Dead or cross-project file must be deleted: ${deletedPath}`);
  }
}

// 4. Dead API functions must stay removed from front/src/lib/api.ts
const deletedApiFunctions = [
  "getUserProfileByLogin",
  "getPullRequestDrafts",
  "getPullRequestDraftSubmission",
  "getPullRequestDraftTimeline",
  "getPullRequestDraftContributionReport",
  "createPullRequestDraftTranslation",
  "getPullRequestDraftTranslationActivities",
  "getPullRequestDraftTranslationReadiness",
  "getPullRequestDraftTranslationTimeline",
  "getPullRequestDraftTranslationSubmission",
  "getCollectedIssues",
  "getIssueGuideWorkflow",
];

for (const fnName of deletedApiFunctions) {
  assertNotIncludes(apiSource, `function ${fnName}`, `Dead API function ${fnName} must be removed from front/src/lib/api.ts.`);
}

// 5. Required brand assets, cleanroom UI primitives, icons & shared modules
for (const requiredPath of [
  "front/public/icon.svg",
  "front/public/favicon.ico",
  "front/public/apple-touch-icon.png",
  "front/public/images/default-profile.svg",
  "front/src/lib/format.ts",
  "front/src/lib/utils.ts",
  "front/src/components/icons.tsx",
  "front/src/components/StatusBadge.tsx",
  "front/src/components/ui/button.tsx",
  "front/src/components/ui/badge.tsx",
  "front/src/components/ui/card.tsx",
  "front/src/components/ui/kbd.tsx",
  "front/src/components/ui/tabs.tsx",
]) {
  if (!existsSync(join(repoRoot, requiredPath))) {
    fail(`Missing required asset or shared module: ${requiredPath}`);
  }
}
assertIncludes(iconSvgSource.toLowerCase(), "#1f2328", "front/public/icon.svg must use #1f2328 ink badge.");

// 6. Layout & Metadata
assertIncludes(layoutSource, 'metadataBase: new URL("https://buddy.aquilaxk.site")', "Metadata must set https://buddy.aquilaxk.site as metadataBase.");
assertIncludes(
  layoutSource,
  'description: "GitHub 오픈소스 이슈 추천, 코드 맥락 분석 및 PR 초안 작성을 지원하는 기여 워크스페이스"',
  "Metadata must use Korean workspace description without tautology."
);
assertIncludes(layoutSource, "/icon.svg", "Layout metadata must reference /icon.svg.");
assertIncludes(layoutSource, 'data-color-mode="light"', "Root html element must enable light color mode.");
assertIncludes(layoutSource, 'data-light-theme="light"', "Root html element must enable light theme.");
assertIncludes(layoutSource, '"./globals.css"', "Root layout must load ./globals.css.");
for (const bannedModule of [
  "geist/font",
  "GeistSans.variable",
  "GeistMono.variable",
  "pretendard/dist/",
  "@primer/primitives/dist/",
  "lucide-react",
  "class-variance-authority",
  "clsx",
  "tailwind-merge",
]) {
  assertNotIncludes(layoutSource, bannedModule, `Root layout must not reference removed module ${bannedModule}.`);
}
assertNotIncludes(layoutSource, 'className="dark"', "Root html element must not force dark mode.");
assertNotIncludes(layoutSource, "serviceBrandAssets", "Layout must not reference cross-project serviceBrandAssets.");

// 7. Web manifest
assertIncludes(manifestSource, "/icon.svg", "Manifest must reference /icon.svg.");
assertIncludes(manifestSource.toLowerCase(), 'background_color: "#ffffff"', "Manifest must use #ffffff background_color.");
assertNotIncludes(manifestSource, "serviceBrandAssets", "Manifest must not reference cross-project serviceBrandAssets.");

// 8. Navbar
assertIncludes(navbarSource, 'aria-label="Primary"', "Navbar must expose a primary navigation label.");
assertIncludes(navbarSource, "<svg", "Navbar must use a clean inline geometric SVG mark.");
assertIncludes(navbarSource, "Aquila Buddy", "Navbar must display Aquila Buddy typographic lockup.");
assertNotIncludes(navbarSource, "brand-mascot-bank.png", "Navbar must not reference bank mascot asset.");
assertNotIncludes(navbarSource, "github-", "Navbar must not use duplicate github-* color classes.");

// 9. Protected layout
assertIncludes(protectedLayoutSource, "max-w-[1280px]", "Protected shell must keep the 1280px inset content width.");
assertIncludes(protectedLayoutSource, "<Navbar />", "Protected shell must keep the navigation structure.");
assertIncludes(protectedLayoutSource, "<SiteHeader />", "Protected shell must render sticky SiteHeader.");
assertNotIncludes(protectedLayoutSource, "github-", "ProtectedLayout must not use duplicate github-* color classes.");

if (failures.length > 0) {
  console.error("Brand parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Brand parity validation passed.");
