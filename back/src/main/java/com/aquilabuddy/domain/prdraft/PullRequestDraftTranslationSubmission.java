package com.aquilabuddy.domain.prdraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 저장된 PR 초안 번역 결과를 실제 제출 화면에서 바로 사용할 수 있는 복사 항목 묶음으로 변환합니다.
 */
public record PullRequestDraftTranslationSubmission(
    String draftId,
    String translationId,
    String targetLanguage,
    String upstreamRepo,
    long githubIssueNumber,
    String sourceIssueId,
    String baseBranch,
    String headBranch,
    List<PullRequestDraftSubmissionItem> items
) {

    private static final String TEST_PLAN_FALLBACK = "PR body에 test plan을 추가해 주세요.";

    public PullRequestDraftTranslationSubmission {
        items = List.copyOf(items);
    }

    public static PullRequestDraftTranslationSubmission from(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation
    ) {
        if (!draft.id().equals(translation.draftId())) {
            throw new IllegalArgumentException("translation must belong to draft.");
        }

        List<PullRequestDraftSubmissionItem> items = List.of(
            new PullRequestDraftSubmissionItem(
                "title",
                "Title",
                "GitHub PR title 입력란에 복사합니다.",
                translation.title()
            ),
            new PullRequestDraftSubmissionItem(
                "body",
                "Body",
                "GitHub PR body 입력란에 복사합니다.",
                translation.body()
            ),
            new PullRequestDraftSubmissionItem(
                "branch",
                "Branch",
                "base/head branch 선택값을 확인합니다.",
                "base: " + draft.baseBranch() + "\nhead: " + draft.headBranch()
            ),
            new PullRequestDraftSubmissionItem(
                "test-plan",
                "Test Plan",
                "PR body 검증 섹션에 들어갈 test plan입니다.",
                extractTestPlan(translation.body())
            )
        );

        return new PullRequestDraftTranslationSubmission(
            draft.id(),
            translation.id(),
            translation.targetLanguage(),
            draft.upstreamRepo(),
            draft.githubIssueNumber(),
            draft.sourceIssueId(),
            draft.baseBranch(),
            draft.headBranch(),
            items
        );
    }

    private static String extractTestPlan(String body) {
        List<String> lines = body.lines().toList();
        List<String> testPlanLines = new ArrayList<>();
        boolean collecting = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (collecting && trimmed.startsWith("## ")) {
                break;
            }

            if (collecting && !trimmed.isBlank()) {
                testPlanLines.add(trimmed);
                continue;
            }

            if (trimmed.equalsIgnoreCase("## Test Plan")) {
                collecting = true;
            }
        }

        if (testPlanLines.isEmpty()) {
            return TEST_PLAN_FALLBACK;
        }

        return String.join("\n", testPlanLines);
    }
}
