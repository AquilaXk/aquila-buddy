ALTER TABLE user_profiles
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profiles
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_user_profiles_rw ON user_profiles;
CREATE POLICY app_user_profiles_rw ON user_profiles
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE refresh_token_sessions
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token_sessions
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_refresh_token_sessions_rw ON refresh_token_sessions;
CREATE POLICY app_refresh_token_sessions_rw ON refresh_token_sessions
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE user_profile_primary_languages
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profile_primary_languages
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_user_profile_primary_languages_rw ON user_profile_primary_languages;
CREATE POLICY app_user_profile_primary_languages_rw ON user_profile_primary_languages
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE user_profile_topics
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profile_topics
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_user_profile_topics_rw ON user_profile_topics;
CREATE POLICY app_user_profile_topics_rw ON user_profile_topics
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE recommended_issues
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE recommended_issues
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_recommended_issues_rw ON recommended_issues;
CREATE POLICY app_recommended_issues_rw ON recommended_issues
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE recommended_issue_labels
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE recommended_issue_labels
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_recommended_issue_labels_rw ON recommended_issue_labels;
CREATE POLICY app_recommended_issue_labels_rw ON recommended_issue_labels
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE issue_guides
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE issue_guides
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_issue_guides_rw ON issue_guides;
CREATE POLICY app_issue_guides_rw ON issue_guides
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE issue_guide_generation_requests
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE issue_guide_generation_requests
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_issue_guide_generation_requests_rw ON issue_guide_generation_requests;
CREATE POLICY app_issue_guide_generation_requests_rw ON issue_guide_generation_requests
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE ai_call_observations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_call_observations
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_ai_call_observations_rw ON ai_call_observations;
CREATE POLICY app_ai_call_observations_rw ON ai_call_observations
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE pr_drafts
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE pr_drafts
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_pr_drafts_rw ON pr_drafts;
CREATE POLICY app_pr_drafts_rw ON pr_drafts
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE pr_draft_translations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE pr_draft_translations
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_pr_draft_translations_rw ON pr_draft_translations;
CREATE POLICY app_pr_draft_translations_rw ON pr_draft_translations
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE issue_guide_side_effects
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE issue_guide_side_effects
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_issue_guide_side_effects_rw ON issue_guide_side_effects;
CREATE POLICY app_issue_guide_side_effects_rw ON issue_guide_side_effects
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE issue_guide_file_paths
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE issue_guide_file_paths
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_issue_guide_file_paths_rw ON issue_guide_file_paths;
CREATE POLICY app_issue_guide_file_paths_rw ON issue_guide_file_paths
    FOR ALL TO :"app_user"
    USING (true)
    WITH CHECK (true);

ALTER TABLE user_recommended_issues
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_recommended_issues
    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS app_user_recommended_issues_rw ON user_recommended_issues;
CREATE POLICY app_user_recommended_issues_rw ON user_recommended_issues
    FOR ALL TO :"app_user"
    USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::bigint)
    WITH CHECK (user_id = NULLIF(current_setting('app.current_user_id', true), '')::bigint);
