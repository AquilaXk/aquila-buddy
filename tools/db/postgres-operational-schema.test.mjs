import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const operationalSql = readFileSync("back/src/main/resources/schema-postgres-operational.sql", "utf8");
const deployScript = readFileSync("deploy/oci/run-aquila-buddy.sh", "utf8");

test("operational schema patch restores columns missing from existing OCI databases", () => {
  assert.match(operationalSql, /ALTER TABLE user_profiles\s+ADD COLUMN IF NOT EXISTS avatar_url VARCHAR\(1000\)/);
  assert.match(operationalSql, /ALTER TABLE pr_drafts\s+ADD COLUMN IF NOT EXISTS owner_github_id BIGINT/);
  assert.match(operationalSql, /ALTER TABLE pr_drafts\s+ALTER COLUMN owner_github_id SET NOT NULL/);
  assert.match(operationalSql, /fk_pr_drafts_owner/);
  assert.match(operationalSql, /NOT VALID/);
});

test("OCI deploy applies operational schema patch without requiring destructive reset", () => {
  assert.match(deployScript, /schema-postgres-operational\.sql/);
  assert.match(deployScript, /operational_sql/);
  assert.match(deployScript, /< "\$operational_sql"/);
  assert.doesNotMatch(deployScript, /schema already exists; set AQUILA_BUDDY_FORCE_RESET_SCHEMA=true to reapply destructive schema\.sql\nfi\n\nremove_container/);
});
