export const UX_FLOW_REQUIRED_ROUTES = [
  "/login",
  "/oauth/callback",
  "/dashboard",
  "/profile",
  "/issues",
  "/issues/[id]",
  "/pr-draft",
  "/pr/[id]",
];

export const UX_FLOW_VISUAL_EXCLUSIONS = [
  "colors",
  "fonts",
  "images",
  "decorations",
  "brand-skin",
];

export const UI_UX_FLOW_CONTRACTS = [
  {
    name: "login",
    route: "/login",
    validator: "tools/frontend/validate-login-parity.mjs",
    sourceFiles: ["front/src/app/login/page.tsx", "front/src/lib/auth.ts"],
    fixtureStates: ["normal", "error"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["service-name", "login-title", "oauth-cta", "consent-copy", "backend-hint"],
    primaryActions: ["normalize-next", "start-oauth"],
    interactionGuards: ["oauth-cta-enabled", "unsafe-next-normalized"],
  },
  {
    name: "oauth-callback",
    route: "/oauth/callback",
    validator: "tools/frontend/validate-oauth-callback-parity.mjs",
    sourceFiles: ["front/src/app/oauth/callback/page.tsx", "front/src/lib/auth.ts"],
    fixtureStates: ["loading", "result", "error"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["callback-title", "status-message", "redirect-target"],
    primaryActions: ["read-token", "persist-token", "redirect-dashboard"],
    interactionGuards: ["token-persisted", "unsafe-redirect-normalized"],
  },
  {
    name: "dashboard",
    route: "/dashboard",
    validator: "tools/frontend/validate-dashboard-parity.mjs",
    sourceFiles: ["front/src/app/dashboard/page.tsx"],
    fixtureStates: ["normal", "empty", "loading", "error"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["greeting", "profile-banner", "account-card", "workflow-step-1", "workflow-step-2", "workflow-step-3"],
    primaryActions: ["load-profile", "open-profile", "open-issues", "open-pr-draft"],
    interactionGuards: ["protected-redirect", "profile-vector-cta"],
  },
  {
    name: "profile",
    route: "/profile",
    validator: "tools/frontend/validate-profile-parity.mjs",
    sourceFiles: ["front/src/app/profile/page.tsx", "front/src/app/profile/ProfileEditorPanel.tsx"],
    fixtureStates: ["normal", "empty", "loading", "error", "result"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["profile-title", "identity-fields", "save-profile", "vector-section", "detected-languages"],
    primaryActions: ["load-profile", "save-profile", "refresh-vector"],
    interactionGuards: ["save-disabled-during-submit", "vector-refresh-feedback"],
  },
  {
    name: "issues",
    route: "/issues",
    validator: "tools/frontend/validate-issues-parity.mjs",
    sourceFiles: ["front/src/app/issues/page.tsx", "front/src/lib/api.ts"],
    fixtureStates: ["normal", "empty", "loading", "error", "result"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["title", "recommend-cta", "progress-steps", "history-list", "issue-card", "detail-link"],
    primaryActions: ["profile-check", "crawl", "recommend", "open-detail"],
    interactionGuards: ["recommend-disabled-during-run", "empty-state-guidance"],
  },
  {
    name: "issue-detail",
    route: "/issues/[id]",
    validator: "tools/frontend/validate-issue-detail-parity.mjs",
    sourceFiles: ["front/src/app/issues/[id]/page.tsx", "front/src/app/issues/[id]/issueDetailData.ts", "front/src/lib/api.ts"],
    fixtureStates: ["normal", "empty", "loading", "error", "result"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["back-link", "issue-summary", "recommendation-reason", "guide-card", "pseudo-card", "pr-draft-cta"],
    primaryActions: ["load-detail", "show-guide", "show-pseudo", "open-pr-draft"],
    interactionGuards: ["guide-loading-disabled", "pr-draft-query-preserved"],
  },
  {
    name: "pr-draft",
    route: "/pr-draft",
    validator: "tools/frontend/validate-pr-draft-parity.mjs",
    sourceFiles: ["front/src/app/pr-draft/page.tsx", "front/src/lib/api.ts"],
    fixtureStates: ["normal", "empty", "loading", "error", "result"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["source-issue-card", "branch-form", "create-cta", "draft-title", "draft-body", "translation-cta", "history-list"],
    primaryActions: ["prefill-issue", "create-draft", "translate", "open-detail"],
    interactionGuards: ["create-disabled-during-submit", "source-issue-prefill"],
  },
  {
    name: "pr-detail",
    route: "/pr/[id]",
    validator: "tools/frontend/validate-pr-detail-parity.mjs",
    sourceFiles: ["front/src/app/pr/[id]/page.tsx", "front/src/app/pr/[id]/PrDraftEditPanel.tsx", "front/src/app/pr/[id]/TranslationPanel.tsx"],
    fixtureStates: ["normal", "empty", "loading", "error", "result"],
    responsiveViewports: ["mobile", "desktop"],
    landmarkOrder: ["draft-summary", "edit-panel", "delete-cta", "translation-panel", "github-link"],
    primaryActions: ["load-draft", "edit", "translate", "delete"],
    interactionGuards: ["edit-submit-feedback", "translation-submit-feedback"],
  },
];

const ALLOWED_STATES = new Set(["normal", "empty", "loading", "error", "result"]);
const REQUIRED_STATE_COVERAGE = ["normal", "empty", "loading", "error", "result"];
const ALLOWED_VIEWPORTS = new Set(["mobile", "desktop"]);
const REQUIRED_VIEWPORTS = ["mobile", "desktop"];

export function validateUiUxFlowContracts(contracts) {
  const failures = [];

  if (!Array.isArray(contracts)) {
    return ["UI UX flow contracts must be an array."];
  }

  const routes = contracts.map((contract) => contract.route);
  if (JSON.stringify(routes) !== JSON.stringify(UX_FLOW_REQUIRED_ROUTES)) {
    failures.push(`Route contract order changed: ${routes.join(", ")}`);
  }

  const seenRoutes = new Set();
  const seenValidators = new Set();
  const coveredStates = new Set();

  for (const contract of contracts) {
    if (!contract.name) {
      failures.push("Every UI UX contract must have a name.");
    }
    if (!contract.route || seenRoutes.has(contract.route)) {
      failures.push(`Route must be present and unique: ${contract.route ?? "<missing>"}`);
    }
    seenRoutes.add(contract.route);

    if (!contract.validator?.startsWith("tools/frontend/validate-") || !contract.validator.endsWith(".mjs")) {
      failures.push(`${contract.route} must point to a frontend validator script.`);
    }
    if (seenValidators.has(contract.validator)) {
      failures.push(`${contract.route} reuses validator ${contract.validator}.`);
    }
    seenValidators.add(contract.validator);

    for (const [field, value] of [
      ["sourceFiles", contract.sourceFiles],
      ["fixtureStates", contract.fixtureStates],
      ["responsiveViewports", contract.responsiveViewports],
      ["landmarkOrder", contract.landmarkOrder],
      ["primaryActions", contract.primaryActions],
      ["interactionGuards", contract.interactionGuards],
    ]) {
      if (!Array.isArray(value) || value.length === 0) {
        failures.push(`${contract.route} must define non-empty ${field}.`);
      }
    }

    for (const state of contract.fixtureStates ?? []) {
      if (!ALLOWED_STATES.has(state)) {
        failures.push(`${contract.route} uses unsupported fixture state ${state}.`);
      }
      coveredStates.add(state);
    }

    for (const viewport of contract.responsiveViewports ?? []) {
      if (!ALLOWED_VIEWPORTS.has(viewport)) {
        failures.push(`${contract.route} uses unsupported viewport ${viewport}.`);
      }
    }
    if (JSON.stringify(contract.responsiveViewports ?? []) !== JSON.stringify(REQUIRED_VIEWPORTS)) {
      failures.push(`${contract.route} must define mobile and desktop viewport checks.`);
    }
  }

  for (const state of REQUIRED_STATE_COVERAGE) {
    if (!coveredStates.has(state)) {
      failures.push(`Fixture state coverage is missing ${state}.`);
    }
  }

  return failures;
}
