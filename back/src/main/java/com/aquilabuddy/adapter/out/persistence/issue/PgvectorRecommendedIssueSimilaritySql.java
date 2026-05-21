package com.aquilabuddy.adapter.out.persistence.issue;

final class PgvectorRecommendedIssueSimilaritySql {

    private static final String SCHEMA_RESOURCE_PATH = "schema-postgres-pgvector.sql";

    private static final String RANK_BY_ISSUE_IDS = """
        SELECT
            issue_id,
            repo_full_name,
            issue_number,
            title,
            summary,
            1 - (issue_embedding <=> CAST(:queryVector AS vector)) AS similarity_score,
            status,
            reason,
            copy_output_version,
            COALESCE(string_agg(label.label, ',' ORDER BY label.label_order), '') AS labels
        FROM recommended_issues
        LEFT JOIN recommended_issue_labels label
            ON label.recommended_issue_id = issue_id
        WHERE issue_id IN (:issueIds)
            AND issue_embedding IS NOT NULL
        GROUP BY
            issue_id,
            repo_full_name,
            issue_number,
            title,
            summary,
            issue_embedding,
            status,
            reason,
            copy_output_version
        ORDER BY issue_embedding <=> CAST(:queryVector AS vector), issue_number ASC
        LIMIT :limit
        """;

    private PgvectorRecommendedIssueSimilaritySql() {
    }

    static String rankByIssueIds() {
        return RANK_BY_ISSUE_IDS;
    }

    static String schemaResourcePath() {
        return SCHEMA_RESOURCE_PATH;
    }
}
