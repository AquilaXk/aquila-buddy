import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const cdWorkflow = readFileSync(".github/workflows/oci-buddy-cd.yml", "utf8");
const deployScript = readFileSync("deploy/oci/run-aquila-buddy.sh", "utf8");
const validator = readFileSync("deploy/oci/validate-buddy-deploy-assets.sh", "utf8");

test("OCI CD materializes a deduplicated runtime env from a private source env", () => {
  assert.match(cdWorkflow, /private_env_file="\$DEPLOY_ROOT\/aquila-buddy\.private\.env"/);
  assert.match(cdWorkflow, /runtime_env_file="\$DEPLOY_ROOT\/aquila-buddy\.env"/);
  assert.match(cdWorkflow, /managed_runtime_keys_regex=/);
  assert.match(cdWorkflow, /grep -Ev "\$managed_runtime_keys_regex" "\$private_env_file" > "\$runtime_env_file"/);
  assert.doesNotMatch(cdWorkflow, /base64 -d > "\$DEPLOY_ROOT\/aquila-buddy\.env"[\s\S]*>> "\$DEPLOY_ROOT\/aquila-buddy\.env"/);
});

test("OCI CD owns non-secret production runtime keys that drift in local env files", () => {
  const managedRuntimeKeys = cdWorkflow.match(/managed_runtime_keys_regex='([^']+)'/)?.[1] ?? "";

  for (const key of [
    "DB_URL",
    "DB_USER",
    "CSRF_ALLOWED_ORIGINS",
    "VERTEX_AI_CREDENTIALS_PATH",
    "GOOGLE_APPLICATION_CREDENTIALS"
  ]) {
    assert.match(managedRuntimeKeys, new RegExp(`(^|[|])${key}([|)]|=)`));
  }

  assert.match(cdWorkflow, /echo "DB_URL=jdbc:postgresql:\/\/aquila-buddy-postgres:5432\/aquila_buddy"/);
  assert.match(cdWorkflow, /echo "DB_USER=aquila_app"/);
  assert.match(cdWorkflow, /echo "CSRF_ALLOWED_ORIGINS=https:\/\/\$AQUILA_BUDDY_DOMAIN"/);
  assert.match(cdWorkflow, /echo "VERTEX_AI_CREDENTIALS_PATH=\/run\/secrets\/vertex-key\.json"/);
  assert.match(cdWorkflow, /echo "GOOGLE_APPLICATION_CREDENTIALS=\/run\/secrets\/vertex-key\.json"/);
});

test("runtime deploy rejects duplicate env keys before sourcing secrets", () => {
  assert.match(deployScript, /reject_duplicate_env_keys\(\)/);
  assert.match(deployScript, /env file contains duplicate keys/);
  assert.match(deployScript, /uniq -d/);
  assert.match(deployScript, /reject_duplicate_env_keys "\$env_file"/);
});

test("runtime deploy requires GitHub OAuth credentials before starting backend", () => {
  assert.match(deployScript, /require_var GITHUB_CLIENT_ID/);
  assert.match(deployScript, /require_var GITHUB_CLIENT_SECRET/);
  assert.match(validator, /require_contains deploy\/oci\/run-aquila-buddy\.sh 'require_var GITHUB_CLIENT_ID'/);
  assert.match(validator, /require_contains deploy\/oci\/run-aquila-buddy\.sh 'require_var GITHUB_CLIENT_SECRET'/);
});

test("frontend container does not receive the full private env file", () => {
  const frontendRunBlock = deployScript.slice(deployScript.indexOf('remove_container "$AQUILA_BUDDY_FRONTEND_CONTAINER"'));
  assert.doesNotMatch(frontendRunBlock, /--env-file "\$env_file"/);
  assert.match(frontendRunBlock, /-e "NEXT_PUBLIC_API_URL=\$NEXT_PUBLIC_API_URL"/);
  assert.match(frontendRunBlock, /-e "NEXT_PUBLIC_BACKEND_OAUTH_URL=\$NEXT_PUBLIC_BACKEND_OAUTH_URL"/);
});

test("deploy asset validator locks env dedupe and frontend public env contracts", () => {
  assert.match(validator, /managed_runtime_keys_regex/);
  assert.match(validator, /reject_duplicate_env_keys/);
  assert.match(validator, /CSRF_ALLOWED_ORIGINS=https:\/\/buddy\.aquilaxk\.site/);
  assert.match(validator, /CSRF_ALLOWED_ORIGINS=https:\/\/\$AQUILA_BUDDY_DOMAIN/);
  assert.match(validator, /NEXT_PUBLIC_BACKEND_OAUTH_URL=\$NEXT_PUBLIC_BACKEND_OAUTH_URL/);
});
