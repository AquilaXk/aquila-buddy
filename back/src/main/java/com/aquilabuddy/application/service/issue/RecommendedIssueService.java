package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.GetRecommendedIssueHistoryUseCase;
import com.aquilabuddy.application.port.in.issue.GetRecommendedIssueUseCase;
import com.aquilabuddy.application.port.in.issue.LookupRecommendedIssueUseCase;
import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.application.port.in.issue.CrawlSearchIssuesUseCase;
import com.aquilabuddy.application.port.in.issue.ListCollectedIssuesUseCase;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyQuality;
import com.aquilabuddy.domain.issue.RecommendedIssueHistory;
import com.aquilabuddy.domain.issue.RecommendedIssueLanguageMatcher;
import com.aquilabuddy.domain.user.UserProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 추천 이슈 조회 유스케이스를 수행합니다.
 *
 * <p>데이터 출처는 outbound port 뒤로 숨겨 application 계층이 영속성 구현에 의존하지 않게 합니다.
 */
@Service
public class RecommendedIssueService implements
    RecommendIssuesUseCase,
    CrawlSearchIssuesUseCase,
    GetRecommendedIssueHistoryUseCase,
    GetRecommendedIssueUseCase,
    LookupRecommendedIssueUseCase,
    ListCollectedIssuesUseCase {

    private final LoadRecommendedIssuesPort loadRecommendedIssuesPort;
    private final LoadCollectedIssuesPort loadCollectedIssuesPort;
    private final FindRecommendedIssuePort findRecommendedIssuePort;
    private final SaveRecommendedIssuesPort saveRecommendedIssuesPort;
    private final SearchRecommendedIssuesPort searchRecommendedIssuesPort;
    private final RankRecommendedIssuesPort rankRecommendedIssuesPort;
    private final SummarizeRecommendedIssuePort summarizeRecommendedIssuePort;
    private final LoadUserProfilePort loadUserProfilePort;

    @Autowired
    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort,
        @Autowired(required = false) LoadUserProfilePort loadUserProfilePort
    ) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
        this.loadCollectedIssuesPort = loadCollectedIssuesPort;
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.saveRecommendedIssuesPort = saveRecommendedIssuesPort;
        this.searchRecommendedIssuesPort = searchRecommendedIssuesPort;
        this.rankRecommendedIssuesPort = rankRecommendedIssuesPort;
        this.summarizeRecommendedIssuePort = summarizeRecommendedIssuePort;
        this.loadUserProfilePort = loadUserProfilePort;
    }

    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort
    ) {
        this(
            loadRecommendedIssuesPort,
            loadCollectedIssuesPort,
            findRecommendedIssuePort,
            saveRecommendedIssuesPort,
            searchRecommendedIssuesPort,
            rankRecommendedIssuesPort,
            summarizeRecommendedIssuePort,
            null
        );
    }

    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort
    ) {
        this(
            loadRecommendedIssuesPort,
            loadCollectedIssuesPort,
            findRecommendedIssuePort,
            saveRecommendedIssuesPort,
            searchRecommendedIssuesPort,
            rankRecommendedIssuesPort,
            List::copyOf,
            null
        );
    }

    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort
    ) {
        this(
            loadRecommendedIssuesPort,
            loadCollectedIssuesPort,
            findRecommendedIssuePort,
            saveRecommendedIssuesPort,
            searchRecommendedIssuesPort,
            List::copyOf,
            List::copyOf,
            null
        );
    }

    /**
     * 최신 검색 후보를 우선 반환하고, 검색이 비어 있거나 실패하면 실제 저장/수집된 추천 이슈로 대체합니다.
     * 저장/수집된 실제 이슈도 없으면 임의의 더미 데이터를 생성하지 않고 빈 목록을 반환합니다(Fail-Closed).
     */
    @Override
    public List<RecommendedIssue> recommend() {
        UserProfile userProfile = loadCurrentUserProfileSafely();

        try {
            List<RecommendedIssue> searchedIssues = summarizeSafely(searchRecommendedIssuesPort.searchRecommendedIssues());
            if (!searchedIssues.isEmpty()) {
                saveRecommendedIssuesPort.saveRecommendedIssues(searchedIssues);
                return rankRecommendedIssuesPort.rankRecommendedIssues(searchedIssues);
            }
        } catch (RuntimeException ignored) {
            // 외부 검색 장애가 추천 API 전체 실패로 번지지 않도록 저장 데이터 fallback을 유지합니다.
        }

        List<RecommendedIssue> storedIssues = loadRecommendedIssuesPort.loadRecommendedIssues();
        if (storedIssues.isEmpty()) {
            storedIssues = collectAndRecommendIssues(userProfile);
        }
        if (storedIssues.isEmpty()) {
            return List.of();
        }

        List<RecommendedIssue> summarizedStoredIssues = summarizeAndPersistIfChanged(storedIssues);
        List<RecommendedIssue> candidateIssues = filterByPrimaryLanguagesOnColdStart(summarizedStoredIssues, userProfile);
        return rankRecommendedIssuesPort.rankRecommendedIssues(candidateIssues);
    }

    /**
     * 요청 검색어로 수집한 후보를 저장해 이후 추천/이력 조회에서도 재사용할 수 있게 합니다.
     */
    @Override
    public List<RecommendedIssue> crawlSearchIssues(String query) {
        List<RecommendedIssue> searchedIssues = summarizeSafely(searchRecommendedIssuesPort.searchRecommendedIssues(query));
        saveRecommendedIssuesPort.saveRecommendedIssues(searchedIssues);
        return searchedIssues;
    }

    /**
     * 저장된 수집 이슈 catalog를 외부 검색 호출 없이 반환합니다.
     */
    @Override
    public List<RecommendedIssue> listCollectedIssues() {
        return summarizeAndPersistIfChanged(loadCollectedIssuesPort.loadCollectedIssues());
    }

    /**
     * 내부 이슈 ID로 저장된 추천 후보를 조회합니다.
     */
    @Override
    public Optional<RecommendedIssue> getRecommendedIssue(String id) {
        return findRecommendedIssuePort.findById(id)
            .flatMap(this::summarizeAndPersistIfChanged);
    }

    /**
     * 저장된 추천 후보를 이력 응답으로 변환합니다.
     */
    @Override
    public List<RecommendedIssueHistory> history() {
        return summarizeAndPersistIfChanged(loadRecommendedIssuesPort.loadRecommendedIssues())
            .stream()
            .map(RecommendedIssueHistory::notAnalyzed)
            .toList();
    }

    /**
     * GitHub 이슈 식별자로 저장된 추천 이슈를 조회합니다.
     */
    @Override
    public Optional<RecommendedIssue> lookup(String repoFullName, long issueNumber) {
        return findRecommendedIssuePort.findByRepoFullNameAndIssueNumber(repoFullName, issueNumber)
            .flatMap(this::summarizeAndPersistIfChanged);
    }

    private Optional<RecommendedIssue> summarizeAndPersistIfChanged(RecommendedIssue issue) {
        List<RecommendedIssue> summarizedIssues = summarizeAndPersistIfChanged(List.of(issue));
        if (summarizedIssues.isEmpty()) {
            return Optional.of(issue);
        }
        return Optional.of(summarizedIssues.getFirst());
    }

    private List<RecommendedIssue> summarizeAndPersistIfChanged(List<RecommendedIssue> issues) {
        List<RecommendedIssue> storedIssues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
        List<RecommendedIssue> refreshCandidates = storedIssues.stream()
            .filter(RecommendedIssueCopyQuality::needsRefresh)
            .toList();
        if (refreshCandidates.isEmpty()) {
            return storedIssues;
        }

        List<RecommendedIssue> refreshedIssues = summarizeSafely(refreshCandidates);
        List<RecommendedIssue> summarizedIssues = mergeRefreshedIssues(storedIssues, refreshCandidates, refreshedIssues);
        if (!summarizedIssues.equals(storedIssues)) {
            saveRecommendedIssuesPort.saveRecommendedIssues(summarizedIssues);
        }
        return summarizedIssues;
    }

    private static List<RecommendedIssue> mergeRefreshedIssues(
        List<RecommendedIssue> storedIssues,
        List<RecommendedIssue> refreshCandidates,
        List<RecommendedIssue> refreshedIssues
    ) {
        List<RecommendedIssue> result = new ArrayList<>(storedIssues);
        for (int index = 0; index < refreshCandidates.size() && index < refreshedIssues.size(); index++) {
            RecommendedIssue candidate = refreshCandidates.get(index);
            RecommendedIssue refreshed = refreshedIssues.get(index);
            for (int storedIndex = 0; storedIndex < result.size(); storedIndex++) {
                if (result.get(storedIndex).id().equals(candidate.id())) {
                    result.set(storedIndex, refreshed);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private List<RecommendedIssue> summarizeSafely(List<RecommendedIssue> issues) {
        List<RecommendedIssue> safeIssues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
        try {
            List<RecommendedIssue> summarizedIssues = summarizeRecommendedIssuePort.summarizeRecommendedIssues(safeIssues);
            return summarizedIssues == null ? safeIssues : List.copyOf(summarizedIssues);
        } catch (RuntimeException ignored) {
            return safeIssues;
        }
    }

    /**
     * 인증 컨텍스트나 사용자 정보가 없더라도 추천 서비스가 실패하지 않도록 안전하게 프로필을 조회합니다.
     */
    private UserProfile loadCurrentUserProfileSafely() {
        if (loadUserProfilePort == null) {
            return null;
        }
        try {
            return loadUserProfilePort.loadCurrentUserProfile();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isProfileVectorAbsent(UserProfile profile) {
        return profile == null
            || !profile.profileReady()
            || profile.vectorUpdatedAt() == null
            || profile.vectorUpdatedAt().isBlank();
    }

    private List<RecommendedIssue> collectAndRecommendIssues(UserProfile profile) {
        List<RecommendedIssue> collectedIssues = List.copyOf(
            Objects.requireNonNullElse(loadCollectedIssuesPort.loadCollectedIssues(), List.of())
        );
        if (!collectedIssues.isEmpty()) {
            return collectedIssues;
        }

        List<String> primaryLanguages = profile != null && profile.primaryLanguages() != null
            ? profile.primaryLanguages().stream()
                .filter(lang -> lang != null && !lang.isBlank())
                .map(String::trim)
                .toList()
            : List.of();
        if (primaryLanguages.isEmpty()) {
            return List.of();
        }

        for (String language : primaryLanguages) {
            try {
                String query = "language:" + language + " label:\"good first issue\"";
                List<RecommendedIssue> searched = summarizeSafely(
                    searchRecommendedIssuesPort.searchRecommendedIssues(query)
                );
                if (!searched.isEmpty()) {
                    saveRecommendedIssuesPort.saveRecommendedIssues(searched);
                    return searched;
                }
            } catch (RuntimeException ignored) {
                // 개별 언어 검색 실패 시 다음 후보 또는 빈 결과로 진행합니다.
            }
        }
        return List.of();
    }

    private List<RecommendedIssue> filterByPrimaryLanguagesOnColdStart(
        List<RecommendedIssue> issues,
        UserProfile profile
    ) {
        if (issues.isEmpty() || profile == null || !isProfileVectorAbsent(profile)) {
            return issues;
        }
        List<String> primaryLanguages = profile.primaryLanguages() != null
            ? profile.primaryLanguages()
            : List.of();
        if (primaryLanguages.isEmpty()) {
            return issues;
        }
        List<RecommendedIssue> matched = issues.stream()
            .filter(issue -> RecommendedIssueLanguageMatcher.matchesAnyLanguage(issue, primaryLanguages))
            .toList();
        return matched.isEmpty() ? issues : matched;
    }

    static boolean matchesLanguage(String text, String lang) {
        return RecommendedIssueLanguageMatcher.matchesLanguage(text, lang);
    }
}
