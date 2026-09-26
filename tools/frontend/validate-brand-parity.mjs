import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const tailwindConfig = readFileSync(join(repoRoot, "front/tailwind.config.ts"), "utf8");
const globalsCss = readFileSync(join(repoRoot, "front/src/app/globals.css"), "utf8");
const themePath = join(repoRoot, "front/src/lib/theme.ts");
const layoutPath = join(repoRoot, "front/src/app/layout.tsx");
const manifestPath = join(repoRoot, "front/src/app/manifest.ts");
const navbarPath = join(repoRoot, "front/src/components/Navbar.tsx");
const protectedLayoutPath = join(repoRoot, "front/src/components/ProtectedLayout.tsx");
const serviceBrandAssetsPath = join(repoRoot, "front/src/lib/serviceBrandAssets.ts");

const themeSource = existsSync(themePath) ? readFileSync(themePath, "utf8") : "";
const layoutSource = readFileSync(layoutPath, "utf8");
const manifestSource = existsSync(manifestPath) ? readFileSync(manifestPath, "utf8") : "";
const navbarSource = readFileSync(navbarPath, "utf8");
const protectedLayoutSource = readFileSync(protectedLayoutPath, "utf8");
const serviceBrandAssetsSource = existsSync(serviceBrandAssetsPath)
  ? readFileSync(serviceBrandAssetsPath, "utf8")
  : "";
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

// 1. Tailwind config token exactness
assertIncludes(tailwindConfig, "#5C6BC0", "Tailwind theme must expose the signature lavender brand-500 color.");
assertIncludes(tailwindConfig, "#4D5CB5", "Tailwind theme must expose the hover brand-600 color.");
assertIncludes(tailwindConfig, "#3F4EA3", "Tailwind theme must expose the active brand-700 color.");
assertIncludes(tailwindConfig, "#18191D", "Tailwind theme must expose the dark scaffold color.");
assertIncludes(tailwindConfig, "#212529", "Tailwind theme must expose the dark surface color.");
assertIncludes(tailwindConfig, "#2A2E33", "Tailwind theme must expose the dark elevated color.");
assertIncludes(tailwindConfig, "#343A40", "Tailwind theme must expose the dark border color.");
assertIncludes(tailwindConfig, "#F8F9FA", "Tailwind theme must expose the dark text color.");
assertIncludes(tailwindConfig, "#9CA3AF", "Tailwind theme must expose the dark muted color.");
assertIncludes(tailwindConfig, "#F7F8FC", "Tailwind theme must expose the neutral scaffold color.");
assertIncludes(tailwindConfig, "#FFFFFF", "Tailwind theme must expose the neutral surface color.");
assertIncludes(tailwindConfig, "#E1E4EE", "Tailwind theme must expose the neutral border color.");

// 2. Global CSS variables & effects
assertIncludes(globalsCss, "--brand-500: #5C6BC0;", "Global CSS must define signature lavender brand 500 token.");
assertIncludes(globalsCss, "--bg-scaffold: #F7F8FC;", "Global CSS :root must define neutral scaffold color.");
assertIncludes(globalsCss, "--surface-card: #FFFFFF;", "Global CSS :root must define neutral surface card color.");
assertIncludes(globalsCss, "--bg-scaffold: #18191D;", "Global CSS .dark must define dark scaffold color.");
assertIncludes(globalsCss, "--surface-card: #212529;", "Global CSS .dark must define dark surface card color.");
assertIncludes(globalsCss, "--border-subtle", "Global CSS must define border subtle token.");
assertIncludes(globalsCss, ".lavender-glow", "Global CSS must define lavender glow effect.");
assertIncludes(globalsCss, "rgba(92, 107, 192, 0.35)", "Global CSS lavender glow must match signature lavender shadow.");
assertIncludes(globalsCss, ".glassmorphism", "Global CSS must define glassmorphism utility.");

// 3. Theme library
assertIncludes(themeSource, "LAVENDER_PALETTE", "Theme library must expose the LAVENDER_PALETTE constant.");
assertIncludes(themeSource, "#5C6BC0", "Theme library must define the #5C6BC0 signature lavender token.");
assertIncludes(themeSource, "#18191D", "Theme library must define the #18191D dark scaffold token.");

// 4. Brand assets
for (const asset of [
  "front/public/favicon.ico",
  "front/public/apple-touch-icon.png",
  "front/public/bank-favicon.ico",
  "front/public/bank-apple-touch-icon.png",
  "front/public/brand-mascot.png",
  "front/public/brand-mascot-bank.png",
  "front/public/brand-mascot-blog.png",
  "front/public/blog-favicon.ico",
  "front/public/blog-apple-touch-icon.png",
  "front/public/images/default-profile.svg"
]) {
  if (!existsSync(join(repoRoot, asset))) {
    fail(`Missing brand asset: ${asset}`);
  }
}

// 5. Layout & Metadata
assertIncludes(layoutSource, 'applicationName: "aquila buddy"', "Metadata must expose the aquila buddy application name.");
assertIncludes(layoutSource, 'metadataBase: new URL("https://aquila-buddy.aquilaxk.site")', "Metadata must set the aquila buddy metadata base.");
assertIncludes(layoutSource, 'siteName: "aquila buddy"', "OpenGraph metadata must expose the aquila buddy site name.");
assertIncludes(layoutSource, 'url: "/brand-mascot.png"', "OpenGraph metadata must use the representative brand image.");
assertIncludes(layoutSource, "serviceBrandAssets.map", "OpenGraph metadata must include service brand representative images.");
assertIncludes(layoutSource, "flatMap", "Icon metadata must include service brand favicon aliases.");

// 6. Service brand assets
assertIncludes(serviceBrandAssetsSource, "export const serviceBrandAssets", "Service brand assets must be centralized.");
assertIncludes(serviceBrandAssetsSource, 'id: "bank"', "Service brand assets must include the bank service.");
assertIncludes(serviceBrandAssetsSource, 'id: "blog"', "Service brand assets must include the blog service.");
assertIncludes(serviceBrandAssetsSource, 'favicon: "/bank-favicon.ico"', "Bank service must expose its favicon alias.");
assertIncludes(serviceBrandAssetsSource, 'appleTouchIcon: "/bank-apple-touch-icon.png"', "Bank service must expose its apple touch icon alias.");
assertIncludes(serviceBrandAssetsSource, 'representativeImage: "/brand-mascot-bank.png"', "Bank service must expose its representative image.");
assertIncludes(serviceBrandAssetsSource, 'favicon: "/blog-favicon.ico"', "Blog service must expose its favicon alias.");
assertIncludes(serviceBrandAssetsSource, 'appleTouchIcon: "/blog-apple-touch-icon.png"', "Blog service must expose its apple touch icon alias.");
assertIncludes(serviceBrandAssetsSource, 'representativeImage: "/brand-mascot-blog.png"', "Blog service must expose its representative image.");

// 7. Web manifest
assertIncludes(manifestSource, 'name: "aquila buddy"', "Manifest must expose the full aquila buddy name.");
assertIncludes(manifestSource, 'short_name: "aquila buddy"', "Manifest must expose the short aquila buddy name.");
assertIncludes(manifestSource, 'src: "/brand-mascot.png"', "Manifest must include the representative brand image.");
assertIncludes(manifestSource, 'background_color: "#18191D"', "Manifest must use the dark scaffold background color.");
assertIncludes(manifestSource, 'theme_color: "#18191D"', "Manifest must use the dark scaffold theme color.");
assertNotIncludes(manifestSource, "#101214", "Manifest must not contain the obsolete gothic page color.");
assertNotIncludes(manifestSource, "#121416", "Manifest must not contain the obsolete gothic theme color.");
assertIncludes(manifestSource, "...serviceBrandAssets.flatMap", "Manifest must include service brand icon assets.");
assertIncludes(manifestSource, "asset.favicon", "Manifest must include service brand favicons.");
assertIncludes(manifestSource, "asset.appleTouchIcon", "Manifest must include service brand apple touch icons.");
assertIncludes(manifestSource, "asset.representativeImage", "Manifest must include service brand representative images.");

// 8. Navbar
assertIncludes(navbarSource, 'aria-label="Primary"', "Navbar must expose a primary navigation label.");
assertIncludes(navbarSource, 'src="/brand-mascot-bank.png"', "Navbar must use the framed brand mascot asset.");
assertIncludes(navbarSource, "backdrop-blur-sm", "Navbar must use the glassmorphism backdrop blur.");
assertIncludes(navbarSource, "lucide-react", "Navbar must import icons from lucide-react.");
assertIncludes(navbarSource, "min-h-16", "Navbar must use the header rhythm.");
if (navbarSource.includes("tracking-tight")) {
  fail("Navbar must avoid negative letter spacing.");
}

// 9. Protected layout
assertIncludes(protectedLayoutSource, "bg-transparent", "Protected shell must not hide the page backdrop.");
assertIncludes(protectedLayoutSource, "max-w-6xl", "Protected shell must keep the existing content width.");
assertIncludes(protectedLayoutSource, "<Navbar />", "Protected shell must keep the existing navigation structure.");

// 10. Gothic theme complete purge verification
for (const gothicToken of [
  "--gothic-page",
  "--gothic-brass",
  "--gothic-parchment",
  "--gothic-line",
  "repeating-linear-gradient"
]) {
  assertNotIncludes(globalsCss, gothicToken, `Global CSS must completely purge gothic token: ${gothicToken}`);
}

for (const gothicColor of [
  "#101214",
  "#c2a35b",
  "#efe7d3",
  "#d2b46d",
  "#8aa0a6"
]) {
  assertNotIncludes(tailwindConfig, gothicColor, `Tailwind config must completely purge gothic color: ${gothicColor}`);
  assertNotIncludes(globalsCss, gothicColor, `Global CSS must completely purge gothic color: ${gothicColor}`);
}

if (failures.length > 0) {
  console.error("Brand parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Brand parity validation passed.");
