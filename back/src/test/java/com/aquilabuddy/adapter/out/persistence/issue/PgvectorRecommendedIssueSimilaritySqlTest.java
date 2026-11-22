package com.aquilabuddy.adapter.out.persistence.issue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgvectorRecommendedIssueSimilaritySqlTest {

    @Test
    void buildsCosineDistanceRankingQueryWithDirectHnswScanAndUnitSeparatorLabels() {
        String sql = PgvectorRecommendedIssueSimilaritySql.rankByIssueIds();

        assertThat(sql).contains("recommended_issues");
        assertThat(sql).contains("issue_embedding <=> CAST(:queryVector AS vector)");
        assertThat(sql).contains("WHERE issue_id IN (:issueIds)");
        assertThat(sql).contains("AND issue_embedding IS NOT NULL");
        assertThat(sql).containsPattern("ORDER BY issue_embedding <=> CAST\\(:queryVector AS vector\\) ASC\\s+LIMIT :limit");
        assertThat(sql).contains("chr(31)");
        assertThat(sql).contains("LIMIT :limit");
    }

    @Test
    void buildsFullCatalogTopKCosineScanQueryWithoutIssueIdFilter() {
        String sql = PgvectorRecommendedIssueSimilaritySql.rankCatalogTopK();

        assertThat(sql).contains("FROM recommended_issues");
        assertThat(sql).contains("WHERE issue_embedding IS NOT NULL");
        assertThat(sql).doesNotContain("WHERE issue_id IN (:issueIds)");
        assertThat(sql).containsPattern("ORDER BY issue_embedding <=> CAST\\(:queryVector AS vector\\) ASC\\s+LIMIT :limit");
        assertThat(sql).contains("chr(31)");
    }

    @Test
    void buildsStoredProfileEmbeddingLookupQuery() {
        String lookupSql = PgvectorRecommendedIssueSimilaritySql.findStoredProfileEmbedding();

        assertThat(lookupSql)
            .contains("FROM user_profiles")
            .contains("profile_ready = TRUE")
            .contains("profile_embedding IS NOT NULL");
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
