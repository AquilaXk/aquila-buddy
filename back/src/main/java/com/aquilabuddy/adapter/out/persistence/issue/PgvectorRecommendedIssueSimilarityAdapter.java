package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.user.UserProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Postgres pgvector cosine distance로 현재 사용자와 가까운 이슈를 우선 추천합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.recommendation.similarity", name = "provider", havingValue = "pgvector")
public class PgvectorRecommendedIssueSimilarityAdapter implements RankRecommendedIssuesPort {

    private final JdbcClient jdbcClient;
    private final EmbeddingClient embeddingClient;
    private final LoadUserProfilePort loadUserProfilePort;
    private final int limit;

    public PgvectorRecommendedIssueSimilarityAdapter(
        JdbcClient jdbcClient,
        EmbeddingClient embeddingClient,
        LoadUserProfilePort loadUserProfilePort,
        @Value("${app.recommendation.similarity.limit:20}") int limit
    ) {
        this.jdbcClient = jdbcClient;
        this.embeddingClient = embeddingClient;
        this.loadUserProfilePort = loadUserProfilePort;
        this.limit = limit;
    }

    @Override
    public List<RecommendedIssue> rankRecommendedIssues(List<RecommendedIssue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }

        UserProfile profile = loadUserProfilePort.loadCurrentUserProfile();
        EmbeddingVector queryVector = embeddingClient.embed(buildUserProfileEmbeddingInput(profile));
        List<String> issueIds = issues.stream()
            .map(RecommendedIssue::id)
            .toList();

        List<RecommendedIssue> rankedIssues = jdbcClient.sql(PgvectorRecommendedIssueSimilaritySql.rankByIssueIds())
            .param("queryVector", queryVector.toPgvectorLiteral())
            .param("issueIds", issueIds)
            .param("limit", Math.min(limit, issueIds.size()))
            .query(this::mapIssue)
            .list();

        return mergeRankedWithScoreFallback(issues, rankedIssues);
    }

    static List<RecommendedIssue> mergeRankedWithScoreFallback(
        List<RecommendedIssue> candidates,
        List<RecommendedIssue> rankedIssues
    ) {
        if (rankedIssues.isEmpty()) {
            return List.copyOf(candidates);
        }

        List<RecommendedIssue> result = new ArrayList<>(rankedIssues);
        Set<String> rankedIds = new HashSet<>();
        rankedIssues.stream()
            .map(RecommendedIssue::id)
            .forEach(rankedIds::add);
        candidates.stream()
            .filter(issue -> !rankedIds.contains(issue.id()))
            .forEach(result::add);
        return List.copyOf(result);
    }

    private static String buildUserProfileEmbeddingInput(UserProfile profile) {
        return String.join("\n",
            "Login: " + profile.login(),
            "Name: " + profile.name(),
            "Primary languages: " + String.join(", ", profile.primaryLanguages()),
            "Topics: " + String.join(", ", profile.topics())
        );
    }

    private RecommendedIssue mapIssue(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RecommendedIssue(
            resultSet.getString("issue_id"),
            resultSet.getString("repo_full_name"),
            resultSet.getLong("issue_number"),
            resultSet.getString("title"),
            resultSet.getString("summary"),
            resultSet.getDouble("similarity_score"),
            splitLabels(resultSet.getString("labels")),
            resultSet.getString("status"),
            resultSet.getString("reason")
        );
    }

    private static List<String> splitLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return List.of();
        }
        return Arrays.stream(labels.split(","))
            .map(String::trim)
            .filter(label -> !label.isBlank())
            .toList();
    }
}
