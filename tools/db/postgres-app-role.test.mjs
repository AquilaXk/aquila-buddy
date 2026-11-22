import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const deployScript = readFileSync("deploy/oci/run-aquila-buddy.sh", "utf8");
const envExample = readFileSync("deploy/oci/aquila-buddy.env.example", "utf8");
const validator = readFileSync("deploy/oci/validate-buddy-deploy-assets.sh", "utf8");

test("OCI env example separates postgres admin role from application runtime role", () => {
  assert.match(envExample, /^POSTGRES_USER=aquila_admin$/m);
  assert.match(envExample, /^POSTGRES_PASSWORD=change-this-postgres-admin-password$/m);
  assert.match(envExample, /^DB_USER=aquila_app$/m);
  assert.match(envExample, /^DB_PASSWORD=change-this-postgres-app-password$/m);
});

test("OCI deploy script creates a non-superuser application role and grants only runtime privileges", () => {
  assert.match(deployScript, /require_var POSTGRES_PASSWORD/);
  assert.match(deployScript, /DB_USER="\$\{DB_USER:-aquila_app\}"/);
  assert.match(deployScript, /DB_USER must be a non-superuser app role distinct from POSTGRES_USER/);
  assert.match(deployScript, /NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS/);
  assert.match(deployScript, /GRANT USAGE ON SCHEMA public TO :\"app_user\"/);
  assert.match(deployScript, /GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :\"app_user\"/);
  assert.match(deployScript, /GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO :\"app_user\"/);
  assert.match(deployScript, /ALTER DEFAULT PRIVILEGES IN SCHEMA public/);
  assert.doesNotMatch(deployScript, /DB_PASSWORD="\$\{DB_PASSWORD:-\$\{POSTGRES_PASSWORD:-\}\}"/);
  assert.doesNotMatch(deployScript, /POSTGRES_PASSWORD=\$DB_PASSWORD/);
});

test("deployment asset validator locks the app role contract", () => {
  assert.match(validator, /POSTGRES_USER=aquila_admin/);
  assert.match(validator, /DB_USER=aquila_app/);
  assert.match(validator, /NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS/);
});
