package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.issue.HybridIssueRrfScorer;
import com.aquilabuddy.domain.issue.IssueFingerprintSimHash;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.user.UserProfile;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * pgvector 비활성 환경에서 저장된 카탈로그 병합 및 BM25/신선도/커뮤니티 하이브리드 RRF 랭킹을 제공합니다.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.recommendation.similarity",
    name = "provider",
    havingValue = "score",
    matchIfMissing = true
)
public class ScoreRecommendedIssueRankingAdapter implements RankRecommendedIssuesPort {

    private static final int MAX_STORED_CATALOG_CANDIDATES = 200;

    private final LoadRecommendedIssuesPort loadRecommendedIssuesPort;
    private final LoadUserProfilePort loadUserProfilePort;

    public ScoreRecommendedIssueRankingAdapter() {
        this((LoadRecommendedIssuesPort) null, (LoadUserProfilePort) null);
    }

    @Autowired
    public ScoreRecommendedIssueRankingAdapter(
        ObjectProvider<LoadRecommendedIssuesPort> loadRecommendedIssuesPortProvider,
        ObjectProvider<LoadUserProfilePort> loadUserProfilePortProvider
    ) {
        this(
            loadRecommendedIssuesPortProvider == null ? null : loadRecommendedIssuesPortProvider.getIfAvailable(),
            loadUserProfilePortProvider == null ? null : loadUserProfilePortProvider.getIfAvailable()
        );
    }

    ScoreRecommendedIssueRankingAdapter(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadUserProfilePort loadUserProfilePort
    ) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
        this.loadUserProfilePort = loadUserProfilePort;
    }

    @Override
    public List<RecommendedIssue> rankRecommendedIssues(List<RecommendedIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        UserProfile profile = loadUserProfileSafely();
        if (profile == null) {
            return List.copyOf(issues);
        }
        List<RecommendedIssue> merged = mergeWithStoredCatalog(issues);
        return HybridIssueRrfScorer.rank(merged, List.of(), profile, LocalDate.now());
    }

    private List<RecommendedIssue> mergeWithStoredCatalog(List<RecommendedIssue> issues) {
        if (loadRecommendedIssuesPort == null) {
            return IssueFingerprintSimHash.deduplicateCandidates(issues);
        }
        Map<String, RecommendedIssue> byId = new LinkedHashMap<>();
        for (RecommendedIssue issue : issues) {
            if (issue != null && issue.id() != null) {
                byId.put(issue.id(), issue);
            }
        }
        try {
            List<RecommendedIssue> stored = loadRecommendedIssuesPort.loadRecommendedIssues();
            if (stored != null) {
                int added = 0;
                for (RecommendedIssue candidate : stored) {
                    if (added >= MAX_STORED_CATALOG_CANDIDATES) {
                        break;
                    }
                    if (candidate != null && candidate.id() != null && byId.putIfAbsent(candidate.id(), candidate) == null) {
                        added++;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 저장소 추가 조회 실패 시 기존 후보 목록만 유지합니다.
        }
        return IssueFingerprintSimHash.deduplicateCandidates(List.copyOf(byId.values()));
    }

    private UserProfile loadUserProfileSafely() {
        if (loadUserProfilePort == null) {
            return null;
        }
        try {
            return loadUserProfilePort.loadCurrentUserProfile();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
