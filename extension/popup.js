const loginBtn = document.getElementById("login-btn");
const logoutBtn = document.getElementById("logout-btn");
const statusDot = document.getElementById("status-dot");
const statusLabel = document.getElementById("status-label");
const envSelect = document.getElementById("env-select");
const recList = document.getElementById("rec-list");
const recHint = document.getElementById("rec-hint");
const recLoading = document.getElementById("rec-loading");

function setLoggedIn(isLoggedIn) {
  if (isLoggedIn) {
    statusDot.className = "dot on";
    statusLabel.textContent = "로그인됨";
    loginBtn.classList.add("hidden");
    logoutBtn.classList.remove("hidden");
    loadHistory();
    return;
  }

  statusDot.className = "dot off";
  statusLabel.textContent = "로그인되지 않음";
  recHint.textContent = "";
  loginBtn.classList.remove("hidden");
  logoutBtn.classList.add("hidden");
  renderEmptyState("로그인 후 추천 이슈를 확인할 수 있습니다.");
}

function loadHistory() {
  recLoading.textContent = "불러오는 중...";
  recLoading.classList.remove("hidden");
  recList.innerHTML = "";

  chrome.runtime.sendMessage({ type: "GET_RECOMMEND_HISTORY" }, (response) => {
    recLoading.classList.add("hidden");

    if (!response?.ok) {
      renderEmptyState("이력을 불러오지 못했습니다.");
      return;
    }

    const issues = response.data?.data ?? [];
    if (issues.length === 0) {
      renderEmptyState("아직 추천 이력이 없습니다.\n아래 버튼으로 첫 추천을 받아보세요.");
    } else {
      recHint.textContent = `${issues.length}개`;
      renderIssues(issues);
    }

    appendRefreshButton();
  });
}

function refreshRecommendations() {
  recLoading.textContent = "AI 분석 중... (수 분이 걸릴 수 있습니다)";
  recLoading.classList.remove("hidden");
  recList.innerHTML = "";
  recHint.textContent = "";

  chrome.runtime.sendMessage({ type: "GET_RECOMMEND_ISSUES" }, (response) => {
    if (!response?.ok) {
      recLoading.classList.add("hidden");
      if (response?.error === "NOT_AUTHENTICATED" || response?.error === "TOKEN_EXPIRED") {
        setLoggedIn(false);
        return;
      }
      renderEmptyState("추천 생성에 실패했습니다.");
      appendRefreshButton();
      return;
    }

    loadHistory();
  });
}

function renderIssues(issues) {
  recList.innerHTML = issues.map(renderIssueCard).join("");
}

function formatScoreBadge(score) {
  if (typeof score !== "number" || !Number.isFinite(score) || score <= 0) {
    return "";
  }
  const percent = score <= 1 ? Math.round(score * 100) : Math.round(score);
  return percent > 0 ? `<span class="rec-score">${percent}%</span>` : "";
}

function renderIssueCard(issue) {
  const url = `https://github.com/${issue.repoFullName}/issues/${issue.issueNumber}`;
  const labels = (issue.labels ?? [])
    .map((label) => `<span class="rec-label">${escapeHtml(label)}</span>`)
    .join("");
  const scoreBadge = formatScoreBadge(issue.score);
  const analyzedBadge = issue.isAnalyzed ? '<span class="rec-analyzed">분석 완료</span>' : "";
  const badges = scoreBadge || analyzedBadge ? `<span class="rec-badges">${scoreBadge}${analyzedBadge}</span>` : "";

  return `
    <a class="rec-card" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">
      <div class="rec-card-meta">
        <span class="rec-repo">${escapeHtml(issue.repoFullName)} #${issue.issueNumber}</span>
        ${badges}
      </div>
      <p class="rec-title">${escapeHtml(issue.title)}</p>
      ${issue.summary ? `<p class="rec-summary">${escapeHtml(issue.summary)}</p>` : ""}
      ${labels ? `<div class="rec-labels">${labels}</div>` : ""}
    </a>
  `;
}

function renderEmptyState(message) {
  recList.innerHTML = `
    <div class="empty-state">
      <div>${escapeHtml(message).replace("\n", "<br>")}</div>
    </div>
  `;
}

function appendRefreshButton() {
  const button = document.createElement("button");
  button.id = "refresh-btn";
  button.textContent = "새 추천 받기";
  button.addEventListener("click", () => {
    button.remove();
    refreshRecommendations();
  });
  recList.appendChild(button);
}

const HTML_ESCAPE_MAP = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#039;",
};

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => HTML_ESCAPE_MAP[char]);
}

function initializePopup() {
  chrome.runtime.sendMessage({ type: "GET_ENVIRONMENT" }, (envResponse) => {
    if (envResponse?.ok && envResponse.data?.key && envSelect) {
      envSelect.value = envResponse.data.key;
    }
    chrome.runtime.sendMessage({ type: "GET_AUTH_STATUS" }, (response) => {
      setLoggedIn(response?.isLoggedIn ?? false);
    });
  });
}

if (envSelect) {
  envSelect.addEventListener("change", () => {
    chrome.runtime.sendMessage({ type: "SET_ENVIRONMENT", envKey: envSelect.value }, (response) => {
      if (response?.ok) {
        setLoggedIn(false);
      }
    });
  });
}

loginBtn.addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "LOGIN" });
  window.close();
});

logoutBtn.addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "LOGOUT" }, () => setLoggedIn(false));
});

initializePopup();
