import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const tailwindConfig = readFileSync(join(repoRoot, "front/tailwind.config.ts"), "utf8");
const globalsCss = readFileSync(join(repoRoot, "front/src/app/globals.css"), "utf8");
const layoutPath = join(repoRoot, "front/src/app/layout.tsx");
const manifestPath = join(repoRoot, "front/src/app/manifest.ts");
const navbarPath = join(repoRoot, "front/src/components/Navbar.tsx");
const protectedLayoutPath = join(repoRoot, "front/src/components/ProtectedLayout.tsx");
const iconSvgPath = join(repoRoot, "front/public/icon.svg");
const apiPath = join(repoRoot, "front/src/lib/api.ts");

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

// 1. Tailwind config Pure Light-First token exactness
assertIncludes(tailwindConfig.toLowerCase(), "#ffffff", "Tailwind theme must expose pure white #ffffff canvas/surface color.");
assertIncludes(tailwindConfig.toLowerCase(), "#f8f9fc", "Tailwind theme must expose #f8f9fc elevated/subtle container color.");
assertIncludes(tailwindConfig.toLowerCase(), "#eff2f7", "Tailwind theme must expose #eff2f7 hover surface color.");
assertIncludes(tailwindConfig.toLowerCase(), "#121317", "Tailwind theme must expose #121317 primary ink color.");
assertIncludes(tailwindConfig.toLowerCase(), "#45474d", "Tailwind theme must expose #45474d secondary ink color.");
assertIncludes(tailwindConfig.toLowerCase(), "#6a6a71", "Tailwind theme must expose #6a6a71 muted ink color.");
assertIncludes(tailwindConfig.toLowerCase(), "#e2e5ec", "Tailwind theme must expose #e2e5ec hairline border color.");
assertIncludes(tailwindConfig.toLowerCase(), "#cdd4dc", "Tailwind theme must expose #cdd4dc strong border color.");
assertIncludes(tailwindConfig.toLowerCase(), "#1a73e8", "Tailwind theme must expose #1a73e8 functional accent brand-500 color.");
assertIncludes(tailwindConfig.toLowerCase(), "#1557b0", "Tailwind theme must expose #1557b0 brand-600 color.");
assertIncludes(tailwindConfig.toLowerCase(), "#eef4fe", "Tailwind theme must expose #eef4fe brand-50 color.");
assertNotIncludes(tailwindConfig, "github:", "Tailwind config must remove duplicate github-* color namespace.");
assertNotIncludes(tailwindConfig.toLowerCase(), "#5c6bc0", "Tailwind config must purge old lavender #5C6BC0 token.");

// 2. Global CSS variables & Pure Light-First surfaces
assertIncludes(globalsCss, "color-scheme: light;", "Global CSS must declare color-scheme: light;.");
assertIncludes(globalsCss.toLowerCase(), "--bg-scaffold: #ffffff;", "Global CSS :root must define --bg-scaffold: #ffffff;.");
assertIncludes(globalsCss.toLowerCase(), "--surface-card: #ffffff;", "Global CSS :root must define --surface-card: #ffffff;.");
assertIncludes(globalsCss.toLowerCase(), "--surface-elevated: #f8f9fc;", "Global CSS :root must define --surface-elevated: #f8f9fc;.");
assertIncludes(globalsCss.toLowerCase(), "--surface-subtle: #f8f9fc;", "Global CSS :root must define --surface-subtle: #f8f9fc;.");
assertIncludes(globalsCss.toLowerCase(), "--surface-hover: #eff2f7;", "Global CSS :root must define --surface-hover: #eff2f7;.");
assertIncludes(globalsCss.toLowerCase(), "--text-primary: #121317;", "Global CSS :root must define --text-primary: #121317;.");
assertIncludes(globalsCss.toLowerCase(), "--text-secondary: #45474d;", "Global CSS :root must define --text-secondary: #45474d;.");
assertIncludes(globalsCss.toLowerCase(), "--text-muted: #6a6a71;", "Global CSS :root must define --text-muted: #6a6a71;.");
assertIncludes(globalsCss.toLowerCase(), "--border-subtle: #e2e5ec;", "Global CSS :root must define --border-subtle: #e2e5ec;.");
assertIncludes(globalsCss.toLowerCase(), "--border-strong: #cdd4dc;", "Global CSS :root must define --border-strong: #cdd4dc;.");
assertIncludes(globalsCss, ".btn-primary", "Global CSS must define .btn-primary utility.");
assertIncludes(globalsCss, ".btn-secondary", "Global CSS must define .btn-secondary utility.");
assertIncludes(globalsCss, "text-wrap: balance;", "Global CSS must apply text-wrap: balance to headings.");
assertIncludes(globalsCss, "text-wrap: pretty;", "Global CSS must apply text-wrap: pretty to body copy.");
assertIncludes(globalsCss, "font-variant-numeric: tabular-nums;", "Global CSS must define tabular-nums utility.");
assertIncludes(globalsCss, "text-box: trim-both cap alphabetic;", "Global CSS must define .badge-trim optical alignment.");
assertIncludes(globalsCss, "rgba(33, 34, 38, 0.10)", "Global CSS .surface must use crisp hairline border rgba(33, 34, 38, 0.10).");
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

// 5. Required brand assets & shared components
for (const requiredPath of [
  "front/public/icon.svg",
  "front/public/favicon.ico",
  "front/public/apple-touch-icon.png",
  "front/public/images/default-profile.svg",
  "front/src/lib/format.ts",
  "front/src/components/StatusBadge.tsx",
]) {
  if (!existsSync(join(repoRoot, requiredPath))) {
    fail(`Missing required asset or shared module: ${requiredPath}`);
  }
}
assertIncludes(iconSvgSource.toLowerCase(), "#121317", "front/public/icon.svg must use crisp #121317 charcoal badge.");

// 6. Layout & Metadata
assertIncludes(layoutSource, 'metadataBase: new URL("https://buddy.aquilaxk.site")', "Metadata must set https://buddy.aquilaxk.site as metadataBase.");
assertIncludes(
  layoutSource,
  'description: "GitHub 오픈소스 이슈 추천, 코드 맥락 분석 및 PR 초안 작성을 지원하는 기여 워크스페이스"',
  "Metadata must use Korean workspace description without tautology."
);
assertIncludes(layoutSource, "/icon.svg", "Layout metadata must reference /icon.svg.");
assertIncludes(layoutSource, '<html lang="ko">', "Root html element must render in light-first mode without className=\"dark\".");
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
assertIncludes(protectedLayoutSource, "max-w-6xl", "Protected shell must keep the content width.");
assertIncludes(protectedLayoutSource, "<Navbar />", "Protected shell must keep the navigation structure.");
assertNotIncludes(protectedLayoutSource, "github-", "ProtectedLayout must not use duplicate github-* color classes.");

if (failures.length > 0) {
  console.error("Brand parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Brand parity validation passed.");
