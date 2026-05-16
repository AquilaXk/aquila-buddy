package com.aquilabuddy.domain.prdraft;

import java.util.List;

/**
 * 저장된 PR 초안 번역 결과를 제출 준비 흐름으로 이해할 수 있도록 timeline 단계로 변환합니다.
 */
public record PullRequestDraftTranslationTimeline(
    String draftId,
    String translationId,
    String targetLanguage,
    List<PullRequestDraftTimelineItem> items
) {

    public PullRequestDraftTranslationTimeline {
        items = List.copyOf(items);
    }

    public static PullRequestDraftTranslationTimeline from(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation
    ) {
        PullRequestDraftTranslationReadiness readiness = PullRequestDraftTranslationReadiness.from(draft, translation);
        int totalReadinessCount = readiness.items().size();
        boolean readyForSubmission = readiness.readyCount() == totalReadinessCount;

        List<PullRequestDraftTimelineItem> items = List.of(
            new PullRequestDraftTimelineItem(
                "translation-created",
                "Translation Created",
                "저장된 PR 초안을 영문 제출용 초안으로 변환했습니다.",
                translation.targetLanguage() + " / " + translation.title(),
                PullRequestDraftTimelineStatus.DONE,
                translation.createdAt()
            ),
            new PullRequestDraftTimelineItem(
                "translation-ready",
                "Translation Ready",
                "영문 제목과 본문을 제출 화면에 옮길 수 있는 형태로 준비했습니다.",
                draft.upstreamRepo() + " #" + draft.githubIssueNumber() + " / "
                    + draft.baseBranch() + " -> " + draft.headBranch(),
                PullRequestDraftTimelineStatus.DONE,
                translation.createdAt()
            ),
            new PullRequestDraftTimelineItem(
                "readiness-reviewed",
                "Readiness Reviewed",
                "영문 제출 전 target language, branch, title, body, test plan 준비 상태를 확인했습니다.",
                readiness.readyCount() + "/" + totalReadinessCount + " translation readiness items ready",
                readyForSubmission
                    ? PullRequestDraftTimelineStatus.DONE
                    : PullRequestDraftTimelineStatus.NEEDS_ATTENTION,
                translation.createdAt()
            ),
            new PullRequestDraftTimelineItem(
                "submission-prepared",
                "Submission Prepared",
                "영문 GitHub PR 화면에 옮길 제출용 bundle 준비 상태입니다.",
                readyForSubmission
                    ? "English submission bundle을 복사할 수 있습니다."
                    : "남은 translation readiness 항목을 먼저 확인해 주세요.",
                readyForSubmission
                    ? PullRequestDraftTimelineStatus.READY
                    : PullRequestDraftTimelineStatus.NEEDS_ATTENTION,
                translation.createdAt()
            )
        );

        return new PullRequestDraftTranslationTimeline(
            draft.id(),
            translation.id(),
            translation.targetLanguage(),
            items
        );
    }
}
