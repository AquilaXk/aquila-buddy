CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS profile_embedding vector(1536);

ALTER TABLE recommended_issues
    ADD COLUMN IF NOT EXISTS issue_embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_recommended_issues_issue_embedding
    ON recommended_issues
    USING hnsw (issue_embedding vector_cosine_ops);
