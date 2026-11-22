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
assertIncludes(protectedLayout, 'import Navbar, { SidebarProvider, SiteHeader } from "./Navbar";', "ProtectedLayout must import Navbar, SidebarProvider, and SiteHeader.");
assertIncludes(protectedLayout, "<SidebarProvider>", "ProtectedLayout must provide sidebar state via SidebarProvider.");
assertIncludes(protectedLayout, "<Navbar />", "ProtectedLayout must render Navbar once.");
assertIncludes(protectedLayout, "<SiteHeader />", "ProtectedLayout must render sticky SiteHeader.");
assertIncludes(protectedLayout, "<ShortcutHelpDialog />", "ProtectedLayout must render ShortcutHelpDialog once.");
assertIncludes(protectedLayout, "<CommandPalette />", "ProtectedLayout must render CommandPalette once.");
assertIncludes(protectedLayout, "lg:rounded-xl lg:border lg:border-[#d1d9e0] lg:shadow-xs", "ProtectedLayout must use dashboard-01 inset canvas styling on lg+.");
assertIncludes(protectedLayout, "max-w-[1280px]", "ProtectedLayout main wrapper must use max-w-[1280px].");
assertNotIncludes(protectedLayout, "dark-text", "ProtectedLayout must not use legacy dark-text class.");
assertNotIncludes(protectedLayout, "title:", "ProtectedLayout must not accept a title prop.");
assertNotIncludes(protectedLayout, "WorkspaceStatus", "ProtectedLayout must not render workspace status.");
assertNotIncludes(protectedLayout, "Workspace", "ProtectedLayout must not add a workspace heading.");

const navbar = readText(navbarPath);
assertIncludes(navbar, "const navLinks = [", "Navbar must keep the target navLinks contract.");
assertIncludes(navbar, "export default function Navbar()", "Navbar must be the default export.");
assertIncludes(navbar, "export function SidebarProvider", "Navbar must export SidebarProvider.");
assertIncludes(navbar, "export function SiteHeader()", "Navbar must export sticky SiteHeader.");
assertIncludes(navbar, 'aria-label="Primary"', "Navbar must keep an accessible primary navigation label.");
assertIncludes(navbar, "SIDEBAR_STORAGE_KEY", "Navbar must persist collapsed sidebar preference in localStorage.");
assertIncludes(navbar, "OPEN_SHORTCUT_HELP_EVENT", "Navbar must expose shortcut help dialog trigger.");
assertIncludes(navbar, "OPEN_COMMAND_PALETTE_EVENT", "Navbar must expose command palette trigger.");
assertIncludes(navbar, "<Kbd", "Navbar must render Kbd shortcut hints.");
assertIncludes(navbar, "PanelLeft", "SiteHeader must expose desktop sidebar collapse trigger.");
assertIncludes(navbar, "aria-controls={mobileMenuId}", "Navbar mobile toggle must bind to the drawer via aria-controls.");
assertIncludes(navbar, "id={mobileMenuId}", "Navbar mobile drawer must have id matching aria-controls.");
assertIncludes(navbar, "aria-expanded={mobileOpen}", "Navbar mobile toggle must reflect drawer expansion state via aria-expanded.");
assertIncludes(navbar, "setMobileOpen", "Navbar mobile toggle must reactively update menu visibility.");
assertIncludes(navbar, "lg:hidden", "Navbar mobile controls must be hidden on desktop viewports.");
assertIncludes(navbar, "lg:w-[236px]", "Navbar desktop sidebar must expand to 236px.");
assertIncludes(navbar, "lg:w-[60px]", "Navbar desktop sidebar must collapse to 60px icon rail.");
assertIncludes(navbar, "<svg", "Navbar must render the geometric inline SVG mark.");
assertIncludes(navbar, "Aquila Buddy", "Navbar must show the Aquila Buddy lockup.");
assertIncludes(navbar, "clearAuthState();", "Navbar logout must clear the local auth marker.");
assertIncludes(navbar, "logout()", "Navbar logout must call the backend logout endpoint.");
assertIncludes(navbar, 'router.replace("/login")', "Navbar logout must replace history with the login screen.");
assertIncludes(navbar, 'matchPrefixes: ["/pr-draft", "/pr/"]', "Navbar must keep PR draft/detail active-state grouping.");
assertNotIncludes(navbar, "초안 작성소", "Navbar must not use stale non-standard copy.");
assertNotIncludes(navbar, "Sparkles", "Navbar must not import or render Sparkles icon.");
assertNotIncludes(navbar, "LayoutDashboard", "Navbar must not render decorative tab icons.");
assertNotIncludes(navbar, "/brand-mascot-bank.png", "Navbar must not reference cross-project bank mascot.");
assertNotIncludes(navbar, "github-", "Navbar must not use duplicate github-* color classes.");
assertNotIncludes(navbar, "studio-toolbar", "Navbar must purge legacy studio-toolbar class.");
assertNotIncludes(navbar, "#c0c0c0", "Navbar must purge legacy #c0c0c0 border token.");
assertNotIncludes(navbar, "OMOS", "Navbar must not use the old service name.");
assertNotIncludes(navbar, 'router.push("/login")', "Navbar logout must not leave a protected page in browser history.");

for (const label of ["대시보드", "추천 이슈", "PR 초안", "내 프로필"]) {
  assertIncludes(navbar, `label: "${label}"`, `Navbar is missing ${label}.`);
}

const layout = readText(layoutPath);
assertIncludes(layout, "/icon.svg", "Root metadata must declare /icon.svg.");
assertIncludes(layout, 'apple: "/apple-touch-icon.png"', "Root metadata must declare the apple touch icon.");
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
