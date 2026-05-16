package com.aquilabuddy.domain.prdraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 저장된 PR 초안을 실제 제출 화면에서 바로 사용할 수 있는 복사 항목 묶음으로 변환합니다.
 */
public record PullRequestDraftSubmission(
    String draftId,
    String upstreamRepo,
    long githubIssueNumber,
    String baseBranch,
    String headBranch,
    List<PullRequestDraftSubmissionItem> items
) {

    private static final String TEST_PLAN_FALLBACK = "PR 본문에 테스트 계획을 추가해 주세요.";

    public PullRequestDraftSubmission {
        items = List.copyOf(items);
    }

    public static PullRequestDraftSubmission from(StoredPullRequestDraft draft) {
        List<PullRequestDraftSubmissionItem> items = List.of(
            new PullRequestDraftSubmissionItem(
                "title",
                "Title",
                "GitHub PR title 입력란에 복사합니다.",
                draft.title()
            ),
            new PullRequestDraftSubmissionItem(
                "body",
                "Body",
                "GitHub PR body 입력란에 복사합니다.",
                draft.body()
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
                "PR 본문 검증 섹션에 들어갈 테스트 계획입니다.",
                extractTestPlan(draft.body())
            )
        );

        return new PullRequestDraftSubmission(
            draft.id(),
            draft.upstreamRepo(),
            draft.githubIssueNumber(),
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

            if (isTestPlanHeading(trimmed)) {
                collecting = true;
            }
        }

        if (testPlanLines.isEmpty()) {
            return TEST_PLAN_FALLBACK;
        }

        return String.join("\n", testPlanLines);
    }

    private static boolean isTestPlanHeading(String line) {
        String normalized = line.toLowerCase();

        return normalized.equals("## 테스트") || normalized.equals("## test plan");
    }
}
