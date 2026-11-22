package com.aquilabuddy.application.service.issue;

import com.aquilabuddy.application.port.in.issue.BookmarkRecommendedIssueUseCase;
import com.aquilabuddy.application.port.in.issue.CrawlSearchIssuesUseCase;
import com.aquilabuddy.application.port.in.issue.GetRecommendedIssueHistoryUseCase;
import com.aquilabuddy.application.port.in.issue.GetRecommendedIssueUseCase;
import com.aquilabuddy.application.port.in.issue.ListBookmarkedIssuesUseCase;
import com.aquilabuddy.application.port.in.issue.ListCollectedIssuesUseCase;
import com.aquilabuddy.application.port.in.issue.LookupRecommendedIssueUseCase;
import com.aquilabuddy.application.port.in.issue.RecommendIssuesUseCase;
import com.aquilabuddy.application.port.out.analysis.LoadAnalyzedIssueIdsPort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.LoadRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SearchRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.application.port.out.issue.UserRecommendedIssuePort;
import com.aquilabuddy.application.port.out.user.CurrentUserGithubIdPort;
import com.aquilabuddy.application.port.out.user.LoadUserProfilePort;
import com.aquilabuddy.domain.issue.IssueFingerprintSimHash;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyQuality;
import com.aquilabuddy.domain.issue.RecommendedIssueHistory;
import com.aquilabuddy.domain.issue.RecommendedIssueLanguageMatcher;
import com.aquilabuddy.domain.issue.UserIssueState;
import com.aquilabuddy.domain.user.UserProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 추천 이슈 조회·북마크·이력 유스케이스를 수행합니다.
 */
@Service
public class RecommendedIssueService implements
    RecommendIssuesUseCase,
    CrawlSearchIssuesUseCase,
    GetRecommendedIssueHistoryUseCase,
    GetRecommendedIssueUseCase,
    LookupRecommendedIssueUseCase,
    ListCollectedIssuesUseCase,
    BookmarkRecommendedIssueUseCase,
    ListBookmarkedIssuesUseCase {

    private final LoadRecommendedIssuesPort loadRecommendedIssuesPort;
    private final LoadCollectedIssuesPort loadCollectedIssuesPort;
    private final FindRecommendedIssuePort findRecommendedIssuePort;
    private final SaveRecommendedIssuesPort saveRecommendedIssuesPort;
    private final SearchRecommendedIssuesPort searchRecommendedIssuesPort;
    private final RankRecommendedIssuesPort rankRecommendedIssuesPort;
    private final SummarizeRecommendedIssuePort summarizeRecommendedIssuePort;
    private final LoadUserProfilePort loadUserProfilePort;
    private final LoadAnalyzedIssueIdsPort loadAnalyzedIssueIdsPort;
    private final UserRecommendedIssuePort userRecommendedIssuePort;
    private final CurrentUserGithubIdPort currentUserGithubIdPort;

    @Autowired
    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort,
        @Autowired(required = false) LoadUserProfilePort loadUserProfilePort,
        @Autowired(required = false) LoadAnalyzedIssueIdsPort loadAnalyzedIssueIdsPort,
        @Autowired(required = false) UserRecommendedIssuePort userRecommendedIssuePort,
        @Autowired(required = false) CurrentUserGithubIdPort currentUserGithubIdPort
    ) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
        this.loadCollectedIssuesPort = loadCollectedIssuesPort;
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.saveRecommendedIssuesPort = saveRecommendedIssuesPort;
        this.searchRecommendedIssuesPort = searchRecommendedIssuesPort;
        this.rankRecommendedIssuesPort = rankRecommendedIssuesPort;
        this.summarizeRecommendedIssuePort = summarizeRecommendedIssuePort;
        this.loadUserProfilePort = loadUserProfilePort;
        this.loadAnalyzedIssueIdsPort = loadAnalyzedIssueIdsPort;
        this.userRecommendedIssuePort = userRecommendedIssuePort;
        this.currentUserGithubIdPort = currentUserGithubIdPort;
    }

    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort,
        LoadUserProfilePort loadUserProfilePort,
        LoadAnalyzedIssueIdsPort loadAnalyzedIssueIdsPort
    ) {
        this(
            loadRecommendedIssuesPort,
            loadCollectedIssuesPort,
            findRecommendedIssuePort,
            saveRecommendedIssuesPort,
            searchRecommendedIssuesPort,
            rankRecommendedIssuesPort,
            summarizeRecommendedIssuePort,
            loadUserProfilePort,
            loadAnalyzedIssueIdsPort,
            null,
            null
        );
    }

    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort,
        LoadUserProfilePort loadUserProfilePort
    ) {
        this(
            loadRecommendedIssuesPort,
            loadCollectedIssuesPort,
            findRecommendedIssuePort,
            saveRecommendedIssuesPort,
            searchRecommendedIssuesPort,
            rankRecommendedIssuesPort,
            summarizeRecommendedIssuePort,
            loadUserProfilePort,
            null
        );
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

    @Override
    public List<RecommendedIssue> recommend() {
        UserProfile userProfile = loadCurrentUserProfileSafely();

        try {
            List<RecommendedIssue> deduplicatedSearch = IssueFingerprintSimHash.deduplicateCandidates(
                searchRecommendedIssuesPort.searchRecommendedIssues()
            );
            List<RecommendedIssue> searchedIssues = summarizeSafely(deduplicatedSearch);
            if (!searchedIssues.isEmpty()) {
                saveRecommendedIssuesPort.saveRecommendedIssues(searchedIssues);
                return enrichBookmarks(rankRecommendedIssuesPort.rankRecommendedIssues(searchedIssues));
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
        return enrichBookmarks(rankRecommendedIssuesPort.rankRecommendedIssues(candidateIssues));
    }

    @Override
    public List<RecommendedIssue> crawlSearchIssues(String query) {
        List<RecommendedIssue> deduplicated = IssueFingerprintSimHash.deduplicateCandidates(
            searchRecommendedIssuesPort.searchRecommendedIssues(query)
        );
        List<RecommendedIssue> searchedIssues = summarizeSafely(deduplicated);
        saveRecommendedIssuesPort.saveRecommendedIssues(searchedIssues);
        return enrichBookmarks(searchedIssues);
    }

    @Override
    public List<RecommendedIssue> listCollectedIssues() {
        return enrichBookmarks(summarizeAndPersistIfChanged(loadCollectedIssuesPort.loadCollectedIssues()));
    }

    @Override
    public Optional<RecommendedIssue> getRecommendedIssue(String id) {
        return findRecommendedIssuePort.findById(id)
            .flatMap(this::summarizeAndPersistIfChanged)
            .map(this::enrichBookmark);
    }

    @Override
    public List<RecommendedIssueHistory> history() {
        List<RecommendedIssue> issues = summarizeAndPersistIfChanged(loadRecommendedIssuesPort.loadRecommendedIssues());
        if (issues.isEmpty()) {
            return List.of();
        }
        Long currentUserId = currentUserGithubIdSafely();
        if (userRecommendedIssuePort != null && currentUserId != null) {
            Map<String, UserIssueState> states = loadUserIssueStatesSafely(currentUserId, issues);
            return issues.stream()
                .map(issue -> {
                    UserIssueState state = states.get(issue.id());
                    boolean analyzed = state != null && state.analyzed();
                    boolean bookmarked = state != null && state.bookmarked();
                    return RecommendedIssueHistory.ofState(issue.withBookmarked(bookmarked), analyzed, bookmarked);
                })
                .toList();
        }
        Set<String> analyzedIssueIds = loadAnalyzedIssueIdsSafely(issues);
        return issues.stream()
            .map(issue -> RecommendedIssueHistory.ofAnalysisState(issue, analyzedIssueIds.contains(issue.id())))
            .toList();
    }

    @Override
    public Optional<RecommendedIssue> lookup(String repoFullName, long issueNumber) {
        return findRecommendedIssuePort.findByRepoFullNameAndIssueNumber(repoFullName, issueNumber)
            .flatMap(this::summarizeAndPersistIfChanged)
            .map(this::enrichBookmark);
    }

    @Override
    public List<RecommendedIssue> listBookmarks() {
        if (userRecommendedIssuePort == null) {
            return List.of();
        }
        long userId = requireCurrentUserGithubId();
        return userRecommendedIssuePort.loadBookmarkedIssues(userId);
    }

    @Override
    public RecommendedIssue bookmark(String issueId) {
        long userId = requireCurrentUserGithubId();
        RecommendedIssue issue = findRecommendedIssuePort.findById(issueId)
            .orElseThrow(() -> new NoSuchElementException("추천 이슈를 찾을 수 없습니다."));
        if (userRecommendedIssuePort != null) {
            userRecommendedIssuePort.setBookmarked(userId, issue.id(), true, Instant.now());
        }
        return issue.withBookmarked(true);
    }

    @Override
    public RecommendedIssue unbookmark(String issueId) {
        long userId = requireCurrentUserGithubId();
        RecommendedIssue issue = findRecommendedIssuePort.findById(issueId)
            .orElseThrow(() -> new NoSuchElementException("추천 이슈를 찾을 수 없습니다."));
        if (userRecommendedIssuePort != null) {
            userRecommendedIssuePort.setBookmarked(userId, issue.id(), false, Instant.now());
        }
        return issue.withBookmarked(false);
    }

    private long requireCurrentUserGithubId() {
        if (currentUserGithubIdPort == null) {
            throw new IllegalStateException("현재 사용자 식별 포트를 사용할 수 없습니다.");
        }
        return currentUserGithubIdPort.currentGithubId();
    }

    private Long currentUserGithubIdSafely() {
        if (currentUserGithubIdPort == null) {
            return null;
        }
        try {
            return currentUserGithubIdPort.currentGithubId();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<RecommendedIssue> enrichBookmarks(List<RecommendedIssue> issues) {
        if (issues == null || issues.isEmpty() || userRecommendedIssuePort == null) {
            return issues == null ? List.of() : issues;
        }
        Long currentUserId = currentUserGithubIdSafely();
        if (currentUserId == null) {
            return issues;
        }
        Map<String, UserIssueState> states = loadUserIssueStatesSafely(currentUserId, issues);
        if (states.isEmpty()) {
            return issues;
        }
        return issues.stream()
            .map(issue -> {
                UserIssueState state = states.get(issue.id());
                return state == null ? issue : issue.withBookmarked(state.bookmarked());
            })
            .toList();
    }

    private RecommendedIssue enrichBookmark(RecommendedIssue issue) {
        if (issue == null || userRecommendedIssuePort == null) {
            return issue;
        }
        Long currentUserId = currentUserGithubIdSafely();
        if (currentUserId == null) {
            return issue;
        }
        Map<String, UserIssueState> states = loadUserIssueStatesSafely(currentUserId, List.of(issue));
        UserIssueState state = states.get(issue.id());
        return state == null ? issue : issue.withBookmarked(state.bookmarked());
    }

    private Map<String, UserIssueState> loadUserIssueStatesSafely(long userId, List<RecommendedIssue> issues) {
        if (userRecommendedIssuePort == null || issues == null || issues.isEmpty()) {
            return Map.of();
        }
        List<String> ids = issues.stream()
            .map(RecommendedIssue::id)
            .filter(Objects::nonNull)
            .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return Objects.requireNonNullElse(
                userRecommendedIssuePort.findStatesByUserAndIssueIds(userId, ids),
                Map.of()
            );
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Set<String> loadAnalyzedIssueIdsSafely(List<RecommendedIssue> issues) {
        if (loadAnalyzedIssueIdsPort == null || issues == null || issues.isEmpty()) {
            return Set.of();
        }
        List<String> ids = issues.stream()
            .map(RecommendedIssue::id)
            .filter(Objects::nonNull)
            .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        try {
            return Objects.requireNonNullElse(loadAnalyzedIssueIdsPort.findAnalyzedIssueIds(ids), Set.of());
        } catch (RuntimeException ignored) {
            return Set.of();
        }
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
        List<RecommendedIssue> changedIssues = collectChangedIssues(refreshCandidates, refreshedIssues);
        if (changedIssues.isEmpty()) {
            return storedIssues;
        }
        saveRecommendedIssuesPort.saveRecommendedIssues(changedIssues);
        return mergeRefreshedIssues(storedIssues, changedIssues);
    }

    private static List<RecommendedIssue> collectChangedIssues(
        List<RecommendedIssue> refreshCandidates,
        List<RecommendedIssue> refreshedIssues
    ) {
        int limit = Math.min(refreshCandidates.size(), refreshedIssues.size());
        List<RecommendedIssue> changed = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            RecommendedIssue candidate = refreshCandidates.get(index);
            RecommendedIssue refreshed = refreshedIssues.get(index);
            if (refreshed != null && !refreshed.equals(candidate)) {
                changed.add(refreshed);
            }
        }
        return List.copyOf(changed);
    }

    private static List<RecommendedIssue> mergeRefreshedIssues(
        List<RecommendedIssue> storedIssues,
        List<RecommendedIssue> changedIssues
    ) {
        if (changedIssues.isEmpty()) {
            return storedIssues;
        }
        Map<String, RecommendedIssue> refreshedById = new HashMap<>(changedIssues.size() * 2);
        for (RecommendedIssue refreshed : changedIssues) {
            refreshedById.put(refreshed.id(), refreshed);
        }
        List<RecommendedIssue> result = new ArrayList<>(storedIssues.size());
        for (RecommendedIssue stored : storedIssues) {
            result.add(refreshedById.getOrDefault(stored.id(), stored));
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
