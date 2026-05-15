package com.aquilabuddy.domain.prdraft;

import java.util.List;
import java.util.Locale;

/**
 * 저장된 PR 초안을 upstream 제출 전에 점검할 수 있도록 준비 상태로 변환합니다.
 */
public record PullRequestDraftReadiness(
    String draftId,
    List<PullRequestDraftReadinessItem> items,
    int readyCount
) {

    public PullRequestDraftReadiness {
        items = List.copyOf(items);
    }

    public static PullRequestDraftReadiness from(StoredPullRequestDraft draft) {
        boolean hasBranchFlow = !draft.baseBranch().equals(draft.headBranch());
        boolean hasSubmitTitle = draft.title().length() <= 120;
        boolean hasStructuredBody = draft.body().contains("## ") && draft.body().lines().count() >= 3;
        boolean hasTestPlan = containsAny(draft.body(), "테스트", "test plan");

        List<PullRequestDraftReadinessItem> items = List.of(
            new PullRequestDraftReadinessItem(
                "metadata",
                "Repository & Branch",
                "대상 저장소, 이슈 번호, base/head branch 흐름을 확인합니다.",
                hasBranchFlow
                    ? draft.upstreamRepo() + " #" + draft.githubIssueNumber() + " / "
                        + draft.baseBranch() + " -> " + draft.headBranch()
                    : "base/head branch가 같아 제출 전 branch 흐름 확인이 필요합니다.",
                statusOf(hasBranchFlow)
            ),
            new PullRequestDraftReadinessItem(
                "title",
                "Title",
                "PR 제목이 제출하기에 충분히 짧고 명확한지 확인합니다.",
                hasSubmitTitle ? draft.title() : "제목이 120자를 넘습니다. 핵심 변경만 남겨 주세요.",
                statusOf(hasSubmitTitle)
            ),
            new PullRequestDraftReadinessItem(
                "body",
                "Body Sections",
                "본문이 section 단위로 나뉘어 리뷰어가 빠르게 읽을 수 있는지 확인합니다.",
                hasStructuredBody ? "본문 section이 준비되어 있습니다." : "본문에 section heading을 추가해 주세요.",
                statusOf(hasStructuredBody)
            ),
            new PullRequestDraftReadinessItem(
                "test-plan",
                "Test Plan",
                "검증 방법이나 테스트 계획이 PR 본문에 포함되어 있는지 확인합니다.",
                hasTestPlan ? "테스트 계획이 포함되어 있습니다." : "테스트 또는 Test Plan 항목을 추가해 주세요.",
                statusOf(hasTestPlan)
            )
        );

        return new PullRequestDraftReadiness(
            draft.id(),
            items,
            (int) items.stream()
                .filter(item -> item.status() == PullRequestDraftReadinessStatus.READY)
                .count()
        );
    }

    private static PullRequestDraftReadinessStatus statusOf(boolean ready) {
        return ready ? PullRequestDraftReadinessStatus.READY : PullRequestDraftReadinessStatus.ACTION_REQUIRED;
    }

    private static boolean containsAny(String body, String firstNeedle, String secondNeedle) {
        String normalized = body.toLowerCase(Locale.ROOT);

        return normalized.contains(firstNeedle.toLowerCase(Locale.ROOT))
            || normalized.contains(secondNeedle.toLowerCase(Locale.ROOT));
    }
}
