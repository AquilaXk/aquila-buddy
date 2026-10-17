// Extension 새로고침 후 context가 무효화되면 sendMessage가 에러를 던집니다.
// 모든 메시지 전송을 이 헬퍼로 감싸서 일관된 에러 처리를 합니다.
function sendMessage(message, callback) {
  try {
    chrome.runtime.sendMessage(message, callback);
  } catch (e) {
    if (e.message?.includes('Extension context invalidated')) {
      callback({ ok: false, error: 'CONTEXT_INVALIDATED' });
    } else {
      callback({ ok: false, error: e.message });
    }
  }
}

// GitHub 현재 페이지 타입 감지
function detectPageType() {
  const path = location.pathname;
  if (/^\/[^/]+\/[^/]+\/issues\/?$/.test(path)) return 'ISSUE_LIST';
  if (/^\/[^/]+\/[^/]+\/issues\/\d+\/?$/.test(path)) return 'ISSUE_DETAIL';
  if (/^\/[^/]+\/[^/]+\/compare/.test(path)) return 'PR_CREATE';
  return null;
}

// <aquila-buddy-root> Custom Element 정의
const BaseElement = typeof HTMLElement !== 'undefined' ? HTMLElement : class {};

class AquilaBuddyRoot extends BaseElement {
  constructor() {
    super();
  }
}

if (typeof customElements !== 'undefined' && !customElements.get('aquila-buddy-root')) {
  customElements.define('aquila-buddy-root', AquilaBuddyRoot);
}

const SHADOW_CSS = `
:host {
  all: initial;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
  color: #121317;
  font-size: 13px;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
  -webkit-font-smoothing: antialiased;
}

*, *::before, *::after {
  box-sizing: border-box;
}

#aquila-buddy-toggle {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 9999;
  background: #121317;
  color: #ffffff;
  border: 1px solid rgba(33, 34, 38, 0.18);
  border-right: none;
  border-radius: 6px 0 0 6px;
  width: 32px;
  height: 48px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: -0.02em;
  cursor: pointer;
  transition: background 0.15s;
  box-shadow: -2px 0 8px rgba(18, 19, 23, 0.08);
}

#aquila-buddy-toggle:hover {
  background: #2f3034;
}

#aquila-buddy-toggle:focus-visible {
  outline: 2px solid #1a73e8;
  outline-offset: 2px;
}

#aquila-buddy-sidebar {
  position: fixed;
  right: 0;
  top: 0;
  width: 340px;
  height: 100vh;
  background: #ffffff;
  color: #121317;
  border-left: 1px solid rgba(33, 34, 38, 0.10);
  z-index: 9998;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  transition: transform 0.2s ease;
  box-shadow: -4px 0 16px rgba(18, 19, 23, 0.08);
}

#aquila-buddy-sidebar.aquila-buddy-hidden {
  transform: translateX(100%);
}

#aquila-buddy-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 13px 16px;
  background: #ffffff;
  border-bottom: 1px solid rgba(33, 34, 38, 0.10);
  color: #121317;
  font-weight: 600;
  font-size: 14px;
  letter-spacing: -0.02em;
  flex-shrink: 0;
}

#aquila-buddy-close {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  color: #6a6a71;
  cursor: pointer;
  font-size: 13px;
  width: 24px;
  height: 24px;
  padding: 0;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}

#aquila-buddy-close:hover {
  background: #eff2f7;
  color: #121317;
}

#aquila-buddy-content {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}

.aquila-buddy-loading {
  color: #6a6a71;
  font-size: 12px;
  margin: 0;
}

.aquila-buddy-empty {
  color: #6a6a71;
  font-size: 12px;
  margin: 0;
}

.aquila-buddy-error {
  color: #c5221f;
  background: #fce8e6;
  border: 1px solid rgba(197, 34, 31, 0.20);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}

.aquila-buddy-section-title {
  font-size: 11px;
  font-weight: 600;
  color: #6a6a71;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0 0 10px;
}

.aquila-buddy-desc {
  font-size: 12px;
  color: #45474d;
  line-height: 1.6;
  margin: 0 0 12px;
}

.aquila-buddy-desc-followup {
  margin-top: 8px;
}

.aquila-buddy-count {
  color: #121317;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.aquila-buddy-inline-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: #e6f4ea;
  border: 1px solid rgba(19, 115, 51, 0.20);
  border-radius: 4px;
  color: #137333;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.aquila-buddy-issue-list {
  margin-top: 12px;
}

.aquila-buddy-issue-card {
  background: #f8f9fc;
  border: 1px solid rgba(33, 34, 38, 0.10);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  transition: background 0.15s, border-color 0.15s;
}

.aquila-buddy-issue-card:hover {
  background: #eff2f7;
  border-color: rgba(33, 34, 38, 0.18);
}

.aquila-buddy-issue-card a {
  color: #1a73e8;
  text-decoration: none;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.aquila-buddy-issue-card a:hover {
  text-decoration: underline;
}

.aquila-buddy-issue-card p {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: -0.01em;
  margin: 4px 0 6px;
  color: #121317;
  line-height: 1.4;
}

.aquila-buddy-labels {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.aquila-buddy-label {
  display: inline-block;
  background: #eff2f7;
  border: 1px solid rgba(33, 34, 38, 0.08);
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 11px;
  color: #45474d;
  font-variant-numeric: tabular-nums;
}

.aquila-buddy-btn,
.aquila-buddy-btn-primary {
  display: block;
  width: 100%;
  padding: 8px 12px;
  background: #121317;
  color: #ffffff;
  border: 1px solid #121317;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
  text-align: center;
  transition: background 0.15s, border-color 0.15s;
}

.aquila-buddy-btn:hover,
.aquila-buddy-btn-primary:hover {
  background: #2f3034;
  border-color: #2f3034;
}

.aquila-buddy-btn:disabled {
  background: #eff2f7;
  border-color: rgba(33, 34, 38, 0.08);
  color: #6a6a71;
  cursor: not-allowed;
}

.aquila-buddy-btn-secondary {
  background: #f8f9fc;
  border: 1px solid rgba(33, 34, 38, 0.12);
  color: #121317;
}

.aquila-buddy-btn-secondary:hover {
  background: #eff2f7;
  border-color: rgba(33, 34, 38, 0.18);
}

.aquila-buddy-result {
  margin-top: 12px;
  background: #f8f9fc;
  border: 1px solid rgba(33, 34, 38, 0.10);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aquila-buddy-result-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.aquila-buddy-result-label {
  font-size: 11px;
  font-weight: 600;
  color: #6a6a71;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0;
}

.aquila-buddy-result-text {
  font-size: 13px;
  color: #121317;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.aquila-buddy-result-warning {
  color: #b06000;
  background: #fef7e0;
  border: 1px solid rgba(176, 96, 0, 0.20);
  border-radius: 4px;
  padding: 6px 8px;
}

.aquila-buddy-file-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.aquila-buddy-file-item {
  font-size: 12px;
  color: #1a73e8;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  padding: 3px 8px;
  background: rgba(26, 115, 232, 0.10);
  border: 1px solid rgba(26, 115, 232, 0.18);
  border-radius: 4px;
  word-break: break-all;
}

.aquila-buddy-code-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #121317;
  font-size: 12px;
  line-height: 1.6;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  background: #ffffff;
  border: 1px solid rgba(33, 34, 38, 0.10);
  border-radius: 4px;
  padding: 10px 12px;
}

.aquila-buddy-field-group {
  margin-bottom: 12px;
}

.aquila-buddy-field-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #6a6a71;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 4px;
}

.aquila-buddy-field-value {
  font-size: 12px;
  color: #121317;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-variant-numeric: tabular-nums;
  margin: 0;
}

.aquila-buddy-input {
  width: 100%;
  padding: 6px 10px;
  background: #ffffff;
  border: 1px solid rgba(33, 34, 38, 0.14);
  border-radius: 6px;
  color: #121317;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  box-sizing: border-box;
}

.aquila-buddy-input:focus {
  border-color: #1a73e8;
  box-shadow: 0 0 0 2px rgba(26, 115, 232, 0.10);
}

.aquila-buddy-input::placeholder {
  color: #6a6a71;
}

.aquila-buddy-required {
  color: #c5221f;
}

.aquila-buddy-label-text {
  font-size: 11px;
  font-weight: 600;
  color: #6a6a71;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0 0 4px;
}

.aquila-buddy-draft-title {
  font-size: 13px;
  color: #121317;
  margin: 0;
  font-weight: 500;
}

.aquila-buddy-reason-card {
  background: #f8f9fc;
  border: 1px solid rgba(33, 34, 38, 0.10);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
}

.aquila-buddy-reason-text,
.aquila-buddy-summary-text {
  font-size: 13px;
  color: #45474d;
  line-height: 1.6;
  margin: 0;
}

.aquila-buddy-success {
  color: #137333;
  background: #e6f4ea;
  border: 1px solid rgba(19, 115, 51, 0.20);
  border-radius: 4px;
  padding: 6px 8px;
  font-weight: 500;
  margin-top: 4px;
}

.aquila-buddy-divider {
  border: none;
  border-top: 1px solid rgba(33, 34, 38, 0.10);
  margin: 0 0 16px;
}
`;

function getShadowStyles() {
  return SHADOW_CSS;
}

function createExtensionUI() {
  const root = document.createElement('aquila-buddy-root');
  root.id = 'aquila-buddy-root';
  const shadow = root.attachShadow({ mode: 'closed' });
  root._shadowRoot = shadow;

  const style = document.createElement('style');
  style.textContent = getShadowStyles();
  shadow.appendChild(style);

  const toggleBtn = document.createElement('button');
  toggleBtn.id = 'aquila-buddy-toggle';
  toggleBtn.textContent = 'AB';
  toggleBtn.title = 'Aquila Buddy 열기';

  const sidebar = document.createElement('div');
  sidebar.id = 'aquila-buddy-sidebar';
  sidebar.classList.add('aquila-buddy-hidden');
  sidebar.innerHTML = `
    <div id="aquila-buddy-header">
      <span>Aquila Buddy</span>
      <button id="aquila-buddy-close" aria-label="닫기">✕</button>
    </div>
    <div id="aquila-buddy-content">
      <p class="aquila-buddy-loading">불러오는 중...</p>
    </div>
  `;

  shadow.appendChild(sidebar);
  shadow.appendChild(toggleBtn);
  document.body.appendChild(root);

  shadow.getElementById('aquila-buddy-close')?.addEventListener('click', () => {
    sidebar.classList.add('aquila-buddy-hidden');
    toggleBtn.textContent = 'AB';
    toggleBtn.title = 'Aquila Buddy 열기';
  });

  toggleBtn.addEventListener('click', () => {
    const isHidden = sidebar.classList.contains('aquila-buddy-hidden');
    sidebar.classList.toggle('aquila-buddy-hidden');
    toggleBtn.textContent = isHidden ? '✕' : 'AB';
    toggleBtn.title = isHidden ? 'Aquila Buddy 닫기' : 'Aquila Buddy 열기';
    if (isHidden) {
      loadContent(shadow);
    }
  });

  return root;
}

// 페이지 타입에 따라 사이드바 콘텐츠를 로드합니다
function loadContent(shadow) {
  if (!shadow) {
    const root = typeof document.querySelector === 'function'
      ? document.querySelector('aquila-buddy-root')
      : document.getElementById?.('aquila-buddy-root');
    shadow = root?._shadowRoot;
    if (!shadow) return;
  }
  const pageType = detectPageType();
  const contentEl = shadow.getElementById('aquila-buddy-content');
  if (!contentEl || !pageType) return;

  contentEl.innerHTML = '<p class="aquila-buddy-loading">불러오는 중...</p>';

  switch (pageType) {
    case 'ISSUE_LIST':
      renderIssueList(contentEl);
      break;
    case 'ISSUE_DETAIL':
      renderIssueDetail(contentEl, shadow);
      break;
    case 'PR_CREATE':
      renderPrCreate(contentEl, shadow);
      break;
  }
}

// ── 이슈 목록: 캐시된 전역 추천에서 현재 레포 이슈를 필터링해 뱃지 표시 ───

function renderIssueList(el) {
  const repoFullName = location.pathname.replace(/^\//, '').replace(/\/issues\/?$/, '');
  el.innerHTML = '<p class="aquila-buddy-loading">이슈 분석 중...</p>';

  sendMessage({ type: 'GET_CACHED_RECOMMENDATIONS' }, (res) => {
    const allIssues = res?.data?.data ?? [];
    const repoIssues = allIssues.filter((i) => i.repoFullName === repoFullName);

    highlightRecommendedIssues(repoIssues);

    if (repoIssues.length === 0) {
      el.innerHTML = `
        <p class="aquila-buddy-empty">이 레포에 해당하는 추천 이슈가 없습니다.</p>
        <p class="aquila-buddy-desc aquila-buddy-desc-followup">Extension 아이콘 → 새 추천 받기를 눌러 추천을 업데이트하세요.</p>
      `;
      return;
    }

    el.innerHTML = `
      <h3 class="aquila-buddy-section-title">이슈 매칭 결과</h3>
      <p class="aquila-buddy-desc">
        이 레포에서 <strong class="aquila-buddy-count">${repoIssues.length}개</strong>의 이슈가 나와 잘 맞습니다.<br>
        <span class="aquila-buddy-inline-badge">Aquila Buddy</span> 뱃지가 표시된 이슈를 확인하세요.
      </p>
      <div class="aquila-buddy-issue-list">
        ${repoIssues
          .map(
            (issue) => `
          <div class="aquila-buddy-issue-card">
            <a href="https://github.com/${escapeHtml(issue.repoFullName)}/issues/${issue.issueNumber}" target="_blank">
              #${issue.issueNumber}
            </a>
            <p>${escapeHtml(issue.title)}</p>
            <div class="aquila-buddy-labels">
              ${(issue.labels ?? []).map((l) => `<span class="aquila-buddy-label">${escapeHtml(l)}</span>`).join('')}
            </div>
          </div>`
          )
          .join('')}
      </div>
    `;
  });
}

// 추천 이슈 번호와 일치하는 GitHub 이슈 행에 뱃지를 직접 삽입합니다
function highlightRecommendedIssues(issues) {
  const numberSet = new Set(issues.map((i) => String(i.issueNumber)));
  const pathSegments = location.pathname.split('/').filter(Boolean);
  const repoPrefix = `/${pathSegments.slice(0, 2).join('/')}/issues/`;

  document.querySelectorAll('.aquila-buddy-badge').forEach((badge) => {
    const link = badge.closest('a');
    const match = link?.getAttribute('href')?.match(/\/issues\/(\d+)\/?$/);
    if (!match || !numberSet.has(match[1])) {
      badge.remove();
    }
  });

  document.querySelectorAll(`a[href^="${repoPrefix}"]`).forEach((link) => {
    const match = link.getAttribute('href')?.match(/\/issues\/(\d+)\/?$/);
    if (!match || !numberSet.has(match[1])) return;
    if (link.querySelector('.aquila-buddy-badge')) return;

    const badge = document.createElement('span');
    badge.className = 'aquila-buddy-badge';
    badge.textContent = 'Aquila Buddy';
    link.appendChild(badge);
  });
}

// ── 이슈 상세: 코드 가이드 + 의사 코드 ─────────────────────────────────────

function requestAnalysis(type, loadingText, repoFullName, issueNumber, shadow) {
  const resultEl = shadow.getElementById('aquila-buddy-analysis-result');
  const guideBtn = shadow.getElementById('aquila-buddy-guide-btn');
  const pseudoBtn = shadow.getElementById('aquila-buddy-pseudo-btn');

  // 요청 중 두 버튼 모두 비활성화
  if (guideBtn) guideBtn.disabled = true;
  if (pseudoBtn) pseudoBtn.disabled = true;
  if (resultEl) {
    resultEl.innerHTML = `<p class="aquila-buddy-loading">${escapeHtml(loadingText)} (수 분이 걸릴 수 있습니다)</p>`;
  }

  sendMessage({ type, repoFullName, issueNumber }, (res) => {
    if (guideBtn) guideBtn.disabled = false;
    if (pseudoBtn) pseudoBtn.disabled = false;
    if (resultEl) {
      resultEl.innerHTML = res?.ok
        ? renderAnalysisResult(res.data?.data)
        : renderError(res?.error);
    }
  });
}

function renderRecommendationReason(matched) {
  if (!matched) {
    return '';
  }

  const summary = typeof matched.summary === 'string' ? matched.summary.trim() : '';
  const reason = typeof matched.reason === 'string' ? matched.reason.trim() : '';

  if (summary && reason && summary !== reason) {
    return `
      <h3 class="aquila-buddy-section-title">이슈 요약</h3>
      <div class="aquila-buddy-reason-card">
        <p class="aquila-buddy-summary-text">${escapeHtml(summary)}</p>
      </div>
      <h3 class="aquila-buddy-section-title">추천 이유</h3>
      <div class="aquila-buddy-reason-card">
        <p class="aquila-buddy-reason-text">${escapeHtml(reason)}</p>
      </div>
      <hr class="aquila-buddy-divider">
    `;
  }

  const reasonText = reason || summary || '추천 사유 정보가 없습니다.';
  return `
    <h3 class="aquila-buddy-section-title">추천 이유</h3>
    <div class="aquila-buddy-reason-card">
      <p class="aquila-buddy-reason-text">${escapeHtml(reasonText)}</p>
    </div>
    <hr class="aquila-buddy-divider">
  `;
}

function renderIssueDetail(el, shadow) {
  const cleanPath = location.pathname.replace(/\/+$/, '');
  const pathParts = cleanPath.split('/');
  const repoFullName = `${pathParts[1]}/${pathParts[2]}`;
  const issueNumber = pathParts[pathParts.length - 1];

  el.innerHTML = `
    <div id="aquila-buddy-rec-reason"></div>
    <h3 class="aquila-buddy-section-title">이슈 분석</h3>
    <p class="aquila-buddy-desc">이슈 #${escapeHtml(issueNumber)}에 대한 AI 분석을 제공합니다.</p>
    <button class="aquila-buddy-btn aquila-buddy-btn-primary" id="aquila-buddy-guide-btn">코드 수정 가이드 보기</button>
    <button class="aquila-buddy-btn aquila-buddy-btn-secondary" id="aquila-buddy-pseudo-btn">의사 코드 보기</button>
    <div id="aquila-buddy-analysis-result"></div>
  `;

  sendMessage({ type: 'GET_CACHED_RECOMMENDATIONS' }, (res) => {
    const allIssues = res?.data?.data ?? [];
    const matched = allIssues.find(
      (i) => i.repoFullName === repoFullName && String(i.issueNumber) === issueNumber
    );
    const recReasonEl = shadow.getElementById('aquila-buddy-rec-reason');
    if (recReasonEl && matched) {
      recReasonEl.innerHTML = renderRecommendationReason(matched);
    }
  });

  shadow.getElementById('aquila-buddy-guide-btn')?.addEventListener('click', () => {
    requestAnalysis('GET_ISSUE_GUIDE', '분석 중...', repoFullName, issueNumber, shadow);
  });

  shadow.getElementById('aquila-buddy-pseudo-btn')?.addEventListener('click', () => {
    requestAnalysis('GET_ISSUE_PSEUDO', '의사 코드 생성 중...', repoFullName, issueNumber, shadow);
  });
}

function renderAnalysisResult(data) {
  if (!data) return `<p class="aquila-buddy-empty">결과가 없습니다.</p>`;

  const filePathsHtml = (data.filePaths ?? []).length > 0
    ? `<div class="aquila-buddy-result-section">
        <p class="aquila-buddy-result-label">수정 대상 파일</p>
        <ul class="aquila-buddy-file-list">
          ${data.filePaths.map((p) => `<li class="aquila-buddy-file-item">${escapeHtml(p)}</li>`).join('')}
        </ul>
       </div>`
    : '';

  if (data.guideline != null) {
    return `
      <div class="aquila-buddy-result">
        ${filePathsHtml}
        <div class="aquila-buddy-result-section">
          <p class="aquila-buddy-result-label">수정 방향</p>
          <p class="aquila-buddy-result-text">${escapeHtml(data.guideline)}</p>
        </div>
        <div class="aquila-buddy-result-section">
          <p class="aquila-buddy-result-label">주의 사항</p>
          <p class="aquila-buddy-result-text aquila-buddy-result-warning">${escapeHtml(formatSideEffects(data.sideEffects))}</p>
        </div>
      </div>`;
  }

  if (data.pseudoCode != null) {
    return `
      <div class="aquila-buddy-result">
        ${filePathsHtml}
        <div class="aquila-buddy-result-section">
          <p class="aquila-buddy-result-label">의사 코드</p>
          <pre class="aquila-buddy-code-block">${escapeHtml(data.pseudoCode)}</pre>
        </div>
      </div>`;
  }

  return `<p class="aquila-buddy-empty">결과가 없습니다.</p>`;
}

function formatSideEffects(sideEffects) {
  if (Array.isArray(sideEffects)) {
    return sideEffects.join('\n');
  }

  return sideEffects ?? '';
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

// ── PR 생성: PR 초안 자동 생성 ──────────────────────────────────────────────

function renderPrCreate(el, shadow) {
  const pathParts = location.pathname.split('/');
  const upstreamRepo = `${pathParts[1]}/${pathParts[2]}`;

  // URL에서 baseBranch, headBranch 파싱
  // 형식: /owner/repo/compare/baseBranch...forkOwner:headBranch
  const comparePart = location.pathname.split('/compare/')[1] ?? '';
  const [rawBase, rawHead] = comparePart.split('...');
  const baseBranch = rawBase ?? '';
  const headBranch = rawHead?.includes(':') ? (rawHead.split(':').at(-1) ?? '') : (rawHead ?? '');

  el.innerHTML = `
    <h3 class="aquila-buddy-section-title">PR 초안 생성</h3>
    <p class="aquila-buddy-desc">diff를 분석해 PR 제목과 본문을 자동으로 생성합니다.</p>
    <div class="aquila-buddy-field-group">
      <label class="aquila-buddy-field-label">레포지토리</label>
      <p class="aquila-buddy-field-value">${escapeHtml(upstreamRepo)}</p>
    </div>
    <div class="aquila-buddy-field-group">
      <label class="aquila-buddy-field-label">브랜치</label>
      <p class="aquila-buddy-field-value">${escapeHtml(baseBranch || '—')} → ${escapeHtml(headBranch || '—')}</p>
    </div>
    <div class="aquila-buddy-field-group">
      <label class="aquila-buddy-field-label" for="aquila-buddy-issue-number">연결할 이슈 번호 <span class="aquila-buddy-required">*</span></label>
      <input class="aquila-buddy-input" id="aquila-buddy-issue-number" type="number" min="1" placeholder="예: 42" />
    </div>
    <button class="aquila-buddy-btn aquila-buddy-btn-primary" id="aquila-buddy-draft-btn">초안 생성</button>
    <div id="aquila-buddy-draft-result"></div>
  `;

  shadow.getElementById('aquila-buddy-draft-btn')?.addEventListener('click', () => {
    const issueNumberInput = shadow.getElementById('aquila-buddy-issue-number');
    const githubIssueNumber = parseInt(issueNumberInput?.value ?? '', 10);
    const resultEl = shadow.getElementById('aquila-buddy-draft-result');

    if (!githubIssueNumber || githubIssueNumber < 1) {
      if (resultEl) resultEl.innerHTML = `<p class="aquila-buddy-error">이슈 번호를 입력해주세요.</p>`;
      return;
    }
    if (!baseBranch || !headBranch) {
      if (resultEl) resultEl.innerHTML = `<p class="aquila-buddy-error">브랜치 정보를 URL에서 읽을 수 없습니다.<br>GitHub compare 페이지에서 사용해주세요.</p>`;
      return;
    }

    if (resultEl) resultEl.innerHTML = '<p class="aquila-buddy-loading">생성 중... (수 분이 걸릴 수 있습니다)</p>';

    sendMessage(
      {
        type: 'CREATE_PR_DRAFT',
        payload: { upstreamRepo, githubIssueNumber, baseBranch, headBranch },
      },
      (res) => {
        if (!res?.ok) {
          if (resultEl) resultEl.innerHTML = renderError(res?.error);
          return;
        }
        const draft = res.data?.data;
        if (resultEl) {
          resultEl.innerHTML = `
            <div class="aquila-buddy-result">
              <div class="aquila-buddy-result-section">
                <p class="aquila-buddy-result-label">제목</p>
                <p class="aquila-buddy-draft-title">${escapeHtml(draft?.title)}</p>
              </div>
              <div class="aquila-buddy-result-section">
                <p class="aquila-buddy-result-label">본문</p>
                <pre class="aquila-buddy-code-block">${escapeHtml(draft?.body)}</pre>
              </div>
              <button class="aquila-buddy-btn aquila-buddy-btn-primary" id="aquila-buddy-apply-btn">PR 폼에 적용</button>
              <div id="aquila-buddy-translate-status"></div>
            </div>
          `;
          shadow.getElementById('aquila-buddy-apply-btn')?.addEventListener('click', () => {
            translateAndApplyDraft(draft?.id, shadow.getElementById('aquila-buddy-translate-status'), shadow);
          });
        }
      }
    );
  });
}

function applyDraftToPrForm(title, body) {
  const titleInput = document.querySelector(
    '#pull_request_title, input[name="pull_request[title]"], input[aria-label="Title"], input[aria-label="Pull request title"]'
  );
  const bodyInput = document.querySelector(
    '#pull_request_body, textarea[name="pull_request[body]"], textarea[aria-label="Comment body"], textarea[aria-label="Pull request body"]'
  );
  if (titleInput && title != null && title !== '') {
    titleInput.value = title;
    titleInput.dispatchEvent(new Event('input', { bubbles: true }));
    titleInput.dispatchEvent(new Event('change', { bubbles: true }));
  }
  if (bodyInput && body != null && body !== '') {
    bodyInput.value = body;
    bodyInput.dispatchEvent(new Event('input', { bubbles: true }));
    bodyInput.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

function translateAndApplyDraft(draftId, statusEl, shadow) {
  if (!draftId) {
    if (statusEl) statusEl.innerHTML = renderError('초안 ID가 없습니다.');
    return;
  }

  const applyBtn = shadow.getElementById('aquila-buddy-apply-btn');
  if (applyBtn) applyBtn.disabled = true;
  if (statusEl) statusEl.innerHTML = '<p class="aquila-buddy-loading">번역 중...</p>';

  sendMessage({ type: 'TRANSLATE_PR_DRAFT', draftId }, (res) => {
    if (applyBtn) applyBtn.disabled = false;
    if (!res?.ok) {
      if (statusEl) statusEl.innerHTML = renderError(res?.error);
      return;
    }
    const translated = res.data?.data;
    applyDraftToPrForm(translated?.titleEn ?? translated?.title, translated?.bodyEn ?? translated?.body);
    if (statusEl) statusEl.innerHTML = '<p class="aquila-buddy-desc aquila-buddy-success">번역 후 PR 폼에 적용됐습니다.</p>';
  });
}

// ── 공통 ─────────────────────────────────────────────────────────────────────

function renderError(error) {
  if (error === 'NOT_AUTHENTICATED') {
    return `<p class="aquila-buddy-error">로그인이 필요합니다.<br>Extension 아이콘을 클릭해 로그인해주세요.</p>`;
  }
  if (error === 'TOKEN_EXPIRED') {
    return `<p class="aquila-buddy-error">세션이 만료됐습니다.<br>Extension 아이콘을 클릭해 다시 로그인해주세요.</p>`;
  }
  if (error === 'ISSUE_NOT_IN_DB') {
    return `<p class="aquila-buddy-error">이 이슈는 Aquila Buddy에 등록되지 않았습니다.<br>추천 이슈 목록에서 접근해주세요.</p>`;
  }
  if (error === 'CONTEXT_INVALIDATED') {
    return `<p class="aquila-buddy-error">Extension이 업데이트됐습니다.<br>페이지를 새로고침해주세요. (F5)</p>`;
  }
  return `<p class="aquila-buddy-error">오류가 발생했습니다.<br>${escapeHtml(error ?? '알 수 없는 오류')}</p>`;
}

// ── 진입점 ───────────────────────────────────────────────────────────────────

// GitHub은 Turbo SPA를 사용하므로 페이지 이동 시 content script가 재실행되지 않습니다.
// turbo:load, turbo:render, popstate 이벤트로 URL 변경을 감지해 사이드바를 재초기화합니다.

function init() {
  if (typeof document.querySelectorAll === 'function') {
    document.querySelectorAll('aquila-buddy-root').forEach((el) => el.remove());
  } else if (typeof document.querySelector === 'function') {
    document.querySelector('aquila-buddy-root')?.remove();
  } else if (typeof document.getElementById === 'function') {
    document.getElementById('aquila-buddy-root')?.remove();
  }
  document.getElementById?.('aquila-buddy-sidebar')?.remove();
  document.getElementById?.('aquila-buddy-toggle')?.remove();

  const pageType = detectPageType();
  if (pageType !== 'ISSUE_LIST' && typeof document.querySelectorAll === 'function') {
    document.querySelectorAll('.aquila-buddy-badge').forEach((b) => b.remove());
  }
  if (pageType) {
    createExtensionUI();
  }
}

init();
document.addEventListener('turbo:load', init);
document.addEventListener('turbo:render', init);
window.addEventListener('popstate', init);
document.addEventListener('turbo:before-cache', () => {
  if (typeof document.querySelectorAll === 'function') {
    document.querySelectorAll('aquila-buddy-root').forEach((el) => el.remove());
  }
});

chrome.runtime.onMessage.addListener((message) => {
  if (message.type === 'AUTH_SUCCESS') {
    const root = typeof document.querySelector === 'function'
      ? document.querySelector('aquila-buddy-root')
      : document.getElementById?.('aquila-buddy-root');
    if (root?._shadowRoot) {
      loadContent(root._shadowRoot);
    }
  }
});
