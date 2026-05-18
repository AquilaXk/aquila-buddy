package com.aquilabuddy.application.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.IssueGuideGenerationLimitPort;
import com.aquilabuddy.application.port.out.analysis.LoadIssueGuidePort;
import com.aquilabuddy.application.port.out.analysis.SaveIssueGuidePort;
import com.aquilabuddy.application.port.out.issue.FindRecommendedIssuePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.IssuePseudoCode;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 이슈 가이드 application service가 저장된 guide를 목적별 projection으로 변환하는지 검증합니다.
 */
class IssueGuideServiceTest {

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

    private static IssueGuideService serviceWith(LoadIssueGuidePort loadIssueGuidePort) {
        return new IssueGuideService(
            loadIssueGuidePort,
            new RecordingGenerateIssueGuidePort(issueGuide("generated")),
            new RecordingSaveIssueGuidePort(),
            new RecordingIssueGuideGenerationLimitPort(),
            new StubFindRecommendedIssuePort()
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
        private String succeededIssueId;
        private String failedIssueId;
        private String failureReason;

        @Override
        public void acquireGenerationSlot(String issueId) {
            checkedIssueId = issueId;
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
}
