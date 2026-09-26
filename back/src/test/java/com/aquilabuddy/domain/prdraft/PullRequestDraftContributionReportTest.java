package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PR 초안 제출 전 report가 readiness와 번역 요약을 함께 제공하는지 검증합니다.
 */
class PullRequestDraftContributionReportTest {

    @Test
    void createsReportWithReadinessAndLatestTranslation() {
        PullRequestDraftContributionReport report = PullRequestDraftContributionReport.from(
            storedDraft(),
            List.of(translation("older", "older title", "2026-05-15T02:00:00Z"),
                translation("newer", "newer title", "2026-05-15T03:00:00Z"))
        );

        assertThat(report.draftId()).isEqualTo("draft-1");
        assertThat(report.upstreamRepo()).isEqualTo("spring-projects/spring-boot");
        assertThat(report.githubIssueNumber()).isEqualTo(123);
        assertThat(report.sourceIssueId()).isEqualTo("issue-123");
        assertThat(report.baseBranch()).isEqualTo("main");
        assertThat(report.headBranch()).isEqualTo("feat/readme-fix");
        assertThat(report.readinessReadyCount()).isEqualTo(7);
        assertThat(report.readinessTotalCount()).isEqualTo(7);
        assertThat(report.translationCount()).isEqualTo(2);
        assertThat(report.latestTranslation()).isNotNull();
        assertThat(report.latestTranslation().translationId()).isEqualTo("newer");
        assertThat(report.latestTranslation().title()).isEqualTo("newer title");
    }

    @Test
    void createsReportWithoutTranslationSummary() {
        PullRequestDraftContributionReport report = PullRequestDraftContributionReport.from(storedDraft(), List.of());

        assertThat(report.translationCount()).isZero();
        assertThat(report.latestTranslation()).isNull();
    }

    private static StoredPullRequestDraft storedDraft() {
        return new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            123,
            "main",
            "feat/readme-fix",
            "[Docs] README 기여 안내 보완",
            """
            close #123

            ## 변경 사항
            - README 안내를 보완합니다.

            ## 테스트
            - 관련 테스트를 실행합니다.

            ## 브레이킹 체인지
            - 하위 호환성을 유지합니다.
            """,
            "issue-123",
            "Issue #123",
            "+Add README docs",
            Instant.parse("2026-05-15T01:00:00Z")
        );
    }

    private static PullRequestDraftTranslation translation(String id, String title, String createdAt) {
        return new PullRequestDraftTranslation(
            id,
            "draft-1",
            "en",
            title,
            "translated body",
            Instant.parse(createdAt)
        );
    }
}
