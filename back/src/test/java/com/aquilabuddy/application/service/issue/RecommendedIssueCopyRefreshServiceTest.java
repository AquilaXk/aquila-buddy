package com.aquilabuddy.application.service.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabuddy.application.port.out.issue.LoadCollectedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SaveRecommendedIssuesPort;
import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyRefreshResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendedIssueCopyRefreshServiceTest {

    @Test
    void dryRunReportsLegacyCopyCandidatesWithoutSaving() {
        RecommendedIssue legacy = legacyIssue();
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecordingSummarizeRecommendedIssuePort summarizePort = new RecordingSummarizeRecommendedIssuePort(List.of());
        RecommendedIssueCopyRefreshService service = new RecommendedIssueCopyRefreshService(
            () -> List.of(legacy, currentIssue()),
            savePort,
            summarizePort
        );

        RecommendedIssueCopyRefreshResult result = service.refreshRecommendedIssueCopy(10, true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.skippedCurrent()).isEqualTo(1);
        assertThat(result.samples())
            .extracting(sample -> sample.issueId())
            .containsExactly("legacy");
        assertThat(result.samples().getFirst().reason())
            .contains("copy_output_version");
        assertThat(savePort.savedIssues()).isEmpty();
        assertThat(summarizePort.receivedIssues()).isEmpty();
    }

    @Test
    void refreshesLegacyCopyAndSavesOnlyPassingSummaries() {
        RecommendedIssue legacy = legacyIssue();
        RecommendedIssue refreshed = refreshedIssue();
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecordingSummarizeRecommendedIssuePort summarizePort = new RecordingSummarizeRecommendedIssuePort(List.of(refreshed));
        RecommendedIssueCopyRefreshService service = new RecommendedIssueCopyRefreshService(
            () -> List.of(legacy),
            savePort,
            summarizePort
        );

        RecommendedIssueCopyRefreshResult result = service.refreshRecommendedIssueCopy(10, false);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(summarizePort.receivedIssues()).containsExactly(legacy);
        assertThat(savePort.savedIssues()).containsExactly(refreshed);
        assertThat(savePort.savedIssues().getFirst().copyOutputVersion())
            .isEqualTo(RecommendedIssue.CURRENT_COPY_OUTPUT_VERSION);
    }

    @Test
    void reportsFailureWhenRefreshedCopyStillFailsQualityGate() {
        RecommendedIssue legacy = legacyIssue();
        RecordingSaveRecommendedIssuesPort savePort = new RecordingSaveRecommendedIssuesPort();
        RecordingSummarizeRecommendedIssuePort summarizePort = new RecordingSummarizeRecommendedIssuePort(List.of(legacy));
        RecommendedIssueCopyRefreshService service = new RecommendedIssueCopyRefreshService(
            () -> List.of(legacy),
            savePort,
            summarizePort
        );

        RecommendedIssueCopyRefreshResult result = service.refreshRecommendedIssueCopy(10, false);

        assertThat(result.updated()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failureReasons())
            .containsExactly("legacy: refreshed copy still fails quality gate");
        assertThat(savePort.savedIssues()).isEmpty();
    }

    private static RecommendedIssue legacyIssue() {
        return new RecommendedIssue(
            "legacy",
            "suzumiyaaoba/thymeleaf-snapshot",
            6,
            "Template/inlineTemplate validation uses isEmpty() instead of isBlank()",
            "Problem Snapshot validates that a whitespace-only template passes isEmpty() and fails later.",
            0.9,
            List.of("bug", "good first issue"),
            "OPEN",
            "GitHub Search에서 Good First Issue 조건으로 수집된 후보입니다.",
            "legacy"
        );
    }

    private static RecommendedIssue refreshedIssue() {
        return new RecommendedIssue(
            "legacy",
            "suzumiyaaoba/thymeleaf-snapshot",
            6,
            "Template/inlineTemplate validation uses isEmpty() instead of isBlank()",
            "공백만 들어온 template 값이 isEmpty() 검증을 통과해 Thymeleaf 처리 중 뒤늦게 실패하는 문제입니다.",
            0.9,
            List.of("bug", "good first issue"),
            "OPEN",
            "bug 라벨과 good first issue 범위가 맞아 검증 조건을 작게 재현하고 수정하기 좋습니다."
        );
    }

    private static RecommendedIssue currentIssue() {
        return new RecommendedIssue(
            "current",
            "spring-projects/spring-boot",
            101,
            "Refine validation error example",
            "검증 실패 응답과 예시 코드가 실제 동작을 잘 설명하도록 validation 흐름을 보완하는 이슈입니다.",
            0.8,
            List.of("good first issue"),
            "OPEN",
            "Java/Spring 검증 흐름을 작은 테스트로 확인할 수 있어 첫 기여에 적합합니다."
        );
    }

    private static final class RecordingSaveRecommendedIssuesPort implements SaveRecommendedIssuesPort {

        private List<RecommendedIssue> savedIssues = List.of();

        @Override
        public void saveRecommendedIssues(List<RecommendedIssue> issues) {
            savedIssues = List.copyOf(issues);
        }

        List<RecommendedIssue> savedIssues() {
            return savedIssues;
        }
    }

    private static final class RecordingSummarizeRecommendedIssuePort implements SummarizeRecommendedIssuePort {

        private final List<RecommendedIssue> summarizedIssues;
        private List<RecommendedIssue> receivedIssues = List.of();

        private RecordingSummarizeRecommendedIssuePort(List<RecommendedIssue> summarizedIssues) {
            this.summarizedIssues = summarizedIssues;
        }

        @Override
        public List<RecommendedIssue> summarizeRecommendedIssues(List<RecommendedIssue> issues) {
            receivedIssues = List.copyOf(issues);
            return summarizedIssues;
        }

        List<RecommendedIssue> receivedIssues() {
            return receivedIssues;
        }
    }
}
