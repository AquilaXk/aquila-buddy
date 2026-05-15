package com.aquilabuddy.domain.analysis;

import java.util.List;

/**
 * 이슈 가이드를 PR 초안 작성까지 이어지는 실행 순서로 표현하는 도메인 모델입니다.
 */
public record IssueGuideWorkflow(
    String issueKey,
    List<IssueGuideWorkflowStep> steps,
    int readyCount
) {

    public IssueGuideWorkflow {
        steps = List.copyOf(steps);
    }

    public static IssueGuideWorkflow from(IssueGuide guide) {
        boolean hasGuideline = hasText(guide.guideline());
        boolean hasPseudoCode = hasText(guide.pseudoCode());
        boolean hasSideEffects = guide.sideEffects() != null && !guide.sideEffects().isEmpty();
        boolean canDraft = hasGuideline && hasPseudoCode && hasSideEffects;

        List<IssueGuideWorkflowStep> steps = List.of(
            new IssueGuideWorkflowStep(
                "guide-review",
                "Guide Review",
                "작업 목표와 수정 범위를 확인합니다.",
                detailOrFallback(guide.guideline(), "가이드가 아직 준비되지 않았습니다."),
                statusOf(hasGuideline)
            ),
            new IssueGuideWorkflowStep(
                "implementation-plan",
                "Implementation Plan",
                "pseudo code를 실제 수정 계획으로 옮깁니다.",
                detailOrFallback(guide.pseudoCode(), "구현 계획이 아직 준비되지 않았습니다."),
                statusOf(hasPseudoCode)
            ),
            new IssueGuideWorkflowStep(
                "side-effect-check",
                "Side Effect Check",
                "수정 후 확인해야 할 영향 범위를 점검합니다.",
                hasSideEffects ? String.join("\n", guide.sideEffects()) : "영향 범위가 아직 정리되지 않았습니다.",
                statusOf(hasSideEffects)
            ),
            new IssueGuideWorkflowStep(
                "pr-draft",
                "PR Draft",
                "정리된 가이드를 PR 초안 입력값으로 연결합니다.",
                canDraft ? "가이드 확인 후 PR 초안을 생성할 수 있습니다." : "가이드 정보가 모두 준비되면 PR 초안을 생성합니다.",
                statusOf(canDraft)
            )
        );

        return new IssueGuideWorkflow(
            guide.issueKey(),
            steps,
            (int) steps.stream()
                .filter(step -> step.status() == IssueGuideWorkflowStepStatus.READY)
                .count()
        );
    }

    private static IssueGuideWorkflowStepStatus statusOf(boolean ready) {
        return ready ? IssueGuideWorkflowStepStatus.READY : IssueGuideWorkflowStepStatus.WAITING;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String detailOrFallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
