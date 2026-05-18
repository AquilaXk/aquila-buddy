package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgvectorRecommendedIssueSimilaritySqlTest {

    @Test
    void buildsCosineDistanceRankingQuery() {
        String sql = PgvectorRecommendedIssueSimilaritySql.rankByIssueIds();

        assertThat(sql).contains("recommended_issues");
        assertThat(sql).contains("issue_embedding <=> CAST(:queryVector AS vector)");
        assertThat(sql).contains("WHERE issue_id IN (:issueIds)");
        assertThat(sql).contains("AND issue_embedding IS NOT NULL");
        assertThat(sql).contains("ORDER BY issue_embedding <=> CAST(:queryVector AS vector)");
        assertThat(sql).contains("LIMIT :limit");
    }

    @Test
    void exposesPostgresPgvectorSchemaResource() {
        assertThat(PgvectorRecommendedIssueSimilaritySql.schemaResourcePath())
            .isEqualTo("schema-postgres-pgvector.sql");
        assertThat(getClass().getClassLoader().getResourceAsStream(
            PgvectorRecommendedIssueSimilaritySql.schemaResourcePath()
        )).isNotNull();
    }
}
