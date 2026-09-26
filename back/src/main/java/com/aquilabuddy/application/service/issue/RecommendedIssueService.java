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
     * 최신 검색 후보를 우선 반환하고, 검색이 비어 있거나 실패하면 저장된 추천 이슈로 대체합니다.
     * 신규 사용자이거나 저장된 후보가 부족한 콜드 스타트 시점에는 기술스택 기반 입문 큐레이션을 제공합니다.
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

        // 콜드 스타트 대응: 저장된 추천 데이터가 없거나, 신규 사용자의 기술스택과 일치하는 후보가 없는 경우
        boolean isColdStart = storedIssues.isEmpty()
            || (isNewUser(userProfile) && !hasTechStackMatch(storedIssues, userProfile));

        if (isColdStart) {
            List<RecommendedIssue> curatedIssues = curateStarterIssues(userProfile);
            if (!curatedIssues.isEmpty()) {
                List<RecommendedIssue> summarizedCurated = summarizeSafely(curatedIssues);
                saveRecommendedIssuesPort.saveRecommendedIssues(summarizedCurated);
                return rankRecommendedIssuesPort.rankRecommendedIssues(summarizedCurated);
            }
        }

        List<RecommendedIssue> summarizedStoredIssues = summarizeAndPersistIfChanged(storedIssues);
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

    private boolean isNewUser(UserProfile profile) {
        return profile != null && !profile.profileReady();
    }

    private boolean hasTechStackMatch(List<RecommendedIssue> issues, UserProfile profile) {
        if (profile == null || profile.primaryLanguages() == null || profile.primaryLanguages().isEmpty()) {
            return false;
        }
        return issues.stream().anyMatch(issue -> matchesLanguages(issue, profile.primaryLanguages()));
    }

    private static boolean matchesLanguages(RecommendedIssue issue, List<String> languages) {
        for (String language : languages) {
            if (language == null || language.isBlank()) {
                continue;
            }
            String langLower = language.trim().toLowerCase();
            for (String label : issue.labels()) {
                if (label.toLowerCase().contains(langLower)) {
                    return true;
                }
            }
            if (issue.repoFullName().toLowerCase().contains(langLower)
                || issue.title().toLowerCase().contains(langLower)
                || issue.summary().toLowerCase().contains(langLower)
                || issue.reason().toLowerCase().contains(langLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 신규 사용자 콜드 스타트 시 기술스택(주 사용 언어)에 맞는 엄선된 입문 큐레이션 이슈를 제공합니다.
     */
    private List<RecommendedIssue> curateStarterIssues(UserProfile profile) {
        List<String> primaryLanguages = profile != null && profile.primaryLanguages() != null
            ? profile.primaryLanguages()
            : List.of();

        List<RecommendedIssue> builtin = getBuiltinCuratedStarterIssues();
        if (primaryLanguages.isEmpty()) {
            return builtin;
        }

        List<RecommendedIssue> matched = builtin.stream()
            .filter(issue -> matchesLanguages(issue, primaryLanguages))
            .toList();

        return matched.isEmpty() ? builtin : matched;
    }

    private static List<RecommendedIssue> getBuiltinCuratedStarterIssues() {
        return List.of(
            new RecommendedIssue(
                "curated-starter-java-1",
                "spring-projects/spring-boot",
                101L,
                "[입문 큐레이션] Java/Spring Boot 시작하기 좋은 Good First Issue",
                "신규 기여자를 위한 Java 기반 오픈소스 프로젝트 온보딩 이슈입니다. 문서 및 샘플 코드 개선부터 시작할 수 있습니다.",
                0.90,
                List.of("good first issue", "java", "starter", "beginner-friendly"),
                "OPEN",
                "[입문 큐레이션] Java 기술스택 신규 기여자를 위한 맞춤 추천 이슈입니다."
            ),
            new RecommendedIssue(
                "curated-starter-ts-1",
                "facebook/react",
                202L,
                "[입문 큐레이션] TypeScript 생태계 첫 기여를 위한 맞춤 온보딩 이슈",
                "TypeScript 및 프론트엔드 생태계 입문자를 위한 이슈입니다. 타입 정의와 단위 테스트 보강을 경험할 수 있습니다.",
                0.90,
                List.of("good first issue", "typescript", "starter", "beginner-friendly"),
                "OPEN",
                "[입문 큐레이션] TypeScript 기술스택 신규 기여자를 위한 맞춤 추천 이슈입니다."
            ),
            new RecommendedIssue(
                "curated-starter-py-1",
                "psf/requests",
                303L,
                "[입문 큐레이션] Python 오픈소스 생태계 첫 기여 가이드 이슈",
                "Python 오픈소스 기여를 시작하는 개발자를 위한 문서화 및 단위 테스트 개선 이슈입니다.",
                0.90,
                List.of("good first issue", "python", "starter", "beginner-friendly"),
                "OPEN",
                "[입문 큐레이션] Python 기술스택 신규 기여자를 위한 맞춤 추천 이슈입니다."
            ),
            new RecommendedIssue(
                "curated-starter-go-1",
                "golang/go",
                404L,
                "[입문 큐레이션] Go 언어 생태계 첫 기여 온보딩 이슈",
                "Go 개발자를 위한 오픈소스 입문 이슈로, 표준 라이브러리 예제 및 가이드 개선을 다룹니다.",
                0.90,
                List.of("good first issue", "go", "starter", "beginner-friendly"),
                "OPEN",
                "[입문 큐레이션] Go 기술스택 신규 기여자를 위한 맞춤 추천 이슈입니다."
            )
        );
    }
}
