import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const operationalSql = readFileSync("back/src/main/resources/schema-postgres-operational.sql", "utf8");
const deployScript = readFileSync("deploy/oci/run-aquila-buddy.sh", "utf8");

test("operational schema patch restores columns missing from existing OCI databases", () => {
  assert.match(operationalSql, /ALTER TABLE user_profiles\s+ADD COLUMN IF NOT EXISTS avatar_url VARCHAR\(1000\)/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ADD COLUMN IF NOT EXISTS family_id VARCHAR\(64\)/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ADD COLUMN IF NOT EXISTS github_id BIGINT/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ADD COLUMN IF NOT EXISTS replaced_by_token_hash VARCHAR\(64\)/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ALTER COLUMN family_id SET NOT NULL/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ALTER COLUMN github_id SET NOT NULL/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ALTER COLUMN issued_at SET NOT NULL/);
  assert.match(operationalSql, /ALTER TABLE refresh_token_sessions\s+ALTER COLUMN expires_at SET NOT NULL/);
  assert.match(operationalSql, /fk_refresh_token_sessions_profile/);
  assert.match(operationalSql, /ALTER TABLE pr_drafts\s+ADD COLUMN IF NOT EXISTS owner_github_id BIGINT/);
  assert.match(operationalSql, /ALTER TABLE pr_drafts\s+ALTER COLUMN owner_github_id SET NOT NULL/);
  assert.match(operationalSql, /ALTER TABLE issue_guides\s+ADD COLUMN IF NOT EXISTS output_version VARCHAR\(120\)/);
  assert.match(operationalSql, /UPDATE issue_guides\s+SET output_version = 'legacy'\s+WHERE output_version IS NULL/);
  assert.match(operationalSql, /ALTER TABLE issue_guides\s+ALTER COLUMN output_version SET DEFAULT 'issue-guide-output-v2026-05-21'/);
  assert.match(operationalSql, /ALTER TABLE issue_guides\s+ALTER COLUMN output_version SET NOT NULL/);
  assert.match(operationalSql, /fk_pr_drafts_owner/);
  assert.match(operationalSql, /NOT VALID/);
});

test("operational schema patch restores tables missing from existing OCI databases", () => {
  for (const tableName of [
    "refresh_token_sessions",
    "user_profile_primary_languages",
    "user_profile_topics",
    "recommended_issues",
    "recommended_issue_labels",
    "issue_guides",
    "issue_guide_generation_requests",
    "ai_call_observations",
    "pr_drafts",
    "pr_draft_translations",
    "issue_guide_side_effects",
    "issue_guide_file_paths",
  ]) {
    assert.match(operationalSql, new RegExp(`CREATE TABLE IF NOT EXISTS ${tableName}\\s*\\(`));
  }
});

test("OCI deploy applies operational schema patch without requiring destructive reset", () => {
  assert.match(deployScript, /schema-postgres-operational\.sql/);
  assert.match(deployScript, /operational_sql/);
  assert.match(deployScript, /< "\$operational_sql"/);
  assert.doesNotMatch(deployScript, /schema already exists; set AQUILA_BUDDY_FORCE_RESET_SCHEMA=true to reapply destructive schema\.sql\nfi\n\nremove_container/);
});
