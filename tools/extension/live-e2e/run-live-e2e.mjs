import { createGitHubFixtureServer } from "./fixture-server.mjs";
import { launchChromeSession } from "./browser-session.mjs";

const server = await createGitHubFixtureServer();
const chrome = await launchChromeSession();

try {
  const issueList = await fetch(`${server.origin}/AquilaXk/aquila-buddy/issues`);
  const html = await issueList.text();
  assertIncludes(html, "/AquilaXk/aquila-buddy/issues/42", "issue list fixture link");

  const page = await chrome.newPage(`${server.origin}/AquilaXk/aquila-buddy/issues`);
  await installContentScript(page, {
    cachedRecommendations: {
      data: [
        {
          repoFullName: "AquilaXk/aquila-buddy",
          issueNumber: 42,
          title: "Improve onboarding copy",
          labels: ["good first issue"],
          summary: "문서 기여로 시작하기 좋습니다.",
        },
      ],
    },
  });
  await page.click("#omos-toggle");
  const badgeText = await page.textContent(
    'a[href="/AquilaXk/aquila-buddy/issues/42"] .omos-badge'
  );
  assertEqual(badgeText, "✦ OMOS", "recommended issue badge text");

  console.log("extension live e2e passed");
} finally {
  await chrome.close();
  await server.close();
}

async function installContentScript(page, fixtures) {
  await page.installContentScript(fixtures);
}

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}

function assertEqual(actual, expected, label) {
  if (actual !== expected) {
    throw new Error(`${label}: expected ${expected}, received ${actual}`);
  }
}
