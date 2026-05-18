package com.aquilabuddy.application.service.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.in.analysis.GetIssuePseudoCodeUseCase;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueUpdatedAtPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.LoadRepositoryCodeContextPort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 이슈 작업 가이드 조회 유스케이스를 수행합니다.
 *
 * <p>가이드 생성/저장 방식이 바뀌어도 incoming port 계약은 유지되도록 outbound port만 호출합니다.
 */
@Service
public class IssueGuideService implements GetIssueGuideUseCase, GetIssuePseudoCodeUseCase {

    private final LoadIssueGuidePort loadIssueGuidePort;
    private final GenerateIssueGuidePort generateIssueGuidePort;
    private final SaveIssueGuidePort saveIssueGuidePort;
    private final IssueGuideGenerationLimitPort issueGuideGenerationLimitPort;
    private final FindRecommendedIssuePort findRecommendedIssuePort;
    private final LoadRepositoryCodeContextPort loadRepositoryCodeContextPort;
    private final LoadIssueUpdatedAtPort loadIssueUpdatedAtPort;
    private final Clock clock;
    private final Duration cacheTtl;

    public IssueGuideService(
        LoadIssueGuidePort loadIssueGuidePort,
        GenerateIssueGuidePort generateIssueGuidePort,
        SaveIssueGuidePort saveIssueGuidePort,
        IssueGuideGenerationLimitPort issueGuideGenerationLimitPort,
        FindRecommendedIssuePort findRecommendedIssuePort
    ) {
        this(
            loadIssueGuidePort,
            generateIssueGuidePort,
            saveIssueGuidePort,
            issueGuideGenerationLimitPort,
            findRecommendedIssuePort,
            issue -> RepositoryCodeContext.empty(),
            issue -> java.util.Optional.empty(),
            Clock.systemUTC(),
            Duration.ofDays(3)
        );
    }

    @Autowired
    public IssueGuideService(
        LoadIssueGuidePort loadIssueGuidePort,
        GenerateIssueGuidePort generateIssueGuidePort,
        SaveIssueGuidePort saveIssueGuidePort,
        IssueGuideGenerationLimitPort issueGuideGenerationLimitPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        ObjectProvider<LoadRepositoryCodeContextPort> loadRepositoryCodeContextPortProvider,
        ObjectProvider<LoadIssueUpdatedAtPort> loadIssueUpdatedAtPortProvider,
        @Value("${app.analysis.issue-guide.cache-valid-days:3}") long cacheValidDays
    ) {
        this(
            loadIssueGuidePort,
            generateIssueGuidePort,
            saveIssueGuidePort,
            issueGuideGenerationLimitPort,
            findRecommendedIssuePort,
            loadRepositoryCodeContextPortProvider.getIfAvailable(() -> issue -> RepositoryCodeContext.empty()),
            loadIssueUpdatedAtPortProvider.getIfAvailable(() -> issue -> java.util.Optional.empty()),
            Clock.systemUTC(),
            Duration.ofDays(Math.max(0, cacheValidDays))
        );
    }

    IssueGuideService(
        LoadIssueGuidePort loadIssueGuidePort,
        GenerateIssueGuidePort generateIssueGuidePort,
        SaveIssueGuidePort saveIssueGuidePort,
        IssueGuideGenerationLimitPort issueGuideGenerationLimitPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        LoadRepositoryCodeContextPort loadRepositoryCodeContextPort,
        LoadIssueUpdatedAtPort loadIssueUpdatedAtPort,
        Clock clock,
        Duration cacheTtl
    ) {
        this.loadIssueGuidePort = loadIssueGuidePort;
        this.generateIssueGuidePort = generateIssueGuidePort;
        this.saveIssueGuidePort = saveIssueGuidePort;
        this.issueGuideGenerationLimitPort = issueGuideGenerationLimitPort;
        this.findRecommendedIssuePort = findRecommendedIssuePort;
        this.loadRepositoryCodeContextPort = loadRepositoryCodeContextPort;
        this.loadIssueUpdatedAtPort = loadIssueUpdatedAtPort;
        this.clock = clock;
        this.cacheTtl = cacheTtl == null ? Duration.ofDays(3) : cacheTtl;
    }

    /**
     * 캐시된 guide를 우선 반환하고, 없으면 추천 이슈 기반 guide를 생성해 저장합니다.
     */
    @Override
    public IssueGuide findByIssueId(String issueId) {
        try {
            CachedIssueGuide cachedGuide = loadIssueGuidePort.loadCachedIssueGuide(issueId);
            if (canReuseCachedGuide(issueId, cachedGuide)) {
                issueGuideGenerationLimitPort.recordCacheHit(issueId);
                return cachedGuide.guide();
            }
            return generateAndCacheIssueGuide(issueId);
        } catch (NoSuchElementException ignored) {
            return generateAndCacheIssueGuide(issueId);
        }
    }

    private boolean canReuseCachedGuide(String issueId, CachedIssueGuide cachedGuide) {
        Instant expiresAt = cachedGuide.generatedAt().plus(cacheTtl);
        if (!expiresAt.isBefore(clock.instant())) {
            return true;
        }

        return findRecommendedIssuePort.findById(issueId)
            .flatMap(loadIssueUpdatedAtPort::loadIssueUpdatedAt)
            // 원격 수정 시각을 확인할 수 없으면 과도한 재생성보다 기존 캐시 재사용을 우선한다.
            .map(updatedAt -> !updatedAt.isAfter(cachedGuide.generatedAt()))
            .orElse(true);
    }

    private IssueGuide generateAndCacheIssueGuide(String issueId) {
        RecommendedIssue issue = findRecommendedIssuePort.findById(issueId)
            .orElseThrow(() -> new NoSuchElementException("Recommended issue not found: " + issueId));

        issueGuideGenerationLimitPort.acquireGenerationSlot(issueId);
        try {
            IssueGuide generatedGuide = generateIssueGuidePort.generateIssueGuide(issue, loadRepositoryCodeContext(issue));
            saveIssueGuidePort.saveIssueGuide(generatedGuide);
            issueGuideGenerationLimitPort.recordGenerationSucceeded(issueId);
            return generatedGuide;
        } catch (RuntimeException exception) {
            issueGuideGenerationLimitPort.recordGenerationFailed(issueId, exception.getMessage());
            throw exception;
        }
    }

    private RepositoryCodeContext loadRepositoryCodeContext(RecommendedIssue issue) {
        try {
            return loadRepositoryCodeContextPort.loadRepositoryCodeContext(issue);
        } catch (RuntimeException ignored) {
            return RepositoryCodeContext.empty();
        }
    }

    /**
     * 저장된 작업 가이드를 UI에서 바로 렌더링 가능한 workflow 단계로 변환합니다.
     */
    @Override
    public IssueGuideWorkflow findWorkflowByIssueId(String issueId) {
        return IssueGuideWorkflow.from(findByIssueId(issueId));
    }

    /**
     * 저장된 작업 가이드에서 의사코드만 분리해 반환합니다.
     */
    @Override
    public IssuePseudoCode findPseudoCodeByIssueId(String issueId) {
        return IssuePseudoCode.from(findByIssueId(issueId));
    }
}
