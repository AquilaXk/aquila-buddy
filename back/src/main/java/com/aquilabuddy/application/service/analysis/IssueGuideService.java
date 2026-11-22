package com.aquilabuddy.application.service.analysis;

import com.aquilabuddy.application.port.in.analysis.GetIssueGuideUseCase;
import com.aquilabuddy.application.port.in.analysis.GetIssuePseudoCodeUseCase;
import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueUpdatedAtPort;
import com.aquilabuddy.application.port.out.analysis.LoadRepositoryCodeContextPort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.application.port.out.issue.UserRecommendedIssuePort;
import com.aquilabuddy.application.port.out.user.CurrentUserGithubIdPort;
import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideOutputContract;
import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import com.aquilabuddy.domain.analysis.IssueGuideWorkflow;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.analysis.RepositoryCodeContextFile;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 이슈 작업 가이드 조회 및 실시간 스트리밍 유스케이스를 수행합니다.
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
    private final UserRecommendedIssuePort userRecommendedIssuePort;
    private final CurrentUserGithubIdPort currentUserGithubIdPort;
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
            issue -> Optional.empty(),
            null,
            null,
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
        ObjectProvider<UserRecommendedIssuePort> userRecommendedIssuePortProvider,
        ObjectProvider<CurrentUserGithubIdPort> currentUserGithubIdPortProvider,
        @Value("${app.analysis.issue-guide.cache-valid-days:3}") long cacheValidDays
    ) {
        this(
            loadIssueGuidePort,
            generateIssueGuidePort,
            saveIssueGuidePort,
            issueGuideGenerationLimitPort,
            findRecommendedIssuePort,
            loadRepositoryCodeContextPortProvider.getIfAvailable(() -> issue -> RepositoryCodeContext.empty()),
            loadIssueUpdatedAtPortProvider.getIfAvailable(() -> issue -> Optional.empty()),
            userRecommendedIssuePortProvider.getIfAvailable(),
            currentUserGithubIdPortProvider.getIfAvailable(),
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
        this(
            loadIssueGuidePort,
            generateIssueGuidePort,
            saveIssueGuidePort,
            issueGuideGenerationLimitPort,
            findRecommendedIssuePort,
            loadRepositoryCodeContextPort,
            loadIssueUpdatedAtPort,
            null,
            null,
            clock,
            cacheTtl
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
        UserRecommendedIssuePort userRecommendedIssuePort,
        CurrentUserGithubIdPort currentUserGithubIdPort,
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
        this.userRecommendedIssuePort = userRecommendedIssuePort;
        this.currentUserGithubIdPort = currentUserGithubIdPort;
        this.clock = clock;
        this.cacheTtl = cacheTtl == null ? Duration.ofDays(3) : cacheTtl;
    }

    @Override
    public IssueGuide findByIssueId(String issueId) {
        IssueGuide cached = findReusableCachedGuide(issueId);
        if (cached != null) {
            issueGuideGenerationLimitPort.recordCacheHit(issueId);
            recordUserAnalyzedSafely(issueId);
            return cached;
        }
        return generateAndCacheIssueGuide(issueId);
    }

    @Override
    public void streamByIssueId(String issueId, Consumer<IssueGuideStreamChunk> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null.");
        IssueGuide cached = findReusableCachedGuide(issueId);
        if (cached != null) {
            consumer.accept(IssueGuideStreamChunk.analyzing("저장된 이슈 분석 가이드를 확인하는 중..."));
            issueGuideGenerationLimitPort.recordCacheHit(issueId);
            recordUserAnalyzedSafely(issueId);
            emitCachedGuideChunks(cached, consumer);
            return;
        }

        RecommendedIssue issue = findRecommendedIssuePort.findById(issueId)
            .orElseThrow(() -> new NoSuchElementException("Recommended issue not found: " + issueId));

        consumer.accept(IssueGuideStreamChunk.analyzing("저장소 코드 맥락 분석 중..."));
        issueGuideGenerationLimitPort.acquireGenerationSlot(issueId);
        try {
            RepositoryCodeContext codeContext = loadRepositoryCodeContext(issue);
            consumer.accept(IssueGuideStreamChunk.files(selectedFilePaths(codeContext)));
            IssueGuide generatedGuide = generateIssueGuidePort.streamIssueGuide(issue, codeContext, consumer);
            saveIssueGuidePort.saveIssueGuide(generatedGuide);
            issueGuideGenerationLimitPort.recordGenerationSucceeded(issueId);
            recordUserAnalyzedSafely(issueId);
            consumer.accept(IssueGuideStreamChunk.sideEffects(generatedGuide.sideEffects()));
            consumer.accept(IssueGuideStreamChunk.complete(generatedGuide));
        } catch (RuntimeException exception) {
            issueGuideGenerationLimitPort.recordGenerationFailed(issueId, exception.getMessage());
            throw exception;
        }
    }

    private IssueGuide findReusableCachedGuide(String issueId) {
        try {
            CachedIssueGuide cachedGuide = loadIssueGuidePort.loadCachedIssueGuide(issueId);
            if (canReuseCachedGuide(issueId, cachedGuide)) {
                return cachedGuide.guide();
            }
            return null;
        } catch (NoSuchElementException ignored) {
            return null;
        }
    }

    private boolean canReuseCachedGuide(String issueId, CachedIssueGuide cachedGuide) {
        if (!IssueGuideOutputContract.isCurrent(cachedGuide.outputVersion())) {
            return false;
        }

        Instant expiresAt = cachedGuide.generatedAt().plus(cacheTtl);
        if (!expiresAt.isBefore(clock.instant())) {
            return true;
        }

        return findRecommendedIssuePort.findById(issueId)
            .flatMap(loadIssueUpdatedAtPort::loadIssueUpdatedAt)
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
            recordUserAnalyzedSafely(issueId);
            return generatedGuide;
        } catch (RuntimeException exception) {
            issueGuideGenerationLimitPort.recordGenerationFailed(issueId, exception.getMessage());
            throw exception;
        }
    }

    private void recordUserAnalyzedSafely(String issueId) {
        if (userRecommendedIssuePort == null || currentUserGithubIdPort == null) {
            return;
        }
        try {
            long userId = currentUserGithubIdPort.currentGithubId();
            userRecommendedIssuePort.markAnalyzed(userId, issueId, clock.instant());
        } catch (RuntimeException ignored) {
            // 비인증/단위 테스트 컨텍스트에서는 per-user 상태 기록을 생략합니다.
        }
    }

    private static void emitCachedGuideChunks(IssueGuide guide, Consumer<IssueGuideStreamChunk> consumer) {
        consumer.accept(IssueGuideStreamChunk.files(guide.filePaths()));
        if (guide.guideline() != null && !guide.guideline().isBlank()) {
            consumer.accept(IssueGuideStreamChunk.guideline(guide.guideline()));
        }
        if (guide.pseudoCode() != null && !guide.pseudoCode().isBlank()) {
            consumer.accept(IssueGuideStreamChunk.pseudoCode(guide.pseudoCode()));
        }
        consumer.accept(IssueGuideStreamChunk.sideEffects(guide.sideEffects()));
        consumer.accept(IssueGuideStreamChunk.complete(guide));
    }

    private RepositoryCodeContext loadRepositoryCodeContext(RecommendedIssue issue) {
        try {
            return loadRepositoryCodeContextPort.loadRepositoryCodeContext(issue);
        } catch (RuntimeException ignored) {
            return RepositoryCodeContext.empty();
        }
    }

    private static List<String> selectedFilePaths(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return List.of();
        }
        return codeContext.files().stream()
            .map(RepositoryCodeContextFile::path)
            .filter(path -> path != null && !path.isBlank())
            .distinct()
            .toList();
    }

    @Override
    public IssueGuideWorkflow findWorkflowByIssueId(String issueId) {
        return IssueGuideWorkflow.from(findByIssueId(issueId));
    }

    @Override
    public IssuePseudoCode findPseudoCodeByIssueId(String issueId) {
        return IssuePseudoCode.from(findByIssueId(issueId));
    }
}
