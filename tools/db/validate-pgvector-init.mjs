import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..");
const schemaSql = readFileSync(resolve(repoRoot, "back", "src", "main", "resources", "schema.sql"), "utf8");
const pgvectorSql = readFileSync(
  resolve(repoRoot, "back", "src", "main", "resources", "schema-postgres-pgvector.sql"),
  "utf8"
);

assertIncludes(schemaSql, "profile_embedding VARCHAR(12000)", "base user profile embedding column");
assertIncludes(schemaSql, "issue_embedding VARCHAR(12000)", "base recommended issue embedding column");
assertIncludes(pgvectorSql, "CREATE EXTENSION IF NOT EXISTS vector", "pgvector extension init");
assertIncludes(pgvectorSql, "ALTER COLUMN profile_embedding TYPE vector(1536)", "profile embedding vector conversion");
assertIncludes(pgvectorSql, "ALTER COLUMN issue_embedding TYPE vector(1536)", "issue embedding vector conversion");
assertIncludes(pgvectorSql, "USING NULLIF(profile_embedding, '')::vector", "profile embedding conversion expression");
assertIncludes(pgvectorSql, "USING NULLIF(issue_embedding, '')::vector", "issue embedding conversion expression");

console.log("pgvector init validation passed");

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}
