package com.aquilabuddy.domain.prdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestDraftReadinessTest {

    private static final String DEFAULT_BODY = """
        close #2

        ## 변경 사항
        - validation 예시를 보강합니다.

        ## 작업 메모
        - diff 기반으로 초안을 구성했습니다.

        ## 테스트 계획
        - 관련 테스트를 실행해 주세요.

        ## 브레이킹 체인지
        - 하위 호환성을 유지하며 변경 사항이 없습니다.
        """;

    private static StoredPullRequestDraft createDraft(
        String id,
        int issueNumber,
        String baseBranch,
        String headBranch,
        String title,
        String body,
        String diff
    ) {
        return new StoredPullRequestDraft(
            id,
            "spring-projects/spring-boot",
            issueNumber,
            baseBranch,
            headBranch,
            title,
            body,
            "issue-" + issueNumber,
            "Issue #" + issueNumber,
            diff,
            Instant.parse("2026-05-15T00:00:00Z")
        );
    }

    @Test
    void createsReadyChecklistFromCompleteDraft() {
        StoredPullRequestDraft draft = createDraft(
            "draft-1",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 변경 초안",
            DEFAULT_BODY,
            "diff --git a/docs.adoc b/docs.adoc\n+Add validation example"
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
            .extracting(PullRequestDraftReadinessItem::label)
            .containsExactly(
                "브랜치 흐름",
                "이슈 연결 구문",
                "템플릿 구조 준수",
                "테스트 계획",
                "클린 Diff 검증",
                "브레이킹 체인지 확인",
                "제목 규격"
            );
        assertThat(readiness.items().get(0).detail())
            .isEqualTo("spring-projects/spring-boot #2 / main → feat/validation-docs");
        assertThat(readiness.items())
            .extracting(PullRequestDraftReadinessItem::status)
            .containsOnly(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void recognizesEnglishTestsHeadingAsTestPlan() {
        StoredPullRequestDraft draft = createDraft(
            "draft-english-tests",
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
            "+Add test"
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
        StoredPullRequestDraft draft = createDraft(
            "draft-conflict",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            "close #2\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "<<<<<<< HEAD\nlocal\n=======\nremote\n>>>>>>>"
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(4).key()).isEqualTo("clean-diff");
        assertThat(readiness.items().get(4).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(readiness.items().get(4).detail()).contains("충돌 마커");
    }

    @Test
    void rejectsPrefixedIssueNumberMatches() {
        // 이슈 #4인 초안에 "close #42"만 적혀 있는 경우, substring 매칭 버그로 통과되지 않고 ACTION_REQUIRED 처리되어야 합니다.
        StoredPullRequestDraft draft = createDraft(
            "draft-prefix",
            4,
            "main",
            "feat/validation-docs",
            "docs: update documentation",
            "close #42\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "+valid diff content"
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(1).key()).isEqualTo("issue-link");
        assertThat(readiness.items().get(1).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
    }

    @Test
    void acceptsIssueLinksWithBulletsAndColons() {
        // "- Fixes: #4" 형태의 마크다운 불릿 및 콜론 문법도 정확하게 인식해야 합니다.
        StoredPullRequestDraft draft = createDraft(
            "draft-bullet-colon",
            4,
            "main",
            "feat/validation-docs",
            "docs: update documentation",
            "- Fixes: #4\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "+valid diff content"
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(1).key()).isEqualTo("issue-link");
        assertThat(readiness.items().get(1).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void detectsConflictMarkersWithUnifiedDiffPrefix() {
        // diff 파일 내 추가 라인(+<<<<<<< HEAD)으로 들어간 충돌 마커를 감지해야 합니다.
        StoredPullRequestDraft draft = createDraft(
            "draft-unified-diff-conflict",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            "close #2\n## Summary\n- Update\n## Tests\n- Check\n## Breaking Changes\n- None",
            "diff --git a/App.java b/App.java\n+<<<<<<< HEAD\n+local\n+=======\n+remote\n+>>>>>>> branch"
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(4).key()).isEqualTo("clean-diff");
        assertThat(readiness.items().get(4).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(readiness.items().get(4).detail()).contains("충돌 마커");
    }

    @Test
    void supportsVariousMarkdownHeadingLevelsInStructuredBody() {
        // # 및 ### 등 다양한 헤딩 레벨로 구성된 본문도 템플릿 준수 게이트를 통과해야 합니다.
        StoredPullRequestDraft draft = createDraft(
            "draft-headings",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            "close #2\n# Overview\n- Detail line 1\n### Verification\n- Tests run",
            "+valid diff"
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(2).key()).isEqualTo("template-compliance");
        assertThat(readiness.items().get(2).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void recognizesConventionalCommitBreakingChangeTitle() {
        // Conventional Commits의 feat!: 또는 feat(scope)!: 브레이킹 체인지 표기를 인식해야 합니다.
        StoredPullRequestDraft draft = createDraft(
            "draft-breaking-title",
            2,
            "main",
            "feat/break-api",
            "feat(api)!: break public signature for spring-projects/spring-boot#2",
            "close #2\n## Summary\n- Update\n## Tests\n- Check",
            "+valid diff"
        );

        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);

        assertThat(readiness.items().get(5).key()).isEqualTo("breaking-change");
        assertThat(readiness.items().get(5).status()).isEqualTo(PullRequestDraftReadinessStatus.READY);
    }

    @Test
    void marksCleanDiffActionRequiredWhenDiffLookupFailedOrLacksDiffLines() {
        StoredPullRequestDraft fallbackDraft = createDraft(
            "draft-fallback-diff",
            2,
            "main",
            "missing-branch",
            "docs: spring-projects/spring-boot#2 update",
            DEFAULT_BODY,
            "Diff를 불러오지 못했습니다. 저장소: spring-projects/spring-boot, 브랜치: main -> missing-branch"
        );

        PullRequestDraftReadiness fallbackReadiness = PullRequestDraftReadiness.from(fallbackDraft);

        assertThat(fallbackReadiness.items().get(4).key()).isEqualTo("clean-diff");
        assertThat(fallbackReadiness.items().get(4).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(fallbackReadiness.items().get(4).detail()).isEqualTo("compare 대상 브랜치 diff를 불러오지 못했습니다.");

        StoredPullRequestDraft nonDiffDraft = createDraft(
            "draft-non-diff",
            2,
            "main",
            "feat/validation-docs",
            "docs: spring-projects/spring-boot#2 update",
            DEFAULT_BODY,
            "not a unified diff payload"
        );

        PullRequestDraftReadiness nonDiffReadiness = PullRequestDraftReadiness.from(nonDiffDraft);

        assertThat(nonDiffReadiness.items().get(4).key()).isEqualTo("clean-diff");
        assertThat(nonDiffReadiness.items().get(4).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(nonDiffReadiness.items().get(4).detail()).isEqualTo("유효한 diff 변경 사항을 확인해 주세요.");
    }

    @Test
    void stripsHtmlCommentsAndFencedBlocksBeforeTemplateAndIssueLinkChecks() {
        StoredPullRequestDraft hiddenOnlyDraft = createDraft(
            "draft-hidden-only",
            42,
            "main",
            "fix/issue-42",
            "fix: refine parser",
            """
            <!--
            Fixes #42
            ## Breaking Changes
            테스트 계획:
            -->
            ```markdown
            Fixes #42
            ```
            일반 문단 한 줄만 작성했습니다.
            """,
            "@@ -1,2 +1,3 @@\n+line"
        );

        PullRequestDraftReadiness hiddenReadiness = PullRequestDraftReadiness.from(hiddenOnlyDraft);
        assertThat(hiddenReadiness.items())
            .filteredOn(item -> item.key().equals("issue-link")
                || item.key().equals("template-compliance")
                || item.key().equals("test-plan")
                || item.key().equals("breaking-change"))
            .allSatisfy(item -> assertThat(item.status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED));

        StoredPullRequestDraft validWithCodeFencePlaceholder = createDraft(
            "draft-fenced-example",
            42,
            "main",
            "fix/issue-42",
            "fix: handle edge case",
            """
            ## Summary
            Closes #42
            ```java
            // TODO: example inside fenced code block
            String placeholder = "[TBD]";
            ```
            ## Test Plan
            - 단위 테스트 실행 완료
            ## Breaking Changes
            - 없음 (하위 호환 유지)
            """,
            "diff --git a/App.java b/App.java\n@@ -1 +1 @@\n-a\n+b"
        );

        PullRequestDraftReadiness validReadiness = PullRequestDraftReadiness.from(validWithCodeFencePlaceholder);
        assertThat(validReadiness.readyCount()).isEqualTo(7);

        StoredPullRequestDraft visiblePlaceholderDraft = createDraft(
            "draft-visible-tbd",
            42,
            "main",
            "fix/issue-42",
            "fix: keep placeholder",
            """
            ## Summary
            Closes #42
            [TBD] 상세 설명을 작성합니다.
            ## Test Plan
            - 단위 테스트 실행
            ## Breaking Changes
            - 하위 호환 영향 없음
            """,
            "@@ -1 +1 @@\n+ok"
        );

        PullRequestDraftReadiness placeholderReadiness = PullRequestDraftReadiness.from(visiblePlaceholderDraft);
        assertThat(placeholderReadiness.items().get(2).key()).isEqualTo("template-compliance");
        assertThat(placeholderReadiness.items().get(2).status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
        assertThat(placeholderReadiness.items().get(2).detail()).contains("placeholder");
    }

    @Test
    void marksCleanDiffActionRequiredWhenSecretPatternAppearsInDiff() {
        List<String> constructedSecrets = List.of(
            "AKIA" + "IOSFODNN7EXAMPLE",
            "ghp_" + "0123456789abcdef0123",
            "gho_" + "abcdef0123456789abcd",
            "github_pat_" + "11AAAAAA0123456789abcdef",
            "sk-" + "test1234567890abcdef1234",
            "-----BEGIN RSA " + "PRIVATE KEY-----"
        );

        for (String dummySecret : constructedSecrets) {
            StoredPullRequestDraft draft = createDraft(
                "draft-secret",
                99,
                "main",
                "fix/leak",
                "fix: config cleanup",
                """
                ## Summary
                Closes #99
                ## Test Plan
                - 검증 완료
                ## Breaking Changes
                - 없음
                """,
                "diff --git a/config.txt b/config.txt\n@@ -1 +1 @@\n+TOKEN=" + dummySecret
            );

            PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);
            PullRequestDraftReadinessItem cleanDiff = readiness.items().get(4);
            assertThat(cleanDiff.key()).isEqualTo("clean-diff");
            assertThat(cleanDiff.status()).isEqualTo(PullRequestDraftReadinessStatus.ACTION_REQUIRED);
            assertThat(cleanDiff.detail()).contains("인증 키");
        }
    }
}
