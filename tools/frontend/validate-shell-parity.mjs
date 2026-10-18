import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const protectedLayoutPath = join(repoRoot, "front/src/components/ProtectedLayout.tsx");
const navbarPath = join(repoRoot, "front/src/components/Navbar.tsx");
const layoutPath = join(repoRoot, "front/src/app/layout.tsx");
const appRoot = join(repoRoot, "front/src/app");

const failures = [];

function readText(path) {
  return readFileSync(path, "utf8");
}

function fail(message) {
  failures.push(message);
}

function assertIncludes(text, needle, message) {
  if (!text.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(text, needle, message) {
  if (text.includes(needle)) {
    fail(message);
  }
}

function collectTsxFiles(directory) {
  return readdirSync(directory)
    .flatMap((entry) => {
      const path = join(directory, entry);
      if (statSync(path).isDirectory()) {
        return collectTsxFiles(path);
      }
      return path.endsWith(".tsx") ? [path] : [];
    });
}

const protectedLayout = readText(protectedLayoutPath);
assertIncludes(protectedLayout, "import Navbar from \"./Navbar\";", "ProtectedLayout must use the shell Navbar default export.");
assertIncludes(protectedLayout, "<Navbar />", "ProtectedLayout must render Navbar once.");
assertIncludes(protectedLayout, "<main className=\"mx-auto max-w-6xl px-4 py-8\">", "ProtectedLayout main wrapper changed.");
assertNotIncludes(protectedLayout, "title:", "ProtectedLayout must not accept a title prop.");
assertNotIncludes(protectedLayout, "WorkspaceStatus", "ProtectedLayout must not render workspace status.");
assertNotIncludes(protectedLayout, "Workspace", "ProtectedLayout must not add a workspace heading.");

const navbar = readText(navbarPath);
assertIncludes(navbar, "const navLinks = [", "Navbar must keep the target navLinks contract.");
assertIncludes(navbar, "export default function Navbar()", "Navbar must be the default export.");
assertIncludes(navbar, "aria-label=\"Primary\"", "Navbar must keep an accessible primary navigation label.");
assertIncludes(navbar, "sticky top-0 z-50 border-b border-dark-border", "Navbar root shell must use hairline border-dark-border.");
assertIncludes(navbar, "bg-white/95", "Navbar must use crisp light surface.");
assertIncludes(navbar, "min-h-16", "Navbar must allow the mobile header height to grow instead of squeezing labels.");
assertIncludes(navbar, "border-b-2 border-[#121317]", "Navbar must use a 2px bottom hairline indicator for active tabs instead of boxed button fills.");
assertIncludes(navbar, "aria-controls={mobileMenuId}", "Navbar mobile toggle must bind to the drawer via aria-controls.");
assertIncludes(navbar, "id={mobileMenuId}", "Navbar mobile drawer must have id matching aria-controls.");
assertIncludes(navbar, "aria-expanded={isMenuOpen}", "Navbar mobile toggle must reflect drawer expansion state via aria-expanded.");
assertIncludes(navbar, "setIsMenuOpen", "Navbar mobile toggle must reactively update menu visibility.");
assertIncludes(navbar, "sm:hidden", "Navbar mobile controls must be hidden on desktop viewports.");
assertIncludes(navbar, "sm:flex", "Navbar desktop menu must be restored at the small breakpoint.");
assertIncludes(navbar, "<svg", "Navbar must render the geometric inline SVG mark.");
assertIncludes(navbar, "Aquila Buddy", "Navbar must show the Aquila Buddy lockup.");
assertIncludes(navbar, "clearAuthState();", "Navbar logout must clear the local auth marker.");
assertIncludes(navbar, "logout()", "Navbar logout must call the backend logout endpoint.");
assertIncludes(navbar, "router.replace(\"/login\")", "Navbar logout must replace history with the login screen.");
assertIncludes(navbar, "matchPrefixes: [\"/pr-draft\", \"/pr/\"]", "Navbar must keep PR draft/detail active-state grouping.");
assertNotIncludes(navbar, "Sparkles", "Navbar must not import or render Sparkles icon.");
assertNotIncludes(navbar, "LayoutDashboard", "Navbar must not render decorative tab icons.");
assertNotIncludes(navbar, "/brand-mascot-bank.png", "Navbar must not reference cross-project bank mascot.");
assertNotIncludes(navbar, "github-", "Navbar must not use duplicate github-* color classes.");
assertNotIncludes(navbar, "flex-wrap", "Navbar must not rely on obsolete flex-wrap layout.");
assertNotIncludes(navbar, "sm:flex-row", "Navbar must not rely on obsolete sm:flex-row wrapping.");
assertNotIncludes(navbar, "<header", "Navbar must not add an extra header wrapper.");
assertNotIncludes(navbar, "OMOS", "Navbar must not use the old service name.");
assertNotIncludes(navbar, "router.push(\"/login\")", "Navbar logout must not leave a protected page in browser history.");

for (const label of ["대시보드", "추천 이슈", "PR 초안", "내 프로필"]) {
  assertIncludes(navbar, `label: "${label}"`, `Navbar is missing ${label}.`);
}

const layout = readText(layoutPath);
assertIncludes(layout, "/icon.svg", "Root metadata must declare /icon.svg.");
assertIncludes(layout, "apple: \"/apple-touch-icon.png\"", "Root metadata must declare the apple touch icon.");
assertNotIncludes(layout, 'className="dark"', "Root layout must not force dark theme.");

for (const assetPath of [
  "front/public/icon.svg",
  "front/public/favicon.ico",
  "front/public/apple-touch-icon.png"
]) {
  if (!existsSync(join(repoRoot, assetPath))) {
    fail(`${assetPath} must exist.`);
  }
}

for (const path of collectTsxFiles(appRoot)) {
  const text = readText(path);
  if (text.includes("<ProtectedLayout title=")) {
    fail(`${relative(repoRoot, path)} still passes a title prop to ProtectedLayout.`);
  }
  if (text.includes("Sparkles")) {
    fail(`${relative(repoRoot, path)} still uses the banned Sparkles AI cliché icon.`);
  }
}

for (const removedPath of [
  "front/src/components/WorkspaceStatus.tsx",
  "front/src/components/workspaceHealthStatus.ts"
]) {
  if (existsSync(join(repoRoot, removedPath))) {
    fail(`${removedPath} should stay removed from the frontend shell.`);
  }
}

if (failures.length > 0) {
  console.error("Frontend shell parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend shell parity validation passed.");
