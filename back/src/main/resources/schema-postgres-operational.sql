ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1000);

CREATE TABLE IF NOT EXISTS refresh_token_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    family_id VARCHAR(64) NOT NULL,
    github_id BIGINT NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP,
    replaced_by_token_hash VARCHAR(64),
    CONSTRAINT fk_refresh_token_sessions_profile
        FOREIGN KEY (github_id)
        REFERENCES user_profiles(github_id)
        ON DELETE CASCADE
);

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS family_id VARCHAR(64);

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS github_id BIGINT;

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMP;

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP;

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

ALTER TABLE refresh_token_sessions
    ADD COLUMN IF NOT EXISTS replaced_by_token_hash VARCHAR(64);

UPDATE refresh_token_sessions
SET family_id = token_hash
WHERE family_id IS NULL
    AND token_hash IS NOT NULL;

UPDATE refresh_token_sessions
SET issued_at = CURRENT_TIMESTAMP
WHERE issued_at IS NULL;

UPDATE refresh_token_sessions
SET expires_at = CURRENT_TIMESTAMP
WHERE expires_at IS NULL;

DELETE FROM refresh_token_sessions
WHERE token_hash IS NULL
    OR family_id IS NULL
    OR github_id IS NULL
    OR issued_at IS NULL
    OR expires_at IS NULL;

ALTER TABLE refresh_token_sessions
    ALTER COLUMN token_hash SET NOT NULL;

ALTER TABLE refresh_token_sessions
    ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE refresh_token_sessions
    ALTER COLUMN github_id SET NOT NULL;

ALTER TABLE refresh_token_sessions
    ALTER COLUMN issued_at SET NOT NULL;

ALTER TABLE refresh_token_sessions
    ALTER COLUMN expires_at SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.refresh_token_sessions'::regclass
            AND contype = 'p'
    ) THEN
        ALTER TABLE refresh_token_sessions
            ADD CONSTRAINT refresh_token_sessions_pkey
            PRIMARY KEY (token_hash);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_refresh_token_sessions_profile'
            AND conrelid = 'public.refresh_token_sessions'::regclass
    ) THEN
        ALTER TABLE refresh_token_sessions
            ADD CONSTRAINT fk_refresh_token_sessions_profile
            FOREIGN KEY (github_id)
            REFERENCES user_profiles(github_id)
            ON DELETE CASCADE
            NOT VALID;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS user_profile_primary_languages (
    user_github_id BIGINT NOT NULL,
    language_order INTEGER NOT NULL,
    language VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_github_id, language_order),
    CONSTRAINT fk_user_profile_primary_languages_profile
        FOREIGN KEY (user_github_id)
        REFERENCES user_profiles(github_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_profile_topics (
    user_github_id BIGINT NOT NULL,
    topic_order INTEGER NOT NULL,
    topic VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_github_id, topic_order),
    CONSTRAINT fk_user_profile_topics_profile
        FOREIGN KEY (user_github_id)
        REFERENCES user_profiles(github_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS recommended_issues (
    issue_id VARCHAR(100) PRIMARY KEY,
    repo_full_name VARCHAR(255) NOT NULL,
    issue_number BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    issue_embedding VARCHAR(12000)
);

CREATE TABLE IF NOT EXISTS recommended_issue_labels (
    recommended_issue_id VARCHAR(100) NOT NULL,
    label_order INTEGER NOT NULL,
    label VARCHAR(255) NOT NULL,
    PRIMARY KEY (recommended_issue_id, label_order),
    CONSTRAINT fk_recommended_issue_labels_issue
        FOREIGN KEY (recommended_issue_id)
        REFERENCES recommended_issues(issue_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS issue_guides (
    issue_id VARCHAR(100) PRIMARY KEY,
    guideline VARCHAR(4000) NOT NULL,
    pseudo_code VARCHAR(4000) NOT NULL,
    output_version VARCHAR(120) NOT NULL DEFAULT 'issue-guide-output-v2026-05-21',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE issue_guides
    ADD COLUMN IF NOT EXISTS output_version VARCHAR(120);

UPDATE issue_guides
SET output_version = 'legacy'
WHERE output_version IS NULL;

ALTER TABLE issue_guides
    ALTER COLUMN output_version SET DEFAULT 'issue-guide-output-v2026-05-21';

ALTER TABLE issue_guides
    ALTER COLUMN output_version SET NOT NULL;

CREATE TABLE IF NOT EXISTS issue_guide_generation_requests (
    request_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_github_id BIGINT NOT NULL,
    issue_id VARCHAR(100) NOT NULL,
    requested_on DATE NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS ai_call_observations (
    observation_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider VARCHAR(80) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    model VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(120) NOT NULL,
    latency_millis BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    error_type VARCHAR(255),
    failure_reason VARCHAR(1000),
    input_tokens INTEGER,
    output_tokens INTEGER,
    observed_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS pr_drafts (
    draft_id VARCHAR(100) PRIMARY KEY,
    owner_github_id BIGINT NOT NULL,
    upstream_repo VARCHAR(255) NOT NULL,
    github_issue_number BIGINT NOT NULL,
    source_issue_id VARCHAR(100),
    issue_title VARCHAR(500),
    base_branch VARCHAR(255) NOT NULL,
    head_branch VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    diff_content VARCHAR(12000),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_pr_drafts_owner
        FOREIGN KEY (owner_github_id)
        REFERENCES user_profiles(github_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pr_draft_translations (
    translation_id VARCHAR(100) PRIMARY KEY,
    draft_id VARCHAR(100) NOT NULL,
    target_language VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_pr_draft_translations_draft
        FOREIGN KEY (draft_id)
        REFERENCES pr_drafts(draft_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS issue_guide_side_effects (
    issue_guide_id VARCHAR(100) NOT NULL,
    effect_order INTEGER NOT NULL,
    side_effect VARCHAR(1000) NOT NULL,
    PRIMARY KEY (issue_guide_id, effect_order),
    CONSTRAINT fk_issue_guide_side_effects_guide
        FOREIGN KEY (issue_guide_id)
        REFERENCES issue_guides(issue_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS issue_guide_file_paths (
    issue_guide_id VARCHAR(100) NOT NULL,
    path_order INTEGER NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    PRIMARY KEY (issue_guide_id, path_order),
    CONSTRAINT fk_issue_guide_file_paths_guide
        FOREIGN KEY (issue_guide_id)
        REFERENCES issue_guides(issue_id)
        ON DELETE CASCADE
);

ALTER TABLE pr_drafts
    ADD COLUMN IF NOT EXISTS owner_github_id BIGINT;

UPDATE pr_drafts
SET owner_github_id = 10001
WHERE owner_github_id IS NULL;

ALTER TABLE pr_drafts
    ALTER COLUMN owner_github_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_pr_drafts_owner'
            AND conrelid = 'public.pr_drafts'::regclass
    ) THEN
        ALTER TABLE pr_drafts
            ADD CONSTRAINT fk_pr_drafts_owner
            FOREIGN KEY (owner_github_id)
            REFERENCES user_profiles(github_id)
            ON DELETE CASCADE
            NOT VALID;
    END IF;
END
$$;
