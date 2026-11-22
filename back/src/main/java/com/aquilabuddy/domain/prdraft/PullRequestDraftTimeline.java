package com.aquilabuddy.domain.prdraft;

import java.util.List;

/**
 * 저장된 PR 초안을 제출 준비 흐름으로 이해할 수 있도록 timeline 단계로 변환합니다.
 */
public record PullRequestDraftTimeline(
    String draftId,
    String sourceIssueId,
    List<PullRequestDraftTimelineItem> items
) {

    public PullRequestDraftTimeline {
        items = List.copyOf(items);
    }

    public static PullRequestDraftTimeline from(StoredPullRequestDraft draft) {
        PullRequestDraftReadiness readiness = PullRequestDraftReadiness.from(draft);
        int totalReadinessCount = readiness.items().size();
        boolean readyForSubmission = readiness.readyCount() == totalReadinessCount;

        List<PullRequestDraftTimelineItem> items = List.of(
            new PullRequestDraftTimelineItem(
                "context-captured",
                "Context Captured",
                "저장소, 이슈, base/head branch 입력 맥락을 수집했습니다.",
                draft.upstreamRepo() + " #" + draft.githubIssueNumber() + " / "
                    + draft.baseBranch() + " -> " + draft.headBranch(),
                PullRequestDraftTimelineStatus.DONE,
                draft.createdAt()
            ),
            new PullRequestDraftTimelineItem(
                "draft-created",
                "Draft Created",
                "입력 맥락과 diff를 바탕으로 PR 제목과 본문 초안을 만들었습니다.",
                draft.title(),
                PullRequestDraftTimelineStatus.DONE,
                draft.createdAt()
            ),
            new PullRequestDraftTimelineItem(
                "readiness-reviewed",
                "Readiness Reviewed",
                "제출 전 핵심 점검 항목(브랜치 흐름, 이슈 연결 구문, 템플릿 구조 준수, 테스트 계획, 클린 Diff 검증, 브레이킹 체인지 확인, 제목 규격)을 확인했습니다.",
                readiness.readyCount() + "/" + totalReadinessCount + " readiness items ready",
                readyForSubmission
                    ? PullRequestDraftTimelineStatus.DONE
                    : PullRequestDraftTimelineStatus.NEEDS_ATTENTION,
                draft.createdAt()
            ),
            new PullRequestDraftTimelineItem(
                "submission-prepared",
                "Submission Prepared",
                "GitHub PR 화면에 옮길 제출용 bundle 준비 상태입니다.",
                readyForSubmission
                    ? "Submission bundle을 복사할 수 있습니다."
                    : "남은 readiness 항목을 먼저 확인해 주세요.",
                readyForSubmission
                    ? PullRequestDraftTimelineStatus.READY
                    : PullRequestDraftTimelineStatus.NEEDS_ATTENTION,
                draft.createdAt()
            )
        );

        return new PullRequestDraftTimeline(draft.id(), draft.sourceIssueId(), items);
    }
}
