package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PullRequestDraftReadinessTest {

    @Test
    void createsReadyChecklistFromCompleteDraft() {
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

                ## 테스트 계획
                - 관련 테스트를 실행해 주세요.

                ## 브레이킹 체인지
                - 하위 호환성을 유지하며 변경 사항이 없습니다.
                """,
            "issue-2",
            "Issue #2",
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.draftId()).isEqualTo("draft-1");
        assertThat(readiness.sourceIssueId()).isEqualTo("issue-2");
        assertThat(readiness.readyCount()).isEqualTo(7);
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::key)
            .containsExactly(
                "branch-flow",
                "issue-link",
                "template-compliance",
                "test-plan",
                "clean-diff",
                "breaking-change",
                "title"
            );
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::status)
            .containsOnly(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void recognizesEnglishTestsHeadingAsTestPlan() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-english-tests",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            """
                close #2

                ## Summary
                - Improve validation docs.

                ## Tests
                - ./gradlew check

                ## Breaking Changes
                - None
                """,
            "issue-2",
            "Issue #2",
            "+Add test",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.readyCount()).isEqualTo(7);
        assertThat(readiness.items().get(3).key()).isEqualTo("test-plan");
        assertThat(readiness.items().get(3).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void marksChecklistItemsActionRequiredWhenDraftNeedsReview() {
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

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.readyCount()).isZero();
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::status)
            .containsOnly(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(readiness.items().get(0).detail()).contains("base/head branch");
        assertThat(readiness.items().get(1).detail()).contains("close #2");
        assertThat(readiness.items().get(2).detail()).contains("section");
        assertThat(readiness.items().get(3).detail()).contains("테스트");
        assertThat(readiness.items().get(4).detail()).contains("diff");
        assertThat(readiness.items().get(5).detail()).contains("브레이킹");
        assertThat(readiness.items().get(6).detail()).contains("120자");
    }

    @Test
    void detectsGitConflictMarkersInDiff() {
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-conflict",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            "close #2\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "issue-2",
            "Issue #2",
            "<<<<<<< HEAD\nlocal\n=======\nremote\n>>>>>>>",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(4).key()).isEqualTo("clean-diff");
        assertThat(readiness.items().get(4).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(readiness.items().get(4).detail()).contains("충돌 마커");
    }

    @Test
    void rejectsPrefixedIssueNumberMatches() {
        // 이슈 #4인 초안에 "close #42"만 적혀 있는 경우, substring 매칭 버그로 통과되지 않고 ACTION_REQUIRED 처리되어야 합니다.
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-prefix",
            "spring-projects/spring-boot",
            4,
            "main",
            "feat/validation-docs",
            "docs: update documentation",
            "close #42\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "issue-4",
            "Issue #4",
            "+valid diff content",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(1).key()).isEqualTo("issue-link");
        assertThat(readiness.items().get(1).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
    }

    @Test
    void acceptsIssueLinksWithBulletsAndColons() {
        // "- Fixes: #4" 형태의 마크다운 불릿 및 콜론 문법도 정확하게 인식해야 합니다.
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-bullet-colon",
            "spring-projects/spring-boot",
            4,
            "main",
            "feat/validation-docs",
            "docs: update documentation",
            "- Fixes: #4\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "issue-4",
            "Issue #4",
            "+valid diff content",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(1).key()).isEqualTo("issue-link");
        assertThat(readiness.items().get(1).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void detectsConflictMarkersWithUnifiedDiffPrefix() {
        // diff 파일 내 추가 라인(+<<<<<<< HEAD)으로 들어간 충돌 마커를 감지해야 합니다.
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-unified-diff-conflict",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            "close #2\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "issue-2",
            "Issue #2",
            "diff --git a/App.java b/App.java\n+<<<<<<< HEAD\n+local\n+=======\n+remote\n+>>>>>>> branch",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(4).key()).isEqualTo("clean-diff");
        assertThat(readiness.items().get(4).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(readiness.items().get(4).detail()).contains("충돌 마커");
    }

    @Test
    void supportsVariousMarkdownHeadingLevelsInStructuredBody() {
        // # 및 ### 등 다양한 헤딩 레벨로 구성된 본문도 템플릿 준수 게이트를 통과해야 합니다.
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-headings",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            "close #2\n# Overview\n- Detail line 1\n### Verification\n- Tests run",
            "issue-2",
            "Issue #2",
            "+valid diff",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(2).key()).isEqualTo("template-compliance");
        assertThat(readiness.items().get(2).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void recognizesConventionalCommitBreakingChangeTitle() {
        // Conventional Commits의 feat!: 또는 feat(scope)!: 브레이킹 체인지 표기를 인식해야 합니다.
        StoredPullRequestDraft draft = new StoredPullRequestDraft(
            "draft-breaking-title",
            "spring-projects/spring-boot",
            2,
            "main",
            "feat/break-api",
            "feat(api)!: break public signature for spring-projects/spring-boot#2",
            "close #2\n## Summary\n- Update\n## Tests\n- Check",
            "issue-2",
            "Issue #2",
            "+valid diff",
            Instant.parse("2026-05-15T00:00:00Z")
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(5).key()).isEqualTo("breaking-change");
        assertThat(readiness.items().get(5).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }
}
