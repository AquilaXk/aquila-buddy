const loginBtn = document.getElementById("login-btn");
const logoutBtn = document.getElementById("logout-btn");
const statusDot = document.getElementById("status-dot");
const statusLabel = document.getElementById("status-label");
const recList = document.getElementById("rec-list");
const recHint = document.getElementById("rec-hint");
const recLoading = document.getElementById("rec-loading");

function setLoggedIn(isLoggedIn) {
  if (isLoggedIn) {
    statusDot.className = "dot on";
    statusLabel.textContent = "로그인됨";
    loginBtn.style.display = "none";
    logoutBtn.style.display = "block";
    loadHistory();
    return;
  }

  statusDot.className = "dot off";
  statusLabel.textContent = "로그인되지 않음";
  loginBtn.style.display = "block";
  logoutBtn.style.display = "none";
  renderEmptyState("로그인 후 추천 이슈를 확인할 수 있습니다.");
}

function loadHistory() {
  recLoading.textContent = "불러오는 중...";
  recLoading.style.display = "block";
  recList.innerHTML = "";

  chrome.runtime.sendMessage({ type: "GET_RECOMMEND_HISTORY" }, (response) => {
    recLoading.style.display = "none";

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
  recLoading.style.display = "block";
  recList.innerHTML = "";
  recHint.textContent = "";

  chrome.runtime.sendMessage({ type: "GET_RECOMMEND_ISSUES" }, (response) => {
    if (!response?.ok) {
      recLoading.style.display = "none";
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

function renderIssueCard(issue) {
  const url = `https://github.com/${issue.repoFullName}/issues/${issue.issueNumber}`;
  const labels = (issue.labels ?? [])
    .map((label) => `<span class="rec-label">${escapeHtml(label)}</span>`)
    .join("");
  const analyzedBadge = issue.isAnalyzed ? '<span class="rec-analyzed">분석 완료</span>' : "";

  return `
    <a class="rec-card" href="${url}" target="_blank">
      <div class="rec-card-meta">
        <span class="rec-repo">${escapeHtml(issue.repoFullName)} #${issue.issueNumber}</span>
        ${analyzedBadge}
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

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

chrome.runtime.sendMessage({ type: "GET_AUTH_STATUS" }, (response) => {
  setLoggedIn(response?.isLoggedIn ?? false);
});

loginBtn.addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "LOGIN" });
  window.close();
});

logoutBtn.addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "LOGOUT" }, () => setLoggedIn(false));
});
