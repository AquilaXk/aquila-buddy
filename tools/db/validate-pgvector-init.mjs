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
const initScript = readFileSync(resolve(repoRoot, "tools", "db", "init-postgres-pgvector.sh"), "utf8");

assertIncludes(schemaSql, "profile_embedding VARCHAR(12000)", "base user profile embedding column");
assertIncludes(schemaSql, "issue_embedding VARCHAR(12000)", "base recommended issue embedding column");
assertBefore(
  initScript,
  "back/src/main/resources/schema.sql",
  "back/src/main/resources/schema-postgres-pgvector.sql",
  "schema.sql must be applied before schema-postgres-pgvector.sql"
);
assertIncludes(pgvectorSql, "CREATE EXTENSION IF NOT EXISTS vector", "pgvector extension init");
assertIncludes(pgvectorSql, "ALTER COLUMN profile_embedding TYPE vector(1536)", "profile embedding vector conversion");
assertIncludes(pgvectorSql, "ALTER COLUMN issue_embedding TYPE vector(1536)", "issue embedding vector conversion");
assertIncludes(pgvectorSql, "USING NULLIF(profile_embedding::text, '')::vector", "profile embedding conversion expression");
assertIncludes(pgvectorSql, "USING NULLIF(issue_embedding::text, '')::vector", "issue embedding conversion expression");
assertExcludes(pgvectorSql, "DO $$", "Spring ResourceDatabasePopulator-compatible pgvector schema");
assertIncludes(
  pgvectorSql,
  "CREATE INDEX IF NOT EXISTS idx_user_profiles_profile_embedding",
  "user profile embedding HNSW index"
);
assertIncludes(
  pgvectorSql,
  "USING hnsw (profile_embedding vector_cosine_ops)",
  "user profile embedding HNSW cosine operator"
);
assertIncludes(
  pgvectorSql,
  "CREATE INDEX IF NOT EXISTS idx_recommended_issues_issue_embedding",
  "recommended issue embedding HNSW index"
);
assertIncludes(
  pgvectorSql,
  "USING hnsw (issue_embedding vector_cosine_ops)",
  "recommended issue embedding HNSW cosine operator"
);

console.log("pgvector init validation passed");

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}

function assertExcludes(value, unexpected, label) {
  if (value.includes(unexpected)) {
    throw new Error(`${label} must not include ${unexpected}`);
  }
}

function assertBefore(value, first, second, label) {
  const firstIndex = value.indexOf(first);
  const secondIndex = value.indexOf(second);
  if (firstIndex === -1 || secondIndex === -1 || firstIndex > secondIndex) {
    throw new Error(label);
  }
}
