package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PullRequestDraftTimelineTest {

    @Test
    void createsTimelineFromReadyDraft() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-1",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 변경 초안",
            """
                close #2

                ## 변경 사항
                - validation 예시를 보강합니다.

                ## 작업 메모
                - diff 기반으로 초안을 구성했습니다.

                ## 테스트
                - ./back/gradlew -p back check

                ## 브레이킹 체인지
                - 하위 호환성을 유지합니다.
                """,
            "issue-2",
            "Issue #2",
            "+Add test",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftTimeline timeline = PullRequestDraftTimeline.from(draft);

        assertThat(timeline.draftId()).isEqualTo("draft-1");
        assertThat(timeline.sourceIssueId()).isEqualTo("issue-2");
        assertThat(timeline.items())
            .extracting(PullRequestDraftTimelineItem::key)
            .containsExactly("context-captured", "draft-created", "readiness-reviewed", "submission-prepared");
        assertThat(timeline.items())
            .extracting(PullRequestDraftTimelineItem::status)
            .containsExactly(
                PullRequestDraftTimelineStatus.DONE,
                PullRequestDraftTimelineStatus.DONE,
                PullRequestDraftTimelineStatus.DONE,
                PullRequestDraftTimelineStatus.READY
            );
        assertThat(timeline.items().get(0).detail()).contains("spring-projects/spring-boot #2");
        assertThat(timeline.items().get(2).detail()).isEqualTo("7/7 readiness items ready");
        assertThat(timeline.items().get(3).occurredAt()).isEqualTo(Instant.parse("2026-05-15T00:00:00Z"));
    }

    @Test
    void marksReadinessAndSubmissionAttentionWhenDraftNeedsReview() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-2",
            "spring-projects/spring-boot",
            2,
            "main",
            "main",
            "x".repeat(121),
            "본문만 있는 초안입니다.",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftTimeline timeline = PullRequestDraftTimeline.from(draft);

        assertThat(timeline.items().get(2).status()).isEqualTo(PullRequestDraftTimelineStatus.NEEDS_ATTENTION);
        assertThat(timeline.items().get(2).detail()).isEqualTo("0/7 readiness items ready");
        assertThat(timeline.items().get(3).status()).isEqualTo(PullRequestDraftTimelineStatus.NEEDS_ATTENTION);
        assertThat(timeline.items().get(3).detail()).contains("readiness 항목을 먼저 확인해 주세요.");
    }
}
