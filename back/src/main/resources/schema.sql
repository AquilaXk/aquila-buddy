DROP TABLE IF EXISTS issue_guide_side_effects;
DROP TABLE IF EXISTS issue_guides;
DROP TABLE IF EXISTS pr_draft_translations;
DROP TABLE IF EXISTS pr_drafts;
DROP TABLE IF EXISTS recommended_issue_labels;
DROP TABLE IF EXISTS recommended_issues;
DROP TABLE IF EXISTS user_profile_topics;
DROP TABLE IF EXISTS user_profile_primary_languages;
DROP TABLE IF EXISTS user_profiles;

CREATE TABLE user_profiles (
    github_id BIGINT PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    profile_ready BOOLEAN NOT NULL,
    vector_updated_at VARCHAR(40) NOT NULL
);

CREATE TABLE user_profile_primary_languages (
    user_github_id BIGINT NOT NULL,
    language_order INTEGER NOT NULL,
    language VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_github_id, language_order),
    CONSTRAINT fk_user_profile_primary_languages_profile
        FOREIGN KEY (user_github_id)
        REFERENCES user_profiles(github_id)
        ON DELETE CASCADE
);

CREATE TABLE user_profile_topics (
    user_github_id BIGINT NOT NULL,
    topic_order INTEGER NOT NULL,
    topic VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_github_id, topic_order),
    CONSTRAINT fk_user_profile_topics_profile
        FOREIGN KEY (user_github_id)
        REFERENCES user_profiles(github_id)
        ON DELETE CASCADE
);

CREATE TABLE recommended_issues (
    issue_id VARCHAR(100) PRIMARY KEY,
    repo_full_name VARCHAR(255) NOT NULL,
    issue_number BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason VARCHAR(1000) NOT NULL
);

CREATE TABLE recommended_issue_labels (
    recommended_issue_id VARCHAR(100) NOT NULL,
    label_order INTEGER NOT NULL,
    label VARCHAR(255) NOT NULL,
    PRIMARY KEY (recommended_issue_id, label_order),
    CONSTRAINT fk_recommended_issue_labels_issue
        FOREIGN KEY (recommended_issue_id)
        REFERENCES recommended_issues(issue_id)
        ON DELETE CASCADE
);

CREATE TABLE issue_guides (
    issue_id VARCHAR(100) PRIMARY KEY,
    guideline VARCHAR(4000) NOT NULL,
    pseudo_code VARCHAR(4000) NOT NULL
);

CREATE TABLE pr_drafts (
    draft_id VARCHAR(100) PRIMARY KEY,
    upstream_repo VARCHAR(255) NOT NULL,
    github_issue_number BIGINT NOT NULL,
    base_branch VARCHAR(255) NOT NULL,
    head_branch VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE pr_draft_translations (
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

CREATE TABLE issue_guide_side_effects (
    issue_guide_id VARCHAR(100) NOT NULL,
    effect_order INTEGER NOT NULL,
    side_effect VARCHAR(1000) NOT NULL,
    PRIMARY KEY (issue_guide_id, effect_order),
    CONSTRAINT fk_issue_guide_side_effects_guide
        FOREIGN KEY (issue_guide_id)
        REFERENCES issue_guides(issue_id)
        ON DELETE CASCADE
);
