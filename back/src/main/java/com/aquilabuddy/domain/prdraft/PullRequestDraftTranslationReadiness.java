package com.aquilabuddy.domain.prdraft;

import java.util.List;
import java.util.Locale;

/**
 * 저장된 PR 초안 번역 결과를 upstream 제출 전에 점검할 수 있도록 준비 상태로 변환합니다.
 */
public record PullRequestDraftTranslationReadiness(
    String draftId,
    String translationId,
    String targetLanguage,
    String sourceIssueId,
    List<PullRequestDraftReadinessItem> items,
    int readyCount
) {

    public PullRequestDraftTranslationReadiness {
        items = List.copyOf(items);
    }

    public static PullRequestDraftTranslationReadiness from(
        StoredPullRequestDraft draft,
        PullRequestDraftTranslation translation
    ) {
        if (!draft.id().equals(translation.draftId())) {
            throw new IllegalArgumentException("translation must belong to draft.");
        }

        boolean hasSupportedLanguage = "en".equals(translation.targetLanguage());
        boolean hasBranchFlow = !draft.baseBranch().equals(draft.headBranch());
        boolean hasSubmitTitle = translation.title().length() <= 120;
        boolean hasStructuredBody = translation.body().contains("## ") && translation.body().lines().count() >= 3;
        boolean hasTestPlan = containsAny(translation.body(), "test plan", "tests");

        List<PullRequestDraftReadinessItem> items = List.of(
            new PullRequestDraftReadinessItem(
                "target-language",
                "Target Language",
                "현재 지원하는 영문 제출 대상 언어인지 확인합니다.",
                hasSupportedLanguage ? translation.targetLanguage() : "지원하지 않는 번역 대상 언어입니다.",
                statusOf(hasSupportedLanguage)
            ),
            new PullRequestDraftReadinessItem(
                "branch",
                "Repository & Branch",
                "원본 초안의 저장소, 이슈 번호, base/head branch 흐름을 확인합니다.",
                hasBranchFlow
                    ? draft.upstreamRepo() + " #" + draft.githubIssueNumber() + " / "
                        + draft.baseBranch() + " -> " + draft.headBranch()
                    : "base/head branch가 같아 제출 전 branch 흐름 확인이 필요합니다.",
                statusOf(hasBranchFlow)
            ),
            new PullRequestDraftReadinessItem(
                "title",
                "Title",
                "영문 PR 제목이 제출하기에 충분히 짧고 명확한지 확인합니다.",
                hasSubmitTitle ? translation.title() : "제목이 120자를 넘습니다. 핵심 변경만 남겨 주세요.",
                statusOf(hasSubmitTitle)
            ),
            new PullRequestDraftReadinessItem(
                "body",
                "Body Sections",
                "영문 본문이 section 단위로 나뉘어 리뷰어가 빠르게 읽을 수 있는지 확인합니다.",
                hasStructuredBody ? "영문 본문 section이 준비되어 있습니다." : "영문 본문에 section heading을 추가해 주세요.",
                statusOf(hasStructuredBody)
            ),
            new PullRequestDraftReadinessItem(
                "test-plan",
                "Test Plan",
                "영문 검증 방법이나 테스트 계획이 본문에 포함되어 있는지 확인합니다.",
                hasTestPlan ? "영문 test plan이 포함되어 있습니다." : "영문 Test Plan 항목을 추가해 주세요.",
                statusOf(hasTestPlan)
            )
        );

        return new PullRequestDraftTranslationReadiness(
            draft.id(),
            translation.id(),
            translation.targetLanguage(),
            draft.sourceIssueId(),
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
