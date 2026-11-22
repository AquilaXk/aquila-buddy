package com.aquilabuddy.adapter.out.persistence.issue;

final class PgvectorRecommendedIssueSimilaritySql {

    private static final String SCHEMA_RESOURCE_PATH = "schema-postgres-pgvector.sql";

    private static final String FIND_STORED_PROFILE_EMBEDDING = """
        SELECT CAST(profile_embedding AS VARCHAR)
        FROM user_profiles
        WHERE github_id = :githubId
            AND profile_ready = TRUE
            AND profile_embedding IS NOT NULL
        """;

    private static final String RANK_BY_ISSUE_IDS = """
        SELECT
            ranked.issue_id,
            ranked.repo_full_name,
            ranked.issue_number,
            ranked.title,
            ranked.summary,
            ranked.similarity_score,
            ranked.status,
            ranked.reason,
            ranked.copy_output_version,
            COALESCE(string_agg(label.label, chr(31) ORDER BY label.label_order), '') AS labels
        FROM (
            SELECT
                issue_id,
                repo_full_name,
                issue_number,
                title,
                summary,
                score,
                (issue_embedding <=> CAST(:queryVector AS vector)) AS cosine_distance,
                1 - (issue_embedding <=> CAST(:queryVector AS vector)) AS similarity_score,
                status,
                reason,
                copy_output_version
            FROM recommended_issues
            WHERE issue_id IN (:issueIds)
                AND issue_embedding IS NOT NULL
            ORDER BY issue_embedding <=> CAST(:queryVector AS vector) ASC
            LIMIT :limit
        ) ranked
        LEFT JOIN recommended_issue_labels label
            ON label.recommended_issue_id = ranked.issue_id
        GROUP BY
            ranked.issue_id,
            ranked.repo_full_name,
            ranked.issue_number,
            ranked.title,
            ranked.summary,
            ranked.score,
            ranked.cosine_distance,
            ranked.similarity_score,
            ranked.status,
            ranked.reason,
            ranked.copy_output_version
        ORDER BY ranked.cosine_distance ASC, ranked.score DESC, ranked.issue_number ASC
        """;

    private static final String RANK_CATALOG_TOP_K = """
        SELECT
            ranked.issue_id,
            ranked.repo_full_name,
            ranked.issue_number,
            ranked.title,
            ranked.summary,
            ranked.similarity_score,
            ranked.status,
            ranked.reason,
            ranked.copy_output_version,
            COALESCE(string_agg(label.label, chr(31) ORDER BY label.label_order), '') AS labels
        FROM (
            SELECT
                issue_id,
                repo_full_name,
                issue_number,
                title,
                summary,
                score,
                (issue_embedding <=> CAST(:queryVector AS vector)) AS cosine_distance,
                1 - (issue_embedding <=> CAST(:queryVector AS vector)) AS similarity_score,
                status,
                reason,
                copy_output_version
            FROM recommended_issues
            WHERE issue_embedding IS NOT NULL
            ORDER BY issue_embedding <=> CAST(:queryVector AS vector) ASC
            LIMIT :limit
        ) ranked
        LEFT JOIN recommended_issue_labels label
            ON label.recommended_issue_id = ranked.issue_id
        GROUP BY
            ranked.issue_id,
            ranked.repo_full_name,
            ranked.issue_number,
            ranked.title,
            ranked.summary,
            ranked.score,
            ranked.cosine_distance,
            ranked.similarity_score,
            ranked.status,
            ranked.reason,
            ranked.copy_output_version
        ORDER BY ranked.cosine_distance ASC, ranked.score DESC, ranked.issue_number ASC
        """;

    private PgvectorRecommendedIssueSimilaritySql() {
    }

    static String schemaResourcePath() {
        return SCHEMA_RESOURCE_PATH;
    }

    static String rankByIssueIds() {
        return RANK_BY_ISSUE_IDS;
    }

    static String rankCatalogTopK() {
        return RANK_CATALOG_TOP_K;
    }

    static String findStoredProfileEmbedding() {
        return FIND_STORED_PROFILE_EMBEDDING;
    }
}
