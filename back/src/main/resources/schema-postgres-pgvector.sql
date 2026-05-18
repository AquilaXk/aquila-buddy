CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS profile_embedding vector(1536);

ALTER TABLE user_profiles
    ALTER COLUMN profile_embedding TYPE vector(1536)
    USING NULLIF(profile_embedding, '')::vector;

ALTER TABLE recommended_issues
    ADD COLUMN IF NOT EXISTS issue_embedding vector(1536);

ALTER TABLE recommended_issues
    ALTER COLUMN issue_embedding TYPE vector(1536)
    USING NULLIF(issue_embedding, '')::vector;

CREATE INDEX IF NOT EXISTS idx_recommended_issues_issue_embedding
    ON recommended_issues
    USING hnsw (issue_embedding vector_cosine_ops);
