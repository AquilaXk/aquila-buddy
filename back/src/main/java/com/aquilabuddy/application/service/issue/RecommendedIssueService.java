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
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyQuality;
import com.aquilabuddy.domain.issue.RecommendedIssueHistory;
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

    @Autowired
    public RecommendedIssueService(
        LoadRecommendedIssuesPort loadRecommendedIssuesPort,
        LoadCollectedIssuesPort loadCollectedIssuesPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        SaveRecommendedIssuesPort saveRecommendedIssuesPort,
        SearchRecommendedIssuesPort searchRecommendedIssuesPort,
        RankRecommendedIssuesPort rankRecommendedIssuesPort,
        SummarizeRecommendedIssuePort summarizeRecommendedIssuePort
    ) {
        this.loadRecommendedIssuesPort = loadRecommendedIssuesPort;
        this.loadCollectedIssuesPort = loadCollectedIssuesPort;
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.saveRecommendedIssuesPort = saveRecommendedIssuesPort;
        this.searchRecommendedIssuesPort = searchRecommendedIssuesPort;
        this.rankRecommendedIssuesPort = rankRecommendedIssuesPort;
        this.summarizeRecommendedIssuePort = summarizeRecommendedIssuePort;
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
            List::copyOf
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
            List::copyOf
        );
    }

    /**
     * 최신 검색 후보를 우선 반환하고, 검색이 비어 있거나 실패하면 저장된 추천 이슈로 대체합니다.
     */
    @Override
    public List<RecommendedIssue> recommend() {
        try {
            List<RecommendedIssue> searchedIssues = summarizeSafely(searchRecommendedIssuesPort.searchRecommendedIssues());
            if (!searchedIssues.isEmpty()) {
                saveRecommendedIssuesPort.saveRecommendedIssues(searchedIssues);
                return rankRecommendedIssuesPort.rankRecommendedIssues(searchedIssues);
            }
        } catch (RuntimeException ignored) {
            // 외부 검색 장애가 추천 API 전체 실패로 번지지 않도록 저장 데이터 fallback을 유지합니다.
        }
        List<RecommendedIssue> summarizedStoredIssues = summarizeAndPersistIfChanged(
            loadRecommendedIssuesPort.loadRecommendedIssues()
        );
        return rankRecommendedIssuesPort.rankRecommendedIssues(summarizedStoredIssues);
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
}
