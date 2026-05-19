import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  UI_UX_FLOW_CONTRACTS,
  UX_FLOW_REQUIRED_ROUTES,
  UX_FLOW_VISUAL_EXCLUSIONS,
  validateUiUxFlowContracts,
} from "./ui-ux-flow-contracts.mjs";

describe("frontend UI UX flow contracts", () => {
  it("locks the target route order without visual design concerns", () => {
    assert.deepEqual(
      UI_UX_FLOW_CONTRACTS.map((contract) => contract.route),
      UX_FLOW_REQUIRED_ROUTES
    );
    assert.deepEqual(UX_FLOW_VISUAL_EXCLUSIONS, [
      "colors",
      "fonts",
      "images",
      "decorations",
      "brand-skin",
    ]);
  });

  it("requires normal, empty, loading, error, and result state coverage", () => {
    const states = new Set(UI_UX_FLOW_CONTRACTS.flatMap((contract) => contract.fixtureStates));

    for (const state of ["normal", "empty", "loading", "error", "result"]) {
      assert.equal(states.has(state), true, `missing ${state} fixture state`);
    }
  });

  it("maps every page contract to the aggregate validator scripts", () => {
    const failures = validateUiUxFlowContracts(UI_UX_FLOW_CONTRACTS);

    assert.deepEqual(failures, []);
    assert.deepEqual(
      UI_UX_FLOW_CONTRACTS.map((contract) => contract.validator),
      [
        "tools/frontend/validate-login-parity.mjs",
        "tools/frontend/validate-oauth-callback-parity.mjs",
        "tools/frontend/validate-dashboard-parity.mjs",
        "tools/frontend/validate-profile-parity.mjs",
        "tools/frontend/validate-issues-parity.mjs",
        "tools/frontend/validate-issue-detail-parity.mjs",
        "tools/frontend/validate-pr-draft-parity.mjs",
        "tools/frontend/validate-pr-detail-parity.mjs",
      ]
    );
  });

  it("keeps primary workflow actions ordered for high-risk pages", () => {
    const issues = UI_UX_FLOW_CONTRACTS.find((contract) => contract.route === "/issues");
    const issueDetail = UI_UX_FLOW_CONTRACTS.find((contract) => contract.route === "/issues/[id]");
    const prDraft = UI_UX_FLOW_CONTRACTS.find((contract) => contract.route === "/pr-draft");
    const prDetail = UI_UX_FLOW_CONTRACTS.find((contract) => contract.route === "/pr/[id]");

    assert.deepEqual(issues?.primaryActions, ["profile-check", "crawl", "recommend", "open-detail"]);
    assert.deepEqual(issueDetail?.primaryActions, ["load-detail", "show-guide", "show-pseudo", "open-pr-draft"]);
    assert.deepEqual(prDraft?.primaryActions, ["prefill-issue", "create-draft", "translate", "open-detail"]);
    assert.deepEqual(prDetail?.primaryActions, ["load-draft", "edit", "translate", "delete"]);
  });
});
