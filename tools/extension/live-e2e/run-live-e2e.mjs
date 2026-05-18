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

  const detailPage = await chrome.newPage(`${server.origin}/AquilaXk/aquila-buddy/issues/42`);
  await installContentScript(detailPage, {
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
    issueGuide: {
      data: {
        filePaths: ["front/app/dashboard/page.tsx"],
        guideline: "온보딩 문구를 사용자의 첫 기여 흐름에 맞게 정리합니다.",
        sideEffects: "기존 CTA 문구와 중복되지 않도록 확인합니다.",
      },
    },
    issuePseudo: {
      data: {
        filePaths: ["front/app/dashboard/page.tsx"],
        pseudoCode: "load dashboard copy\nrender first contribution CTA",
      },
    },
  });
  await detailPage.click("#omos-toggle");
  await detailPage.click("#omos-guide-btn");
  const guideline = await detailPage.textContent("#omos-analysis-result .omos-result-text");
  assertIncludes(guideline, "첫 기여 흐름", "issue guide result");

  await detailPage.click("#omos-pseudo-btn");
  const pseudoCode = await detailPage.textContent("#omos-analysis-result .omos-code-block");
  assertIncludes(pseudoCode, "render first contribution CTA", "issue pseudo result");

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
