CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS profile_embedding vector(1536);

ALTER TABLE user_profiles
    ALTER COLUMN profile_embedding TYPE vector(1536)
    USING NULLIF(profile_embedding::text, '')::vector;

CREATE INDEX IF NOT EXISTS idx_user_profiles_profile_embedding
    ON user_profiles
    USING hnsw (profile_embedding vector_cosine_ops);

ALTER TABLE recommended_issues
    ADD COLUMN IF NOT EXISTS issue_embedding vector(1536);

ALTER TABLE recommended_issues
    ALTER COLUMN issue_embedding TYPE vector(1536)
    USING NULLIF(issue_embedding::text, '')::vector;

CREATE INDEX IF NOT EXISTS idx_recommended_issues_issue_embedding
    ON recommended_issues
    USING hnsw (issue_embedding vector_cosine_ops);
