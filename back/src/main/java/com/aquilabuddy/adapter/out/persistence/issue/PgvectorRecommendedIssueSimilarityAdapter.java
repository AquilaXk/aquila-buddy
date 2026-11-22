package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.aquilabuddy.domain.issue.HybridIssueRrfScorer;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueLanguageMatcher;
import com.aquilabuddy.domain.user.UserProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Postgres pgvector cosine distance와 BM25/신선도/커뮤니티 신호를 결합한 하이브리드 RRF 랭커입니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.recommendation.similarity", name = "provider", havingValue = "pgvector")
public class PgvectorRecommendedIssueSimilarityAdapter implements RankRecommendedIssuesPort {

    private static final double COSINE_WEIGHT = 0.75;
    private static final double LANGUAGE_MATCH_BONUS = 0.25;
    private static final char LABEL_DELIMITER = '\u001F';
    private static final int MAX_STORED_CATALOG_CANDIDATES = 200;

    private final JdbcClient jdbcClient;
    private final EmbeddingClient embeddingClient;
    private final LoadUserProfilePort loadUserProfilePort;
    private final LoadRecommendedIssuesPort loadRecommendedIssuesPort;
    private final TransactionTemplate transactionTemplate;
    private final int limit;

    public PgvectorRecommendedIssueSimilarityAdapter(
        JdbcClient jdbcClient,
        EmbeddingClient embeddingClient,
        LoadUserProfilePort loadUserProfilePort,
        @Value("${app.recommendation.similarity.limit:20}") int limit
    ) {
        this(jdbcClient, embeddingClient, loadUserProfilePort, limit, null, (LoadRecommendedIssuesPort) null);
    }

    public PgvectorRecommendedIssueSimilarityAdapter(
        JdbcClient jdbcClient,
        EmbeddingClient embeddingClient,
        LoadUserProfilePort loadUserProfilePort,
        int limit,
        PlatformTransactionManager transactionManager
    ) {
        this(jdbcClient, embeddingClient, loadUserProfilePort, limit, transactionManager, (LoadRecommendedIssuesPort) null);
    }

    @Autowired
    public PgvectorRecommendedIssueSimilarityAdapter(
        JdbcClient jdbcClient,
        EmbeddingClient embeddingClient,
        LoadUserProfilePort loadUserProfilePort,
        @Value("${app.recommendation.similarity.limit:20}") int limit,
        @Autowired(required = false) PlatformTransactionManager transactionManager,
        @Autowired(required = false) ObjectProvider<LoadRecommendedIssuesPort> loadRecommendedIssuesPortProvider
    ) {
        this(
            jdbcClient,
            embeddingClient,
            loadUserProfilePort,
            limit,
            transactionManager,
            loadRecommendedIssuesPortProvider == null ? null : loadRecommendedIssuesPortProvider.getIfAvailable()
        );
    }

    PgvectorRecommendedIssueSimilarityAdapter(
        JdbcClient jdbcClient,
        EmbeddingClient embeddingClient,
        LoadUserProfilePort loadUserProfilePort,
        int limit,
        PlatformTransactionManager transactionManager,
        LoadRecommendedIssuesPort loadRecommendedIssuesPort
    ) {
        this.jdbcClient = jdbcClient;
        this.embeddingClient = embeddingClient;
        this.loadUserProfilePort = loadUserProfilePort;
        this.limit = Math.max(1, limit);
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
        if (transactionManager == null) {
            this.transactionTemplate = null;
        } else {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setReadOnly(true);
            this.transactionTemplate = template;
        }
    }

    @Override
    public List<RecommendedIssue> rankRecommendedIssues(List<RecommendedIssue> issues) {
        if (issues == null || issues.isEmpty()) {
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
            String queryVectorLiteral = resolveQueryVectorLiteral(profile);
            int queryLimit = Math.max(limit, issues.size());
            List<RecommendedIssue> vectorRanked = executeRankQuery(queryVectorLiteral, issues, queryLimit);
            List<RecommendedIssue> mergedCandidates = mergeCatalogCandidates(issues, vectorRanked);
            if (vectorRanked.isEmpty()) {
                return List.copyOf(mergedCandidates);
            }
            return HybridIssueRrfScorer.rank(mergedCandidates, vectorRanked, profile, LocalDate.now());
        } catch (RuntimeException exception) {
            return List.copyOf(issues);
        }
    }

    private List<RecommendedIssue> mergeCatalogCandidates(
        List<RecommendedIssue> liveCandidates,
        List<RecommendedIssue> vectorRanked
    ) {
        Map<String, RecommendedIssue> byId = new LinkedHashMap<>();
        for (RecommendedIssue issue : liveCandidates) {
            if (issue != null && issue.id() != null) {
                byId.put(issue.id(), issue);
            }
        }
        for (RecommendedIssue issue : vectorRanked) {
            if (issue != null && issue.id() != null) {
                byId.putIfAbsent(issue.id(), issue);
            }
        }
        if (loadRecommendedIssuesPort != null) {
            try {
                List<RecommendedIssue> stored = loadRecommendedIssuesPort.loadRecommendedIssues();
                if (stored != null) {
                    int added = 0;
                    for (RecommendedIssue issue : stored) {
                        if (added >= MAX_STORED_CATALOG_CANDIDATES) {
                            break;
                        }
                        if (issue != null && issue.id() != null && byId.putIfAbsent(issue.id(), issue) == null) {
                            added++;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // 저장소 추가 조회 실패 시 실시간/벡터 후보만으로 계속 진행합니다.
            }
        }
        return List.copyOf(byId.values());
    }

    private String resolveQueryVectorLiteral(UserProfile profile) {
        if (profile.profileReady() && profile.githubId() > 0) {
            String stored = loadStoredProfileEmbedding(profile.githubId());
            if (stored != null && !stored.isBlank()) {
                return stored;
            }
        }
        EmbeddingVector queryVector = embeddingClient.embed(buildUserProfileEmbeddingInput(profile));
        return queryVector.toPgvectorLiteral();
    }

    private String loadStoredProfileEmbedding(long githubId) {
        try {
            return jdbcClient.sql(PgvectorRecommendedIssueSimilaritySql.findStoredProfileEmbedding())
                .param("githubId", githubId)
                .query(String.class)
                .optional()
                .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<RecommendedIssue> executeRankQuery(
        String queryVectorLiteral,
        List<RecommendedIssue> liveCandidates,
        int queryLimit
    ) {
        if (transactionTemplate == null) {
            applyHnswSessionHints();
            return runCatalogOrIdRankQuery(queryVectorLiteral, liveCandidates, queryLimit);
        }
        return transactionTemplate.execute(status -> {
            applyHnswSessionHints();
            return runCatalogOrIdRankQuery(queryVectorLiteral, liveCandidates, queryLimit);
        });
    }

    private List<RecommendedIssue> runCatalogOrIdRankQuery(
        String queryVectorLiteral,
        List<RecommendedIssue> liveCandidates,
        int queryLimit
    ) {
        List<String> issueIds = liveCandidates.stream()
            .map(RecommendedIssue::id)
            .filter(id -> id != null && !id.isBlank())
            .toList();
        if (loadRecommendedIssuesPort == null) {
            return runRankQueryByIds(queryVectorLiteral, issueIds, Math.min(limit, Math.max(1, issueIds.size())));
        }
        List<RecommendedIssue> catalogRanked = runCatalogRankQuery(queryVectorLiteral, queryLimit);
        if (catalogRanked.isEmpty()) {
            return runRankQueryByIds(queryVectorLiteral, issueIds, Math.min(limit, Math.max(1, issueIds.size())));
        }
        Set<String> rankedIds = new HashSet<>(catalogRanked.size());
        for (RecommendedIssue issue : catalogRanked) {
            if (issue != null && issue.id() != null) {
                rankedIds.add(issue.id());
            }
        }
        List<String> missingLiveIds = issueIds.stream()
            .filter(id -> !rankedIds.contains(id))
            .toList();
        if (missingLiveIds.isEmpty()) {
            return catalogRanked;
        }
        List<RecommendedIssue> liveRanked = runRankQueryByIdsSafe(queryVectorLiteral, missingLiveIds);
        if (liveRanked.isEmpty()) {
            return catalogRanked;
        }
        List<RecommendedIssue> combined = new ArrayList<>(catalogRanked.size() + liveRanked.size());
        combined.addAll(catalogRanked);
        combined.addAll(liveRanked);
        return List.copyOf(combined);
    }

    private List<RecommendedIssue> runRankQueryByIdsSafe(String queryVectorLiteral, List<String> issueIds) {
        try {
            return runRankQueryByIds(queryVectorLiteral, issueIds, issueIds.size());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<RecommendedIssue> runCatalogRankQuery(String queryVectorLiteral, int queryLimit) {
        try {
            List<RecommendedIssue> results = jdbcClient.sql(PgvectorRecommendedIssueSimilaritySql.rankCatalogTopK())
                .param("queryVector", queryVectorLiteral)
                .param("limit", queryLimit)
                .query(this::mapIssue)
                .list();
            return results == null ? List.of() : results;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<RecommendedIssue> runRankQueryByIds(
        String queryVectorLiteral,
        List<String> issueIds,
        int queryLimit
    ) {
        if (issueIds.isEmpty()) {
            return List.of();
        }
        List<RecommendedIssue> results = jdbcClient.sql(PgvectorRecommendedIssueSimilaritySql.rankByIssueIds())
            .param("queryVector", queryVectorLiteral)
            .param("issueIds", issueIds)
            .param("limit", queryLimit)
            .query(this::mapIssue)
            .list();
        return results == null ? List.of() : results;
    }

    private void applyHnswSessionHints() {
        try {
            String efSupported = jdbcClient.sql("SELECT current_setting('hnsw.ef_search', true)")
                .query(String.class)
                .optional()
                .orElse(null);
            if (efSupported != null) {
                jdbcClient.sql("SET LOCAL hnsw.ef_search = 80").update();
            }
            String iterativeSupported = jdbcClient.sql("SELECT current_setting('hnsw.iterative_scan', true)")
                .query(String.class)
                .optional()
                .orElse(null);
            if (iterativeSupported != null) {
                jdbcClient.sql("SET LOCAL hnsw.iterative_scan = 'relaxed_order'").update();
            }
        } catch (RuntimeException ignored) {
            // pgvector 0.8+ GUC를 지원하지 않는 환경에서는 기본 스캔 설정으로 진행합니다.
        }
    }

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
                issue.copyOutputVersion(),
                issue.bookmarked()
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

    static List<String> splitLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return List.of();
        }
        String delimiterRegex = labels.indexOf(LABEL_DELIMITER) >= 0 ? "\\u001F" : ",";
        return Arrays.stream(labels.split(delimiterRegex))
            .map(String::trim)
            .filter(label -> !label.isBlank())
            .toList();
    }
}
