const domFrameQueue = (() => {
  const readQueue = [];
  const writeQueue = [];
  let frameId = 0;
  let scheduled = false;
  let flushing = false;
  let generation = 0;

  function drain(queue, runGeneration) {
    const batch = queue.splice(0, queue.length);
    for (let i = 0; i < batch.length; i++) {
      if (generation !== runGeneration) break;
      try {
        batch[i]();
      } catch {
        // Isolate callback failure so remaining tasks and scheduler state remain valid.
      }
    }
  }

  function flush() {
    frameId = 0;
    scheduled = false;
    flushing = true;
    const runGeneration = generation;
    try {
      drain(readQueue, runGeneration);
      if (generation === runGeneration) {
        drain(writeQueue, runGeneration);
      }
    } finally {
      flushing = false;
      if ((readQueue.length > 0 || writeQueue.length > 0) && !scheduled) {
        scheduleFlush();
      }
    }
  }

  function scheduleFlush() {
    if (scheduled || flushing) return;
    if (typeof requestAnimationFrame !== 'function') {
      scheduled = true;
      flush();
      return;
    }
    scheduled = true;
    frameId = requestAnimationFrame(flush);
  }

  return {
    read(fn) {
      if (typeof fn !== 'function') return;
      readQueue.push(fn);
      scheduleFlush();
    },
    write(fn) {
      if (typeof fn !== 'function') return;
      writeQueue.push(fn);
      scheduleFlush();
    },
    cancelAll() {
      generation += 1;
      if (frameId && typeof cancelAnimationFrame === 'function') {
        cancelAnimationFrame(frameId);
      }
      frameId = 0;
      scheduled = false;
      readQueue.length = 0;
      writeQueue.length = 0;
    },
  };
})();

const DEFAULT_SIDEBAR_WIDTH = 340;
const MIN_SIDEBAR_WIDTH = 280;
const MAX_SIDEBAR_WIDTH = 680;
let isSidebarOpen = false;
let currentSidebarWidth = DEFAULT_SIDEBAR_WIDTH;
let badgeRefreshPending = false;

function getActiveRoot() {
  return typeof document.querySelector === 'function'
    ? document.querySelector('aquila-buddy-root')
    : document.getElementById?.('aquila-buddy-root');
}

function clampSidebarWidth(width) {
  const viewportLimit =
    typeof window !== 'undefined' && typeof window.innerWidth === 'number' && window.innerWidth > 0
      ? Math.floor(window.innerWidth * 0.75)
      : MAX_SIDEBAR_WIDTH;
  const maxWidth = Math.max(MIN_SIDEBAR_WIDTH, Math.min(MAX_SIDEBAR_WIDTH, viewportLimit));
  return Math.max(MIN_SIDEBAR_WIDTH, Math.min(maxWidth, Math.round(width)));
}

function applySidebarWidth(sidebar, width) {
  const clamped = clampSidebarWidth(width);
  currentSidebarWidth = clamped;
  sidebar?.style?.setProperty?.('--ab-sidebar-width', `${clamped}px`);
  return clamped;
}

// Extension 새로고침 후 context가 무효화되면 sendMessage가 에러를 던집니다.
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

function detectPageType() {
  const path = location.pathname;
  if (/^\/[^/]+\/[^/]+\/issues\/?$/.test(path)) return 'ISSUE_LIST';
  if (/^\/[^/]+\/[^/]+\/issues\/\d+\/?$/.test(path)) return 'ISSUE_DETAIL';
  if (/^\/[^/]+\/[^/]+\/compare/.test(path)) return 'PR_CREATE';
  return null;
}

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
:host { all: initial; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans KR', Helvetica, Arial, sans-serif; color: #1f2328; font-size: 13px; line-height: 1.5; font-variant-numeric: tabular-nums; -webkit-font-smoothing: antialiased; }
*, *::before, *::after { box-sizing: border-box; }
#aquila-buddy-toggle { position: fixed; right: 0; top: 50%; transform: translateY(-50%); z-index: 9999; background: #1f2328; color: #ffffff; border: 1px solid #d1d9e0; border-right: none; border-radius: 6px 0 0 6px; width: 32px; height: 48px; font-size: 11px; font-weight: 600; letter-spacing: -0.02em; cursor: pointer; transition: background 0.15s; box-shadow: -2px 0 8px rgba(31, 35, 40, 0.08); }
#aquila-buddy-toggle:hover { background: #0969da; }
#aquila-buddy-toggle:focus-visible { outline: 2px solid #0969da; outline-offset: 2px; }
#aquila-buddy-sidebar { position: fixed; right: 0; top: 0; width: var(--ab-sidebar-width, 340px); min-width: 280px; max-width: 680px; height: 100vh; background: #ffffff; color: #1f2328; border-left: 1px solid #d1d9e0; z-index: 9998; display: flex; flex-direction: column; font-size: 13px; font-variant-numeric: tabular-nums; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans KR', Helvetica, Arial, sans-serif; transition: transform 0.2s ease; box-shadow: -4px 0 16px rgba(31, 35, 40, 0.08); }
#aquila-buddy-sidebar.aquila-buddy-hidden { transform: translateX(100%); }
#aquila-buddy-resizer { position: absolute; left: 0; top: 0; bottom: 0; width: 5px; cursor: ew-resize; z-index: 2; background: transparent; transition: background 0.12s; }
#aquila-buddy-resizer:hover, #aquila-buddy-resizer.is-resizing, #aquila-buddy-resizer:focus-visible { background: #0969da; outline: none; }
#aquila-buddy-header, .aquila-buddy-sidebar-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; background: #f6f8fa; border-bottom: 1px solid #d1d9e0; color: #1f2328; font-weight: 600; font-size: 13.5px; letter-spacing: -0.02em; flex-shrink: 0; }
.aquila-buddy-context-bar { padding: 6px 14px; background: #f6f8fa; border-bottom: 1px solid #d1d9e0; color: #59636e; font-size: 11.5px; font-weight: 500; font-variant-numeric: tabular-nums; }
#aquila-buddy-close { background: transparent; border: 1px solid transparent; border-radius: 6px; color: #59636e; cursor: pointer; font-size: 13px; width: 26px; height: 26px; padding: 0; line-height: 1; display: inline-flex; align-items: center; justify-content: center; transition: background 0.15s, border-color 0.15s, color 0.15s; }
#aquila-buddy-close:hover { background: #eff2f5; border-color: #d1d9e0; color: #1f2328; }
#aquila-buddy-content { padding: 14px; overflow-y: auto; flex: 1; background: #ffffff; }
.aquila-buddy-loading, .aquila-buddy-empty { color: #59636e; font-size: 12.5px; margin: 0; }
.aquila-buddy-error { color: #d1242f; background: #ffebe9; border: 1px solid rgba(255, 129, 130, 0.4); border-radius: 6px; padding: 8px 10px; font-size: 12px; font-weight: 500; line-height: 1.6; margin: 0; }
.aquila-buddy-section-title { font-size: 13px; font-weight: 600; color: #1f2328; letter-spacing: -0.015em; margin: 0 0 8px; }
.aquila-buddy-desc { font-size: 12.5px; font-weight: 400; color: #59636e; line-height: 1.6; margin: 0 0 12px; }
.aquila-buddy-desc-followup { margin-top: 8px; }
.aquila-buddy-count { color: #1f2328; font-weight: 600; font-variant-numeric: tabular-nums; }
.aquila-buddy-inline-badge { display: inline-flex; align-items: center; padding: 1px 8px; background: #dafbe1; border: 1px solid rgba(26, 127, 55, 0.3); border-radius: 9999px; color: #1a7f37; font-size: 11px; font-weight: 600; font-variant-numeric: tabular-nums; }
.aquila-buddy-card, .aquila-buddy-issue-list { margin-top: 10px; border: 1px solid #d1d9e0; border-radius: 6px; background: #ffffff; overflow: hidden; }
.aquila-buddy-issue-card { background: #ffffff; border-left: 3px solid transparent; border-bottom: 1px solid #d1d9e0; padding: 10px 12px; margin: 0; transition: background 0.12s, border-left-color 0.12s; }
.aquila-buddy-issue-card:last-child { border-bottom: none; }
.aquila-buddy-issue-card:hover { background: #f6f8fa; border-left-color: #0969da; }
.aquila-buddy-issue-card-top { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.aquila-buddy-issue-card a { color: #0969da; text-decoration: none; font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
.aquila-buddy-issue-card a:hover { text-decoration: underline; }
.aquila-buddy-issue-card p { font-size: 13.5px; font-weight: 600; letter-spacing: -0.01em; margin: 4px 0 6px; color: #1f2328; line-height: 1.4; }
.aquila-buddy-labels { display: flex; flex-wrap: wrap; gap: 4px; }
.aquila-buddy-label { display: inline-block; background: #f6f8fa; border: 1px solid #d1d9e0; border-radius: 9999px; padding: 1px 7px; font-size: 11px; font-weight: 500; color: #59636e; font-variant-numeric: tabular-nums; }
.aquila-buddy-btn, .aquila-buddy-btn-primary { display: block; width: 100%; padding: 7px 12px; background: #1f883d; color: #ffffff; border: 1px solid rgba(31, 35, 40, 0.15); border-radius: 6px; cursor: pointer; font-size: 12.5px; font-weight: 600; margin-bottom: 8px; text-align: center; transition: background 0.15s, border-color 0.15s; }
.aquila-buddy-btn:hover, .aquila-buddy-btn-primary:hover { background: #1a7f37; }
.aquila-buddy-btn:disabled { background: #eff2f5; border-color: #d1d9e0; color: #818b98; cursor: not-allowed; }
.aquila-buddy-btn-secondary { background: #f6f8fa; border: 1px solid #d1d9e0; color: #1f2328; font-weight: 500; }
.aquila-buddy-btn-secondary:hover { background: #eff2f5; border-color: #b9c3ce; color: #1f2328; }
.aquila-buddy-result { margin-top: 12px; background: #ffffff; border: 1px solid #d1d9e0; border-radius: 6px; overflow: hidden; display: flex; flex-direction: column; }
.aquila-buddy-result-section { display: flex; flex-direction: column; gap: 6px; padding: 10px 12px; border-bottom: 1px solid #d1d9e0; }
.aquila-buddy-result-section:last-child { border-bottom: none; }
.aquila-buddy-result-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.aquila-buddy-copy-btn { padding: 2px 8px; font-size: 11px; font-weight: 500; color: #1f2328; background: #f6f8fa; border: 1px solid #d1d9e0; border-radius: 6px; cursor: pointer; }
.aquila-buddy-copy-btn:hover { background: #eff2f5; border-color: #b9c3ce; }
.aquila-buddy-score-badge { display: inline-flex; align-items: center; padding: 1px 7px; background: #ddf4ff; border: 1px solid rgba(9, 105, 218, 0.25); border-radius: 9999px; color: #0969da; font-size: 11px; font-weight: 600; font-variant-numeric: tabular-nums; }
.aquila-buddy-result-label { font-size: 12.5px; font-weight: 600; color: #1f2328; letter-spacing: -0.01em; margin: 0; }
.aquila-buddy-result-text { font-size: 13px; font-weight: 400; color: #1f2328; line-height: 1.6; margin: 0; white-space: pre-wrap; word-break: break-word; }
.aquila-buddy-result-warning { color: #9a6700; background: #fff8c5; border: 1px solid rgba(154, 103, 0, 0.25); border-radius: 6px; padding: 8px 10px; }
.aquila-buddy-file-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.aquila-buddy-file-item { font-size: 12px; font-weight: 500; color: #0969da; padding: 4px 8px; background: #ddf4ff; border: 1px solid rgba(9, 105, 218, 0.2); border-radius: 6px; font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; word-break: break-all; }
.aquila-buddy-code-block { margin: 0; white-space: pre-wrap; word-break: break-word; color: #1f2328; font-size: 12px; line-height: 1.6; font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; background: #f6f8fa; border: 1px solid #d1d9e0; border-radius: 6px; padding: 8px 10px; }
.aquila-buddy-field-group { margin-bottom: 12px; }
.aquila-buddy-field-label { display: block; font-size: 12px; font-weight: 600; color: #59636e; letter-spacing: -0.01em; margin-bottom: 4px; }
.aquila-buddy-field-value { font-size: 12px; color: #1f2328; font-variant-numeric: tabular-nums; margin: 0; font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace; }
.aquila-buddy-input { width: 100%; padding: 6px 10px; background: #ffffff; border: 1px solid #d1d9e0; border-radius: 6px; color: #1f2328; font-size: 13px; font-variant-numeric: tabular-nums; outline: none; transition: border-color 0.15s, box-shadow 0.15s; box-sizing: border-box; }
.aquila-buddy-input:focus { border-color: #0969da; box-shadow: 0 0 0 3px rgba(9, 105, 218, 0.15); }
.aquila-buddy-input::placeholder { color: #59636e; }
.aquila-buddy-required { color: #d1242f; }
.aquila-buddy-label-text { font-size: 12px; font-weight: 600; color: #59636e; letter-spacing: -0.01em; margin: 0 0 4px; }
.aquila-buddy-draft-title { font-size: 13.5px; color: #1f2328; margin: 0; font-weight: 600; }
.aquila-buddy-reason-card { background: #f6f8fa; border: 1px solid #d1d9e0; border-left: 3px solid #0969da; border-radius: 6px; padding: 10px 12px; margin-bottom: 12px; }
.aquila-buddy-reason-text, .aquila-buddy-summary-text { font-size: 13px; font-weight: 400; color: #1f2328; line-height: 1.6; margin: 0; }
.aquila-buddy-success { color: #1a7f37; background: #dafbe1; border: 1px solid rgba(26, 127, 55, 0.3); border-radius: 6px; padding: 6px 8px; font-weight: 500; margin-top: 4px; }
.aquila-buddy-divider { border: none; border-top: 1px solid #d1d9e0; margin: 0 0 14px; }
`;

function getShadowStyles() {
  return SHADOW_CSS;
}

function setSidebarOpen(sidebar, toggleBtn, open) {
  isSidebarOpen = open;
  sidebar.classList.toggle('aquila-buddy-hidden', !open);
  toggleBtn.textContent = open ? '✕' : 'AB';
  const label = open ? 'Aquila Buddy 닫기' : 'Aquila Buddy 열기';
  toggleBtn.title = label;
  toggleBtn.setAttribute?.('aria-label', label);
  toggleBtn.setAttribute?.('aria-expanded', open ? 'true' : 'false');
}

function updateContextBar(shadow, pageType) {
  const bar = shadow.getElementById('aquila-buddy-context-bar');
  if (!bar) return;
  const parts = location.pathname.split('/').filter(Boolean);
  const repoFullName = parts.length >= 2 ? `${parts[0]}/${parts[1]}` : '';
  const modeLabel =
    pageType === 'ISSUE_LIST'
      ? '이슈 목록'
      : pageType === 'ISSUE_DETAIL'
      ? `이슈 #${parts[3] ?? ''}`
      : pageType === 'PR_CREATE'
      ? 'PR 초안'
      : 'GitHub';
  bar.textContent = repoFullName ? `${repoFullName} / ${modeLabel}` : modeLabel;
}

function copyTextWithFeedback(button, text) {
  if (!text) return;
  const original = button.textContent;
  const markCopied = () => {
    button.textContent = '복사됨';
    setTimeout(() => {
      button.textContent = original;
    }, 1500);
  };
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text).then(markCopied).catch(() => {});
  }
}

function bindSidebarResizer(resizer, sidebar) {
  if (!resizer || typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
    return;
  }

  let rafId = 0;
  let pendingWidth = currentSidebarWidth;

  const persistWidth = (width) => {
    if (typeof chrome !== 'undefined' && chrome.storage?.local?.set) {
      chrome.storage.local.set({ sidebarWidth: width }).catch?.(() => {});
    }
  };

  resizer.addEventListener?.('pointerdown', (event) => {
    if (event.button !== 0) return;
    event.preventDefault?.();
    resizer.classList?.add('is-resizing');
    resizer.setPointerCapture?.(event.pointerId);

    const onMove = (moveEvent) => {
      const vw = window.innerWidth || 1280;
      pendingWidth = clampSidebarWidth(vw - moveEvent.clientX);
      if (!rafId && typeof requestAnimationFrame === 'function') {
        rafId = requestAnimationFrame(() => {
          rafId = 0;
          applySidebarWidth(sidebar, pendingWidth);
        });
      } else if (typeof requestAnimationFrame !== 'function') {
        applySidebarWidth(sidebar, pendingWidth);
      }
    };

    const onEnd = () => {
      if (rafId && typeof cancelAnimationFrame === 'function') {
        cancelAnimationFrame(rafId);
        rafId = 0;
      }
      const saved = applySidebarWidth(sidebar, pendingWidth);
      resizer.classList?.remove('is-resizing');
      persistWidth(saved);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
  });

  resizer.addEventListener?.('keydown', (event) => {
    const step = event.shiftKey ? 32 : 16;
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      persistWidth(applySidebarWidth(sidebar, currentSidebarWidth + step));
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      persistWidth(applySidebarWidth(sidebar, currentSidebarWidth - step));
    }
  });
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
  toggleBtn.type = 'button';

  const sidebar = document.createElement('div');
  sidebar.id = 'aquila-buddy-sidebar';
  sidebar.innerHTML = `
    <div id="aquila-buddy-resizer" role="separator" aria-orientation="vertical" aria-label="사이드바 너비 조절" tabindex="0"></div>
    <div id="aquila-buddy-header" class="aquila-buddy-sidebar-header">
      <span>Aquila Buddy</span>
      <button id="aquila-buddy-close" type="button" aria-label="닫기">✕</button>
    </div>
    <div id="aquila-buddy-context-bar" class="aquila-buddy-context-bar"></div>
    <div id="aquila-buddy-content">
      <p class="aquila-buddy-loading">불러오는 중...</p>
    </div>
  `;

  shadow.appendChild(sidebar);
  shadow.appendChild(toggleBtn);
  applySidebarWidth(sidebar, currentSidebarWidth);
  setSidebarOpen(sidebar, toggleBtn, isSidebarOpen);
  const pageType = detectPageType();
  updateContextBar(shadow, pageType);
  document.body.appendChild(root);

  if (typeof chrome !== 'undefined' && chrome.storage?.local?.get) {
    chrome.storage.local.get('sidebarWidth', (stored) => {
      if (typeof stored?.sidebarWidth === 'number' && Number.isFinite(stored.sidebarWidth)) {
        applySidebarWidth(sidebar, stored.sidebarWidth);
      }
    });
  }

  bindSidebarResizer(shadow.getElementById?.('aquila-buddy-resizer'), sidebar);

  shadow.getElementById('aquila-buddy-close')?.addEventListener('click', () => {
    setSidebarOpen(sidebar, toggleBtn, false);
  });

  toggleBtn.addEventListener('click', () => {
    const nextOpen = !isSidebarOpen;
    setSidebarOpen(sidebar, toggleBtn, nextOpen);
    if (nextOpen) {
      loadContent(shadow);
    }
  });

  shadow.addEventListener('click', (event) => {
    const target = event.target?.closest?.('.aquila-buddy-copy-btn');
    if (!target) return;
    const selector = target.getAttribute('data-copy-selector');
    if (!selector) return;
    const source = shadow.querySelector(selector);
    if (!source) return;
    const text =
      selector === '.aquila-buddy-file-list'
        ? Array.from(source.querySelectorAll('.aquila-buddy-file-item')).map((el) => el.textContent).join('\n')
        : source.textContent;
    copyTextWithFeedback(target, (text ?? '').trim());
  });

  if (isSidebarOpen) {
    loadContent(shadow);
  } else if (pageType === 'ISSUE_LIST') {
    refreshRecommendedBadgesForCurrentRepo();
  }

  return root;
}

function refreshRecommendedBadgesForCurrentRepo() {
  if (detectPageType() !== 'ISSUE_LIST') return;
  const repoFullName = location.pathname.replace(/^\//, '').replace(/\/issues\/?$/, '');
  sendMessage({ type: 'GET_CACHED_RECOMMENDATIONS' }, (res) => {
    const allIssues = res?.data?.data ?? [];
    highlightRecommendedIssues(allIssues.filter((i) => i.repoFullName === repoFullName));
  });
}

function loadContent(shadow) {
  if (!shadow) {
    shadow = getActiveRoot()?._shadowRoot;
    if (!shadow) return;
  }
  const pageType = detectPageType();
  const contentEl = shadow.getElementById('aquila-buddy-content');
  if (!contentEl || !pageType) return;
  updateContextBar(shadow, pageType);

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

function formatMatchPercent(score) {
  if (typeof score !== 'number' || !Number.isFinite(score) || score <= 0) return 0;
  return score <= 1 ? Math.round(score * 100) : Math.round(score);
}

function clearInjectedBadges() {
  if (typeof document.querySelectorAll !== 'function') return;
  domFrameQueue.write(() => {
    document.querySelectorAll('.aquila-buddy-badge, .aquila-buddy-score-pill').forEach((b) => b.remove());
  });
}

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
          .map((issue) => {
            const pct = formatMatchPercent(issue.score);
            const scoreBadge = pct > 0 ? `<span class="aquila-buddy-score-badge">${pct}%</span>` : '';
            return `
          <div class="aquila-buddy-issue-card">
            <div class="aquila-buddy-issue-card-top">
              <a href="https://github.com/${escapeHtml(issue.repoFullName)}/issues/${issue.issueNumber}" target="_blank" rel="noopener noreferrer">
                #${issue.issueNumber}
              </a>
              ${scoreBadge}
            </div>
            <p>${escapeHtml(issue.title)}</p>
            <div class="aquila-buddy-labels">
              ${(issue.labels ?? []).map((l) => `<span class="aquila-buddy-label">${escapeHtml(l)}</span>`).join('')}
            </div>
          </div>`;
          })
          .join('')}
      </div>
    `;
  });
}

function highlightRecommendedIssues(issues) {
  if (typeof document.querySelectorAll !== 'function') return;

  const issueByNumber = new Map();
  for (let i = 0; i < issues.length; i++) {
    issueByNumber.set(String(issues[i].issueNumber), issues[i]);
  }
  const pathSegments = location.pathname.split('/').filter(Boolean);
  const repoPrefix = `/${pathSegments.slice(0, 2).join('/')}/issues/`;

  let staleNodes = [];
  let pendingAdditions = [];

  domFrameQueue.read(() => {
    staleNodes = [];
    pendingAdditions = [];

    document.querySelectorAll('.aquila-buddy-badge').forEach((badge) => {
      const link = badge.closest('a');
      const match = link?.getAttribute('href')?.match(/\/issues\/(\d+)\/?$/);
      if (!match || !issueByNumber.has(match[1])) {
        staleNodes.push(badge);
        const nextSibling = badge.nextElementSibling;
        if (nextSibling?.classList?.contains('aquila-buddy-score-pill')) {
          staleNodes.push(nextSibling);
        }
      }
    });

    document.querySelectorAll(`a[href^="${repoPrefix}"]`).forEach((link) => {
      const match = link.getAttribute('href')?.match(/\/issues\/(\d+)\/?$/);
      if (!match) return;
      const issue = issueByNumber.get(match[1]);
      if (!issue || link.querySelector('.aquila-buddy-badge')) return;
      pendingAdditions.push({ link, issue });
    });
  });

  domFrameQueue.write(() => {
    for (let i = 0; i < staleNodes.length; i++) {
      staleNodes[i].remove();
    }
    for (let i = 0; i < pendingAdditions.length; i++) {
      const { link, issue } = pendingAdditions[i];
      const badge = document.createElement('span');
      badge.className = 'aquila-buddy-badge';
      badge.textContent = 'Aquila Buddy';
      link.appendChild(badge);

      const pct = formatMatchPercent(issue.score);
      if (pct > 0) {
        const pill = document.createElement('span');
        pill.className = 'aquila-buddy-score-pill';
        pill.textContent = `${pct}%`;
        link.appendChild(pill);
      }
    }
  });
}

function requestAnalysis(type, loadingText, repoFullName, issueNumber, shadow) {
  const resultEl = shadow.getElementById('aquila-buddy-analysis-result');
  const guideBtn = shadow.getElementById('aquila-buddy-guide-btn');
  const pseudoBtn = shadow.getElementById('aquila-buddy-pseudo-btn');

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
        <div class="aquila-buddy-result-header">
          <p class="aquila-buddy-result-label">수정 대상 파일</p>
          <button type="button" class="aquila-buddy-copy-btn" data-copy-selector=".aquila-buddy-file-list">경로 복사</button>
        </div>
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
          <div class="aquila-buddy-result-header">
            <p class="aquila-buddy-result-label">의사 코드</p>
            <button type="button" class="aquila-buddy-copy-btn" data-copy-selector=".aquila-buddy-code-block">코드 복사</button>
          </div>
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

const HTML_ESCAPE_MAP = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => HTML_ESCAPE_MAP[char]);
}

function safeDecodeSegment(segment) {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

function parseCompareBranches(pathname) {
  const cleanCompare = (pathname.split('/compare/')[1] ?? '').split(/[?#]/)[0].replace(/\/+$/, '');
  const parts = cleanCompare.includes('...') ? cleanCompare.split('...') : cleanCompare.split('..');
  const baseBranch = safeDecodeSegment(parts[0] ?? '');
  const decodedHead = safeDecodeSegment(parts[1] ?? '');
  const colonIdx = decodedHead.indexOf(':');
  if (colonIdx !== -1) {
    return {
      baseBranch,
      forkOwner: decodedHead.slice(0, colonIdx),
      headBranch: decodedHead.slice(colonIdx + 1),
    };
  }
  return { baseBranch, forkOwner: '', headBranch: decodedHead };
}

function renderPrCreate(el, shadow) {
  const pathParts = location.pathname.split('/');
  const upstreamRepo = `${pathParts[1]}/${pathParts[2]}`;
  const { baseBranch, forkOwner, headBranch } = parseCompareBranches(location.pathname);
  const headDisplay = forkOwner ? `${forkOwner}:${headBranch}` : headBranch;

  el.innerHTML = `
    <h3 class="aquila-buddy-section-title">PR 초안 생성</h3>
    <p class="aquila-buddy-desc">diff를 분석해 PR 제목과 본문을 자동으로 생성합니다.</p>
    <div class="aquila-buddy-field-group">
      <label class="aquila-buddy-field-label">레포지토리</label>
      <p class="aquila-buddy-field-value">${escapeHtml(upstreamRepo)}</p>
    </div>
    <div class="aquila-buddy-field-group">
      <label class="aquila-buddy-field-label">브랜치</label>
      <p class="aquila-buddy-field-value">${escapeHtml(baseBranch || '—')} ← ${escapeHtml(headDisplay || '—')}</p>
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
                <div class="aquila-buddy-result-header">
                  <p class="aquila-buddy-result-label">본문</p>
                  <button type="button" class="aquila-buddy-copy-btn" data-copy-selector="#aquila-buddy-draft-body">본문 복사</button>
                </div>
                <pre id="aquila-buddy-draft-body" class="aquila-buddy-code-block">${escapeHtml(draft?.body)}</pre>
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

function setNativeControlValue(control, proto, value) {
  const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
  if (descriptor && typeof descriptor.set === 'function') {
    descriptor.set.call(control, value);
  } else {
    control.value = value;
  }
  control.dispatchEvent(new Event('input', { bubbles: true }));
  control.dispatchEvent(new Event('change', { bubbles: true }));
}

function applyDraftToPrForm(title, body) {
  const titleInput = document.querySelector(
    '#pull_request_title, input[name="pull_request[title]"], input[aria-label="Title"], input[aria-label="Pull request title"]'
  );
  const bodyInput = document.querySelector(
    '#pull_request_body, textarea[name="pull_request[body]"], textarea[aria-label="Comment body"], textarea[aria-label="Pull request body"]'
  );
  if (titleInput && title != null && title !== '') {
    const inputProto = typeof HTMLInputElement !== 'undefined' ? HTMLInputElement.prototype : null;
    setNativeControlValue(titleInput, inputProto, title);
  }
  if (bodyInput && body != null && body !== '') {
    const textareaProto = typeof HTMLTextAreaElement !== 'undefined' ? HTMLTextAreaElement.prototype : null;
    setNativeControlValue(bodyInput, textareaProto, body);
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

let lastRenderedKey = null;

function init(event) {
  const pageType = detectPageType();
  const renderKey = `${location.pathname}::${pageType ?? 'NONE'}`;
  const existingRoot = getActiveRoot();

  if (
    (event?.type === 'turbo:render' || event?.type === 'soft-nav:payload') &&
    lastRenderedKey === renderKey &&
    (pageType ? Boolean(existingRoot) : !existingRoot)
  ) {
    if (pageType === 'ISSUE_LIST') {
      refreshRecommendedBadgesForCurrentRepo();
    }
    return;
  }

  domFrameQueue.cancelAll();
  if (lastRenderedKey !== renderKey) {
    isSidebarOpen = false;
  }
  lastRenderedKey = renderKey;

  if (typeof document.querySelectorAll === 'function') {
    document.querySelectorAll('aquila-buddy-root').forEach((el) => el.remove());
  } else {
    existingRoot?.remove?.();
  }
  document.getElementById?.('aquila-buddy-sidebar')?.remove();
  document.getElementById?.('aquila-buddy-toggle')?.remove();

  if (pageType !== 'ISSUE_LIST') {
    clearInjectedBadges();
  }
  if (pageType) {
    createExtensionUI();
  }
}

init();
document.addEventListener('turbo:load', init);
document.addEventListener('turbo:render', init);
document.addEventListener('soft-nav:payload', init);
document.addEventListener('pjax:end', init);
window.addEventListener('popstate', init);
document.addEventListener('turbo:before-cache', () => {
  domFrameQueue.cancelAll();
  lastRenderedKey = null;
  isSidebarOpen = false;
  if (typeof document.querySelectorAll === 'function') {
    document.querySelectorAll('aquila-buddy-root').forEach((el) => el.remove());
  }
});

document.addEventListener('animationstart', (event) => {
  if (event.animationName !== 'aquila-buddy-row-appear' || badgeRefreshPending) {
    return;
  }
  if (detectPageType() !== 'ISSUE_LIST') {
    return;
  }
  badgeRefreshPending = true;
  const run = () => {
    badgeRefreshPending = false;
    refreshRecommendedBadgesForCurrentRepo();
  };
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(run);
  } else {
    setTimeout(run, 0);
  }
});

document.addEventListener('keydown', (event) => {
  const shadow = getActiveRoot()?._shadowRoot;
  if (!shadow) return;
  const sidebar = shadow.getElementById('aquila-buddy-sidebar');
  const toggleBtn = shadow.getElementById('aquila-buddy-toggle');
  if (!sidebar || !toggleBtn) return;

  if (event.key === 'Escape' && isSidebarOpen) {
    setSidebarOpen(sidebar, toggleBtn, false);
    return;
  }

  if (
    event.altKey &&
    event.shiftKey &&
    !event.ctrlKey &&
    !event.metaKey &&
    (event.code === 'KeyB' || event.key?.toLowerCase() === 'b')
  ) {
    event.preventDefault();
    const nextOpen = !isSidebarOpen;
    setSidebarOpen(sidebar, toggleBtn, nextOpen);
    if (nextOpen) {
      loadContent(shadow);
    }
  }
});

chrome.runtime.onMessage.addListener((message) => {
  if (!message) return;
  const shadow = getActiveRoot()?._shadowRoot;

  if (message.type === 'AUTH_SUCCESS' || (message.type === 'AUTH_STATE_CHANGED' && message.authenticated)) {
    if (detectPageType() === 'ISSUE_LIST') {
      refreshRecommendedBadgesForCurrentRepo();
    }
    if (shadow && isSidebarOpen) {
      loadContent(shadow);
    }
    return;
  }

  if (message.type === 'AUTH_STATE_CHANGED' && message.authenticated === false) {
    clearInjectedBadges();
    if (shadow && isSidebarOpen) {
      const contentEl = shadow.getElementById('aquila-buddy-content');
      if (contentEl) {
        contentEl.innerHTML = renderError('NOT_AUTHENTICATED');
      }
    }
  }
});
