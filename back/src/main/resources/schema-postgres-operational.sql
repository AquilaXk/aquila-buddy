ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1000);

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
