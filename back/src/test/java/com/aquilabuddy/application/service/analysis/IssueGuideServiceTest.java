package com.aquilabuddy.application.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueUpdatedAtPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.domain.analysis.CachedIssueGuide;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssueGuideOutputContract;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 이슈 가이드 application service가 저장된 guide를 목적별 projection으로 변환하는지 검증합니다.
 */
class IssueGuideServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-19T00:00:00Z");
    private static final Duration CACHE_TTL = Duration.ofDays(3);

    @Test
    void findsPseudoCodeByIssueId() {
        IssueGuideService service = serviceWith(new StubIssueGuidePort());

        IssuePseudoCode pseudoCode = service.findPseudoCodeByIssueId("demo-1");

        assertThat(pseudoCode.issueKey()).isEqualTo("demo-1");
        assertThat(pseudoCode.pseudoCode()).isEqualTo("if request is invalid:\n  return bad request");
    }

    @Test
    void returnsCachedIssueGuideWithoutGeneration() {
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(issueGuide("generated-1"));
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        IssueGuideService service = new IssueGuideService(
            new StubIssueGuidePort(),
            generatePort,
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort()
        );

        IssueGuide guide = service.findByIssueId("demo-1");

        assertThat(guide.guideline()).isEqualTo("Validate request fields");
        assertThat(generatePort.generateCount).isZero();
        assertThat(savePort.savedGuide).isNull();
        assertThat(limitPort.cacheHitIssueId).isEqualTo("demo-1");
        assertThat(limitPort.checkedIssueId).isNull();
        assertThat(limitPort.succeededIssueId).isNull();
        assertThat(limitPort.failedIssueId).isNull();
    }

    @Test
    void generatesAndCachesIssueGuideWhenStoredGuideIsMissing() {
        IssueGuide generatedGuide = issueGuide("demo-3");
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(generatedGuide);
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        IssueGuideService service = new IssueGuideService(
            issueId -> {
                throw new NoSuchElementException("Issue guide not found: " + issueId);
            },
            generatePort,
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort()
        );

        IssueGuide guide = service.findByIssueId("demo-3");

        assertThat(guide).isEqualTo(generatedGuide);
        assertThat(limitPort.checkedIssueId).isEqualTo("demo-3");
        assertThat(limitPort.succeededIssueId).isEqualTo("demo-3");
        assertThat(limitPort.failedIssueId).isNull();
        assertThat(generatePort.generatedIssue.id()).isEqualTo("demo-3");
        assertThat(savePort.savedGuide).isEqualTo(generatedGuide);
    }

    @Test
    void recordsGenerationFailureWhenGuideGenerationFails() {
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        IssueGuideService service = new IssueGuideService(
            issueId -> {
                throw new NoSuchElementException("Issue guide not found: " + issueId);
            },
            new FailingGenerateIssueGuidePort(),
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort()
        );

        assertThatThrownBy(() -> service.findByIssueId("demo-4"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI generation failed");

        assertThat(limitPort.checkedIssueId).isEqualTo("demo-4");
        assertThat(limitPort.succeededIssueId).isNull();
        assertThat(limitPort.failedIssueId).isEqualTo("demo-4");
        assertThat(limitPort.failureReason).contains("AI generation failed");
        assertThat(savePort.savedGuide).isNull();
    }

    @Test
    void reusesCachedIssueGuideWithinCacheTtlWithoutRemoteCheck() {
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(issueGuide("generated-ttl"));
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        RecordingIssueUpdatedAtPort issueUpdatedAtPort = new RecordingIssueUpdatedAtPort(
            Optional.of(NOW.plusSeconds(60))
        );
        IssueGuideService service = serviceWithCache(
            new StubCachedIssueGuidePort("demo-ttl", NOW.minus(Duration.ofDays(2))),
            generatePort,
            new RecordingSaveIssueGuidePort(),
            limitPort,
            new StubFindRecommendedIssuePort(),
            issueUpdatedAtPort
        );

        IssueGuide guide = service.findByIssueId("demo-ttl");

        assertThat(guide.issueKey()).isEqualTo("demo-ttl");
        assertThat(generatePort.generateCount).isZero();
        assertThat(issueUpdatedAtPort.checkedIssue).isNull();
        assertThat(limitPort.cacheHitIssueId).isEqualTo("demo-ttl");
        assertThat(limitPort.checkedIssueId).isNull();
    }

    @Test
    void reusesExpiredCachedIssueGuideWhenRemoteIssueWasNotModifiedAfterGeneration() {
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(issueGuide("generated-old"));
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        RecordingIssueUpdatedAtPort issueUpdatedAtPort = new RecordingIssueUpdatedAtPort(
            Optional.of(NOW.minus(Duration.ofDays(6)))
        );
        IssueGuideService service = serviceWithCache(
            new StubCachedIssueGuidePort("demo-old", NOW.minus(Duration.ofDays(5))),
            generatePort,
            new RecordingSaveIssueGuidePort(),
            limitPort,
            new StubFindRecommendedIssuePort(),
            issueUpdatedAtPort
        );

        IssueGuide guide = service.findByIssueId("demo-old");

        assertThat(guide.issueKey()).isEqualTo("demo-old");
        assertThat(generatePort.generateCount).isZero();
        assertThat(issueUpdatedAtPort.checkedIssue.id()).isEqualTo("demo-old");
        assertThat(limitPort.cacheHitIssueId).isEqualTo("demo-old");
        assertThat(limitPort.checkedIssueId).isNull();
    }

    @Test
    void regeneratesExpiredCachedIssueGuideWhenRemoteIssueWasModifiedAfterGeneration() {
        IssueGuide generatedGuide = issueGuide("demo-stale");
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(generatedGuide);
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        RecordingIssueUpdatedAtPort issueUpdatedAtPort = new RecordingIssueUpdatedAtPort(
            Optional.of(NOW.minus(Duration.ofDays(1)))
        );
        IssueGuideService service = serviceWithCache(
            new StubCachedIssueGuidePort("demo-stale", NOW.minus(Duration.ofDays(5))),
            generatePort,
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort(),
            issueUpdatedAtPort
        );

        IssueGuide guide = service.findByIssueId("demo-stale");

        assertThat(guide).isEqualTo(generatedGuide);
        assertThat(generatePort.generateCount).isOne();
        assertThat(savePort.savedGuide).isEqualTo(generatedGuide);
        assertThat(limitPort.checkedIssueId).isEqualTo("demo-stale");
        assertThat(limitPort.succeededIssueId).isEqualTo("demo-stale");
        assertThat(limitPort.cacheHitIssueId).isNull();
    }

    @Test
    void regeneratesCachedIssueGuideWhenOutputVersionIsStale() {
        IssueGuide generatedGuide = issueGuide("demo-stale-version");
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(generatedGuide);
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        RecordingIssueUpdatedAtPort issueUpdatedAtPort = new RecordingIssueUpdatedAtPort(
            Optional.of(NOW.minus(Duration.ofMinutes(5)))
        );
        IssueGuideService service = serviceWithCache(
            new StubCachedIssueGuidePort(
                "demo-stale-version",
                NOW.minus(Duration.ofMinutes(10)),
                IssueGuideOutputContract.LEGACY_VERSION
            ),
            generatePort,
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort(),
            issueUpdatedAtPort
        );

        IssueGuide guide = service.findByIssueId("demo-stale-version");

        assertThat(guide).isEqualTo(generatedGuide);
        assertThat(generatePort.generateCount).isOne();
        assertThat(savePort.savedGuide).isEqualTo(generatedGuide);
        assertThat(issueUpdatedAtPort.checkedIssue).isNull();
        assertThat(limitPort.checkedIssueId).isEqualTo("demo-stale-version");
        assertThat(limitPort.succeededIssueId).isEqualTo("demo-stale-version");
        assertThat(limitPort.cacheHitIssueId).isNull();
    }

    @Test
    void reusesExpiredCachedIssueGuideWhenRemoteIssueUpdatedAtCannotBeLoaded() {
        RecordingGenerateIssueGuidePort generatePort = new RecordingGenerateIssueGuidePort(issueGuide("generated-unknown"));
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        RecordingIssueUpdatedAtPort issueUpdatedAtPort = new RecordingIssueUpdatedAtPort(Optional.empty());
        IssueGuideService service = serviceWithCache(
            new StubCachedIssueGuidePort("demo-unknown", NOW.minus(Duration.ofDays(5))),
            generatePort,
            new RecordingSaveIssueGuidePort(),
            limitPort,
            new StubFindRecommendedIssuePort(),
            issueUpdatedAtPort
        );

        IssueGuide guide = service.findByIssueId("demo-unknown");

        assertThat(guide.issueKey()).isEqualTo("demo-unknown");
        assertThat(generatePort.generateCount).isZero();
        assertThat(issueUpdatedAtPort.checkedIssue.id()).isEqualTo("demo-unknown");
        assertThat(limitPort.cacheHitIssueId).isEqualTo("demo-unknown");
        assertThat(limitPort.checkedIssueId).isNull();
    }

    @Test
    void rejectsGenerationWhenRecommendedIssueIsMissing() {
        IssueGuideService service = new IssueGuideService(
            issueId -> {
                throw new NoSuchElementException("Issue guide not found: " + issueId);
            },
            new RecordingGenerateIssueGuidePort(issueGuide("missing")),
            new RecordingSaveIssueGuidePort(),
            new RecordingIssueGuideGenerationLimitPort(),
            new EmptyFindRecommendedIssuePort()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findByIssueId("missing"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Recommended issue not found");
    }

    @Test
    void streamsAnalyzingAndFilesBeforeLlmAndPersistsValidatedGuideOnCacheMiss() {
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        java.util.List<com.aquilabuddy.domain.analysis.IssueGuideStreamChunk> events = new java.util.ArrayList<>();
        com.aquilabuddy.domain.analysis.RepositoryCodeContext context = com.aquilabuddy.domain.analysis.RepositoryCodeContext.fromFiles(
            "spring-projects/spring-boot",
            "main",
            List.of(new com.aquilabuddy.domain.analysis.RepositoryCodeContextFile("src/main/java/Demo.java", "class Demo {}")),
            5,
            3000
        );
        GenerateIssueGuidePort streamingPort = new GenerateIssueGuidePort() {
            @Override
            public IssueGuide generateIssueGuide(RecommendedIssue issue) {
                throw new AssertionError("non-streaming generate should not be called");
            }

            @Override
            public IssueGuide streamIssueGuide(
                RecommendedIssue issue,
                com.aquilabuddy.domain.analysis.RepositoryCodeContext codeContext,
                java.util.function.Consumer<com.aquilabuddy.domain.analysis.IssueGuideStreamChunk> onChunk
            ) {
                // Verify ANALYZING and FILES were emitted BEFORE LLM call starts
                assertThat(events)
                    .extracting(com.aquilabuddy.domain.analysis.IssueGuideStreamChunk::step)
                    .containsExactly(
                        com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.ANALYZING,
                        com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.FILES
                    );
                assertThat(events.get(1).filePaths()).containsExactly("src/main/java/Demo.java");
                onChunk.accept(com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.guideline("원인 분석"));
                onChunk.accept(com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.pseudoCode("GIVEN Demo"));
                return new IssueGuide(issue.id(), List.of("src/main/java/Demo.java"), "원인 분석", "GIVEN Demo", List.of("side effect"));
            }
        };
        IssueGuideService service = new IssueGuideService(
            issueId -> {
                throw new NoSuchElementException("Issue guide not found: " + issueId);
            },
            streamingPort,
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort(),
            issue -> context,
            issue -> Optional.empty(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            CACHE_TTL
        );

        service.streamByIssueId("demo-stream", events::add);

        assertThat(events)
            .extracting(com.aquilabuddy.domain.analysis.IssueGuideStreamChunk::step)
            .containsExactly(
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.ANALYZING,
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.FILES,
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.GUIDELINE,
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.PSEUDO_CODE,
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.SIDE_EFFECTS,
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.COMPLETE
            );
        assertThat(savePort.savedGuide).isNotNull();
        assertThat(savePort.savedGuide.issueKey()).isEqualTo("demo-stream");
        assertThat(limitPort.succeededIssueId).isEqualTo("demo-stream");
    }

    @Test
    void recordsFailureWithoutSavingWhenStreamingFailsClosed() {
        RecordingSaveIssueGuidePort savePort = new RecordingSaveIssueGuidePort();
        RecordingIssueGuideGenerationLimitPort limitPort = new RecordingIssueGuideGenerationLimitPort();
        java.util.List<com.aquilabuddy.domain.analysis.IssueGuideStreamChunk> events = new java.util.ArrayList<>();
        IssueGuideService service = new IssueGuideService(
            issueId -> {
                throw new NoSuchElementException("Issue guide not found: " + issueId);
            },
            new FailingGenerateIssueGuidePort(),
            savePort,
            limitPort,
            new StubFindRecommendedIssuePort()
        );

        assertThatThrownBy(() -> service.streamByIssueId("demo-fail", events::add))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI generation failed");

        assertThat(events)
            .extracting(com.aquilabuddy.domain.analysis.IssueGuideStreamChunk::step)
            .containsExactly(
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.ANALYZING,
                com.aquilabuddy.domain.analysis.IssueGuideStreamChunk.Step.FILES
            );
        assertThat(savePort.savedGuide).isNull();
        assertThat(limitPort.failedIssueId).isEqualTo("demo-fail");
    }

    private static IssueGuideService serviceWith(LoadIssueGuidePort loadIssueGuidePort) {
        return new IssueGuideService(
            loadIssueGuidePort,
            new RecordingGenerateIssueGuidePort(issueGuide("generated")),
            new RecordingSaveIssueGuidePort(),
            new RecordingIssueGuideGenerationLimitPort(),
            new StubFindRecommendedIssuePort()
        );
    }

    private static IssueGuideService serviceWithCache(
        LoadIssueGuidePort loadIssueGuidePort,
        RecordingGenerateIssueGuidePort generatePort,
        SaveIssueGuidePort savePort,
        RecordingIssueGuideGenerationLimitPort limitPort,
        FindRecommendedIssuePort findRecommendedIssuePort,
        LoadIssueUpdatedAtPort issueUpdatedAtPort
    ) {
        return new IssueGuideService(
            loadIssueGuidePort,
            generatePort,
            savePort,
            limitPort,
            findRecommendedIssuePort,
            issue -> com.aquilabuddy.domain.analysis.RepositoryCodeContext.empty(),
            issueUpdatedAtPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            CACHE_TTL
        );
    }

    private static IssueGuide issueGuide(String issueId) {
        return new IssueGuide(
            issueId,
            "Validate request fields",
            "if request is invalid:\n  return bad request",
            List.of("Clients must handle 400 responses")
        );
    }

    private static RecommendedIssue recommendedIssue(String issueId) {
        return new RecommendedIssue(
            issueId,
            "spring-projects/spring-boot",
            2L,
            "Refine validation error example",
            "Java/Spring validation 예시 개선 이슈입니다.",
            0.87,
            List.of("good first issue", "validation"),
            "OPEN",
            "현재 Java 21 + Spring 작업 스택과 직접 맞닿아 있습니다."
        );
    }

    private static class StubIssueGuidePort implements LoadIssueGuidePort {

        @Override
        public IssueGuide loadIssueGuide(String issueId) {
            return issueGuide(issueId);
        }
    }

    private static class StubCachedIssueGuidePort implements LoadIssueGuidePort {

        private final String issueId;
        private final Instant generatedAt;
        private final String outputVersion;

        private StubCachedIssueGuidePort(String issueId, Instant generatedAt) {
            this(issueId, generatedAt, IssueGuideOutputContract.CURRENT_VERSION);
        }

        private StubCachedIssueGuidePort(String issueId, Instant generatedAt, String outputVersion) {
            this.issueId = issueId;
            this.generatedAt = generatedAt;
            this.outputVersion = outputVersion;
        }

        @Override
        public IssueGuide loadIssueGuide(String issueId) {
            return issueGuide(issueId);
        }

        @Override
        public CachedIssueGuide loadCachedIssueGuide(String issueId) {
            return new CachedIssueGuide(issueGuide(this.issueId), generatedAt, outputVersion);
        }
    }

    private static class RecordingGenerateIssueGuidePort implements GenerateIssueGuidePort {

        private final IssueGuide guide;
        private RecommendedIssue generatedIssue;
        private int generateCount;

        private RecordingGenerateIssueGuidePort(IssueGuide guide) {
            this.guide = guide;
        }

        @Override
        public IssueGuide generateIssueGuide(RecommendedIssue issue) {
            generatedIssue = issue;
            generateCount++;
            return guide;
        }
    }

    private static class FailingGenerateIssueGuidePort implements GenerateIssueGuidePort {

        @Override
        public IssueGuide generateIssueGuide(RecommendedIssue issue) {
            throw new IllegalStateException("AI generation failed");
        }
    }

    private static class RecordingSaveIssueGuidePort implements SaveIssueGuidePort {

        private IssueGuide savedGuide;

        @Override
        public void saveIssueGuide(IssueGuide guide) {
            savedGuide = guide;
        }
    }

    private static class RecordingIssueGuideGenerationLimitPort implements IssueGuideGenerationLimitPort {

        private String checkedIssueId;
        private String cacheHitIssueId;
        private String succeededIssueId;
        private String failedIssueId;
        private String failureReason;

        @Override
        public void acquireGenerationSlot(String issueId) {
            checkedIssueId = issueId;
        }

        @Override
        public void recordCacheHit(String issueId) {
            cacheHitIssueId = issueId;
        }

        @Override
        public void recordGenerationSucceeded(String issueId) {
            succeededIssueId = issueId;
        }

        @Override
        public void recordGenerationFailed(String issueId, String reason) {
            failedIssueId = issueId;
            failureReason = reason;
        }
    }

    private static class StubFindRecommendedIssuePort implements FindRecommendedIssuePort {

        @Override
        public Optional<RecommendedIssue> findById(String id) {
            return Optional.of(recommendedIssue(id));
        }

        @Override
        public Optional<RecommendedIssue> findByRepoFullNameAndIssueNumber(String repoFullName, long issueNumber) {
            return Optional.empty();
        }
    }

    private static class EmptyFindRecommendedIssuePort extends StubFindRecommendedIssuePort {

        @Override
        public Optional<RecommendedIssue> findById(String id) {
            return Optional.empty();
        }
    }

    private static class RecordingIssueUpdatedAtPort implements LoadIssueUpdatedAtPort {

        private final Optional<Instant> updatedAt;
        private RecommendedIssue checkedIssue;

        private RecordingIssueUpdatedAtPort(Optional<Instant> updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Override
        public Optional<Instant> loadIssueUpdatedAt(RecommendedIssue issue) {
            checkedIssue = issue;
            return updatedAt;
        }
    }
}
