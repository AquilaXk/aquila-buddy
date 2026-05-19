import { access, mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { launchChromeSession, resolveChromeUserDataDir } from "./browser-session.mjs";

const evidenceFile = process.env.EXTENSION_GITHUB_LIVE_EVIDENCE_FILE;
const stepState = {
  current: "startup",
};

if (process.env.EXTENSION_GITHUB_LIVE_SMOKE_ENABLED !== "true") {
  const reason = "EXTENSION_GITHUB_LIVE_SMOKE_ENABLED is not true";
  console.log(`Extension GitHub live smoke skipped because ${reason}.`);
  await writeEvidence("skipped", reason);
  process.exit(0);
}

const missing = await missingRequiredLiveInputs();
if (missing.length > 0) {
  const reason = `missing required profile or target env: ${missing.join(", ")}`;
  if (process.env.EXTENSION_GITHUB_LIVE_SKIP_ON_MISSING_SECRETS === "true") {
    console.log("Extension GitHub live smoke skipped because required profile or target env is missing.");
    console.log(`Extension GitHub live smoke skip reason: ${reason}.`);
    await writeEvidence("skipped", "missing required profile or target env");
    process.exit(0);
  }
  throw new Error(reason);
}

const githubBaseUrl = trimTrailingSlash(process.env.EXTENSION_GITHUB_LIVE_BASE_URL ?? "https://github.com");
const repo = process.env.EXTENSION_GITHUB_LIVE_REPO;
const issueNumber = process.env.EXTENSION_GITHUB_LIVE_ISSUE_NUMBER;
const compareUrl = process.env.EXTENSION_GITHUB_LIVE_COMPARE_URL;
const backendBaseUrl = trimTrailingSlash(process.env.EXTENSION_GITHUB_LIVE_BACKEND_BASE_URL);
const authHeader = process.env.EXTENSION_GITHUB_LIVE_AUTH_HEADER;
let chrome;

try {
  stepState.current = "backend-health";
  await assertBackendHealth(backendBaseUrl, authHeader);

  chrome = await launchChromeSession();

  stepState.current = "badge";
  const issueListPage = await chrome.newPage(`${githubBaseUrl}/${repo}/issues`);
  await issueListPage.installContentScript(githubFixtures({ repo, issueNumber }));
  await issueListPage.click("#omos-toggle");
  const badgeText = await issueListPage.textContent(`a[href="/${repo}/issues/${issueNumber}"] .omos-badge`);
  assertIncludes(badgeText, "✦ OMOS", "live issue list badge");

  stepState.current = "sidebar";
  const issueDetailPage = await chrome.newPage(`${githubBaseUrl}/${repo}/issues/${issueNumber}`);
  await issueDetailPage.installContentScript(githubFixtures({ repo, issueNumber }));
  await issueDetailPage.click("#omos-toggle");
  await issueDetailPage.click("#omos-guide-btn");
  const guideline = await issueDetailPage.textContent("#omos-analysis-result .omos-result-text");
  assertIncludes(guideline, "GitHub live smoke", "live issue guide result");
  await issueDetailPage.click("#omos-pseudo-btn");
  const pseudoCode = await issueDetailPage.textContent("#omos-analysis-result .omos-code-block");
  assertIncludes(pseudoCode, "assert live GitHub selectors", "live issue pseudo result");

  stepState.current = "pr-autofill";
  const prPage = await chrome.newPage(compareUrl);
  await prPage.installContentScript(githubFixtures({ repo, issueNumber }));
  await prPage.click("#omos-toggle");
  await prPage.fill("#omos-issue-number", issueNumber);
  await prPage.click("#omos-draft-btn");
  await prPage.click("#omos-apply-btn");
  const appliedTitle = await prPage.inputValue("#pull_request_title");
  const appliedBody = await prPage.inputValue("#pull_request_body");
  assertIncludes(appliedTitle, "GitHub live smoke", "live PR title autofill");
  assertIncludes(appliedBody, `close #${issueNumber}`, "live PR body autofill");

  await writeEvidence("pass", "none");
  console.log(`Extension GitHub live smoke completed for repo=${repo} issue=${issueNumber}`);
} catch (error) {
  const reason = `failed at ${stepState.current}: ${error.message}`;
  await writeEvidence("failed", reason);
  throw new Error(`${reason}. Check GitHub login, backend auth, content script injection, or DOM selectors.`);
} finally {
  await chrome?.close();
}

async function missingRequiredLiveInputs() {
  const required = [
    "EXTENSION_GITHUB_LIVE_REPO",
    "EXTENSION_GITHUB_LIVE_ISSUE_NUMBER",
    "EXTENSION_GITHUB_LIVE_COMPARE_URL",
    "EXTENSION_GITHUB_LIVE_BACKEND_BASE_URL",
    "EXTENSION_GITHUB_LIVE_AUTH_HEADER",
  ];
  const missingNames = required.filter((name) => !process.env[name]?.trim());
  const profileDir = resolveChromeUserDataDir();
  if (!profileDir) {
    missingNames.push("EXTENSION_E2E_CHROME_USER_DATA_DIR");
  } else {
    try {
      await access(profileDir);
    } catch {
      missingNames.push("EXTENSION_E2E_CHROME_USER_DATA_DIR");
    }
  }
  return missingNames;
}

async function assertBackendHealth(backendBaseUrl, authHeader) {
  const response = await fetch(`${backendBaseUrl}/api/v1/health`, {
    headers: {
      Accept: "application/json",
      ...parseHeader(authHeader),
    },
  });
  if (!response.ok) {
    throw new Error(`backend health check failed: HTTP ${response.status}`);
  }
}

function parseHeader(header) {
  const separator = header.indexOf(":");
  if (separator < 1) {
    return {};
  }
  return {
    [header.slice(0, separator).trim()]: header.slice(separator + 1).trim(),
  };
}

async function writeEvidence(result, reason) {
  if (!evidenceFile) {
    return;
  }
  const status = result === "pass" ? "pass" : result;
  const content = [
    "# Extension GitHub Live Smoke Evidence",
    "",
    `- executed_at: ${new Date().toISOString()}`,
    `- commit: ${process.env.GITHUB_SHA ?? "unknown"}`,
    `- github_run_id: ${process.env.GITHUB_RUN_ID ?? "local"}`,
    `- github_base_url: ${process.env.EXTENSION_GITHUB_LIVE_BASE_URL ?? "https://github.com"}`,
    `- repo: ${process.env.EXTENSION_GITHUB_LIVE_REPO ?? "unset"}`,
    `- issue_number: ${process.env.EXTENSION_GITHUB_LIVE_ISSUE_NUMBER ?? "unset"}`,
    `- result: ${result}`,
    `- failure_reason: ${reason}`,
    "",
    "## smoke 결과",
    "",
    "| area | result | evidence | note |",
    "| --- | --- | --- | --- |",
    `| badge | ${status} | GitHub issues page | 추천 badge 표시 |`,
    `| sidebar | ${status} | GitHub issue page | guide/pseudo sidebar 동작 |`,
    `| pr-autofill | ${status} | GitHub compare page | PR 초안/번역 autofill |`,
    "",
  ].join("\n");

  await mkdir(dirname(evidenceFile), { recursive: true });
  await writeFile(evidenceFile, content, "utf8");
  console.log(`Extension GitHub live smoke evidence written to ${evidenceFile}`);
}

function githubFixtures({ repo, issueNumber }) {
  return {
    cachedRecommendations: {
      data: [
        {
          repoFullName: repo,
          issueNumber: Number.parseInt(issueNumber, 10),
          title: "GitHub live smoke target",
          labels: ["good first issue"],
          summary: "GitHub live smoke selector target",
        },
      ],
    },
    issueGuide: {
      data: {
        filePaths: ["extension/content.js"],
        guideline: "GitHub live smoke guide result",
        sideEffects: "실제 GitHub DOM selector 변화 여부를 확인합니다.",
      },
    },
    issuePseudo: {
      data: {
        filePaths: ["extension/content.js"],
        pseudoCode: "assert live GitHub selectors\nassert sidebar actions",
      },
    },
    prDraft: {
      data: {
        id: 297,
        title: "[Test] GitHub live smoke autofill",
        body: `## 관련 이슈\nclose #${issueNumber}\n\n## 요약\nGitHub live smoke autofill을 확인합니다.`,
      },
    },
    prTranslation: {
      data: {
        titleEn: "GitHub live smoke autofill",
        bodyEn: `## Related issue\nclose #${issueNumber}\n\n## Summary\nValidate GitHub live smoke autofill.`,
      },
    },
  };
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, "");
}

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}
