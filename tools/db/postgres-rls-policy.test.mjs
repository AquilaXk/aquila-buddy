import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const rlsPath = "back/src/main/resources/schema-postgres-rls.sql";
const deployScript = readFileSync("deploy/oci/run-aquila-buddy.sh", "utf8");
const validator = readFileSync("deploy/oci/validate-buddy-deploy-assets.sh", "utf8");

const protectedTables = [
  "user_profiles",
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
  "user_recommended_issues",
];

test("RLS schema patch enables and forces row level security on application tables", () => {
  assert.equal(existsSync(rlsPath), true);
  const rlsSql = readFileSync(rlsPath, "utf8");

  for (const tableName of protectedTables) {
    assert.match(rlsSql, new RegExp(`ALTER TABLE ${tableName}\\s+ENABLE ROW LEVEL SECURITY`));
    assert.match(rlsSql, new RegExp(`ALTER TABLE ${tableName}\\s+FORCE ROW LEVEL SECURITY`));
  }
});

test("RLS schema patch creates explicit app role policies instead of relying on table grants only", () => {
  const rlsSql = readFileSync(rlsPath, "utf8");

  assert.match(rlsSql, /CREATE POLICY app_user_profiles_rw/);
  assert.match(rlsSql, /CREATE POLICY app_pr_drafts_rw/);
  assert.match(rlsSql, /CREATE POLICY app_pr_draft_translations_rw/);
  assert.match(rlsSql, /CREATE POLICY app_user_recommended_issues_rw/);
  assert.match(rlsSql, /app\.current_user_id/);
  assert.match(rlsSql, /TO :"app_user"/);
  assert.match(rlsSql, /WITH CHECK \(true\)/);
});

test("OCI deploy applies RLS policy patch after app role creation", () => {
  assert.match(deployScript, /rls_sql/);
  assert.match(deployScript, /schema-postgres-rls\.sql/);
  assert.match(deployScript, /psql_as_admin -v app_user="\$DB_USER" < "\$rls_sql"/);
  assert.match(validator, /schema-postgres-rls\.sql/);
});
