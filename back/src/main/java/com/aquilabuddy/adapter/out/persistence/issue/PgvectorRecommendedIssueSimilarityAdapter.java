package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueLanguageMatcher;
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

    private static final double COSINE_WEIGHT = 0.75;
    private static final double LANGUAGE_MATCH_BONUS = 0.25;

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

        if (loadUserProfilePort == null) {
            return List.copyOf(issues);
        }

        try {
            UserProfile profile = loadUserProfilePort.loadCurrentUserProfile();
            if (profile == null) {
                return List.copyOf(issues);
            }
            EmbeddingVector queryVector = embeddingClient.embed(buildUserProfileEmbeddingInput(profile));
            List<String> issueIds = issues.stream()
                .map(RecommendedIssue::id)
                .toList();

            List<RecommendedIssue> queryResults = jdbcClient.sql(PgvectorRecommendedIssueSimilaritySql.rankByIssueIds())
                .param("queryVector", queryVector.toPgvectorLiteral())
                .param("issueIds", issueIds)
                .param("limit", Math.min(limit, issueIds.size()))
                .query(this::mapIssue)
                .list();

            // 코사인 유사도 점수와 주 사용 언어 일치 가중치를 결합한 하이브리드 랭킹 적용
            List<RecommendedIssue> hybridRankedIssues = applyHybridRanking(queryResults, profile.primaryLanguages());
            return mergeRankedWithScoreFallback(issues, hybridRankedIssues);
        } catch (RuntimeException exception) {
            return List.copyOf(issues);
        }
    }

    /**
     * 임베딩 기반 코사인 유사도 점수와 사용자의 주 사용 언어 일치 여부를 결합하여 다면 하이브리드 점수를 계산하고 랭킹을 최적화합니다.
     */
    static List<RecommendedIssue> applyHybridRanking(
        List<RecommendedIssue> rankedIssues,
        List<String> primaryLanguages
    ) {
        if (rankedIssues.isEmpty()) {
            return List.of();
        }

        List<String> normalizedLanguages = primaryLanguages == null
            ? List.of()
            : primaryLanguages.stream()
                .filter(lang -> lang != null && !lang.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .toList();

        List<HybridScoreHolder> scoredList = new ArrayList<>();
        for (RecommendedIssue issue : rankedIssues) {
            double rawSimilarity = Math.max(0.0, Math.min(1.0, issue.score()));
            String matchedLanguage = RecommendedIssueLanguageMatcher.findMatchingLanguage(issue, normalizedLanguages);
            boolean isLanguageMatched = matchedLanguage != null;

            double hybridScore = isLanguageMatched
                ? Math.min(1.0, rawSimilarity * COSINE_WEIGHT + LANGUAGE_MATCH_BONUS)
                : rawSimilarity * COSINE_WEIGHT;

            String baseReason = issue.reason() == null ? "" : issue.reason();
            String updatedReason = isLanguageMatched && !baseReason.contains("주 사용 언어 일치")
                ? (baseReason.isBlank() ? "[주 사용 언어 일치: " + matchedLanguage + "]" : baseReason + " [주 사용 언어 일치: " + matchedLanguage + "]")
                : baseReason;

            RecommendedIssue updatedIssue = new RecommendedIssue(
                issue.id(),
                issue.repoFullName(),
                issue.issueNumber(),
                issue.title(),
                issue.summary(),
                Math.round(hybridScore * 10000.0) / 10000.0,
                issue.labels(),
                issue.status(),
                updatedReason,
                issue.copyOutputVersion()
            );

            scoredList.add(new HybridScoreHolder(updatedIssue, hybridScore, issue.issueNumber()));
        }

        scoredList.sort((a, b) -> {
            int scoreCompare = Double.compare(b.hybridScore(), a.hybridScore());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Long.compare(a.issueNumber(), b.issueNumber());
        });

        return scoredList.stream()
            .map(HybridScoreHolder::issue)
            .toList();
    }

    static boolean matchesLanguage(String text, String lang) {
        return RecommendedIssueLanguageMatcher.matchesLanguage(text, lang);
    }

    private record HybridScoreHolder(RecommendedIssue issue, double hybridScore, long issueNumber) {
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
        if (profile == null) {
            return "";
        }
        List<String> languages = profile.primaryLanguages() != null ? profile.primaryLanguages() : List.of();
        List<String> topics = profile.topics() != null ? profile.topics() : List.of();
        return String.join("\n",
            "Login: " + (profile.login() != null ? profile.login() : ""),
            "Name: " + (profile.name() != null ? profile.name() : ""),
            "Primary languages: " + String.join(", ", languages),
            "Topics: " + String.join(", ", topics)
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
            resultSet.getString("reason"),
            resultSet.getString("copy_output_version")
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
