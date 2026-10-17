package com.aquilabuddy.domain.prdraft;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 저장된 PR 초안을 upstream 제출 전에 핵심 체크리스트 항목으로 점검할 수 있도록 변환합니다.
 */
public record PullRequestDraftReadiness(
    String draftId,
    String sourceIssueId,
    List<PullRequestDraftReadinessItem> items,
    int readyCount
) {

    private static final Pattern CLOSING_PATTERN = Pattern.compile(
        "(?i)(?:^[\\s-*•]*|\\b)(?:close|closes|closed|fix|fixes|fixed|resolve|resolves|resolved):?\\s+#(\\d+)\\b"
    );
    private static final Pattern TITLE_CONVENTION_PATTERN = Pattern.compile(
        "(?i)^(\\[[a-z0-9_\\-\\./\\s]+\\]|(feat|fix|docs|style|refactor|perf|test|chore|build|ci|revert)(\\([a-z0-9_\\-\\./]+\\))?(!)?):?\\s*.+$"
    );
    private static final Pattern CONFLICT_MARKER_PATTERN = Pattern.compile(
        "(?m)^\\+?\\s*(<{7}|={7}|>{7})"
    );
    private static final Pattern HAS_DIFF_CHANGE_PATTERN = Pattern.compile(
        "(?m)^(?:diff --git\\s+|@@\\s+|[+-](?![+-]{2})\\S*)"
    );
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile(
        "(?m)^#{1,6}\\s+\\S+"
    );

    public PullRequestDraftReadiness {
        items = List.copyOf(items);
    }

    /**
     * 제출 전 핵심 준비도 항목(브랜치 흐름, 이슈 연결 구문, 템플릿 구조 준수, 테스트 계획, 클린 Diff 검증, 브레이킹 체인지 확인, 제목 규격)을 평가합니다.
     */
    public static PullRequestDraftReadiness from(StoredPullRequestDraft draft) {
        // 1. 브랜치 흐름: base/head branch가 비어있지 않고 서로 다름
        boolean hasBranchFlow = !draft.baseBranch().isBlank()
            && !draft.headBranch().isBlank()
            && !draft.baseBranch().equals(draft.headBranch());

        // 2. 이슈 연결 구문: body에 close #N, fix #N 등 연계 구문 포함
        boolean hasIssueLink = hasClosingIssueLink(draft.body(), draft.githubIssueNumber());

        // 3. 템플릿 구조 준수: 마크다운 섹션 헤딩(#, ## 등) 포함 및 3줄 이상 구조화
        boolean hasStructuredBody = MARKDOWN_HEADING_PATTERN.matcher(draft.body()).find()
            && draft.body().lines().count() >= 3;

        // 4. 테스트 계획: 테스트, 검증, test plan 관련 키워드 포함
        boolean hasTestPlan = containsAny(draft.body(), "테스트", "검증", "test plan", "tests", "testing");

        // 5. 클린 Diff 검증: 유효한 unified diff 변경 라인이 존재하고 충돌 마커 및 실패 폴백 문구가 없음
        boolean hasCleanDiff = isCleanDiff(draft.diffContent());

        // 6. 브레이킹 체인지 확인: 하위 호환성 영향 또는 브레이킹 체인지 여부 명시
        boolean hasBreakingChangeCheck = hasBreakingChangeCheck(draft.body(), draft.title());

        // 7. 제목 규격: 120자 이하 및 Conventional Commits 규격 준수
        boolean hasValidTitleLength = draft.title().length() <= 120;
        boolean hasTitleConvention = hasValidTitleLength && isConventionalTitle(draft.title());

        List<PullRequestDraftReadinessItem> items = List.of(
            new PullRequestDraftReadinessItem(
                "branch-flow",
                "브랜치 흐름",
                "대상 저장소, 이슈 번호, base/head branch 흐름을 확인합니다.",
                hasBranchFlow
                    ? draft.upstreamRepo() + " #" + draft.githubIssueNumber() + " / "
                        + draft.baseBranch() + " → " + draft.headBranch()
                    : "base/head branch가 같아 제출 전 branch 흐름 확인이 필요합니다.",
                statusOf(hasBranchFlow)
            ),
            new PullRequestDraftReadinessItem(
                "issue-link",
                "이슈 연결 구문",
                "GitHub 이슈 자동 연계 및 종료 구문(close #N, fix #N 등)을 확인합니다.",
                hasIssueLink
                    ? "이슈 연계 구문(close #" + draft.githubIssueNumber() + ")이 본문에 포함되어 있습니다."
                    : "본문에 'close #" + draft.githubIssueNumber() + "' 또는 'fix #" + draft.githubIssueNumber() + "' 구문을 추가해 주세요.",
                statusOf(hasIssueLink)
            ),
            new PullRequestDraftReadinessItem(
                "template-compliance",
                "템플릿 구조 준수",
                "본문이 section 단위로 나뉘어 리뷰어가 빠르게 읽을 수 있는지 확인합니다.",
                hasStructuredBody
                    ? "본문 section 및 템플릿 필수 항목이 준비되어 있습니다."
                    : "본문에 section heading을 추가해 주세요.",
                statusOf(hasStructuredBody)
            ),
            new PullRequestDraftReadinessItem(
                "test-plan",
                "테스트 계획",
                "검증 방법이나 테스트 계획이 PR 본문에 포함되어 있는지 확인합니다.",
                hasTestPlan
                    ? "테스트 계획이 포함되어 있습니다."
                    : "테스트 또는 Test Plan 항목을 추가해 주세요.",
                statusOf(hasTestPlan)
            ),
            new PullRequestDraftReadinessItem(
                "clean-diff",
                "클린 Diff 검증",
                "충돌 마커나 오류 없이 유효한 코드 변경 사항(diff)이 존재하는지 확인합니다.",
                hasCleanDiff
                    ? "충돌 없이 깨끗한 diff가 준비되어 있습니다."
                    : describeDiffIssue(draft.diffContent()),
                statusOf(hasCleanDiff)
            ),
            new PullRequestDraftReadinessItem(
                "breaking-change",
                "브레이킹 체인지 확인",
                "하위 호환성 영향이나 브레이킹 체인지 유무가 명확하게 명시되었는지 확인합니다.",
                hasBreakingChangeCheck
                    ? "하위 호환성 및 브레이킹 체인지 영향이 명시되었습니다."
                    : "하위 호환성 영향 또는 브레이킹 체인지 유무를 본문에 명시해 주세요.",
                statusOf(hasBreakingChangeCheck)
            ),
            new PullRequestDraftReadinessItem(
                "title",
                "제목 규격",
                "PR 제목이 제출하기에 충분히 짧고 명확한지 확인합니다.",
                !hasValidTitleLength
                    ? "제목이 120자를 넘습니다. 핵심 변경만 남겨 주세요."
                    : (hasTitleConvention ? draft.title() : "Conventional Commits 접두사(docs:, feat:, fix: 등)를 사용해 주세요."),
                statusOf(hasTitleConvention)
            )
        );

        return new PullRequestDraftReadiness(
            draft.id(),
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

    public static boolean isUnavailableDiff(String diffContent) {
        if (diffContent == null || diffContent.isBlank()) {
            return true;
        }
        String normalized = diffContent.toLowerCase(Locale.ROOT);
        return diffContent.contains("Diff를 불러오지 못했습니다")
            || normalized.contains("failed to load diff");
    }

    public static boolean isCleanDiff(String diffContent) {
        if (isUnavailableDiff(diffContent)) {
            return false;
        }
        if (CONFLICT_MARKER_PATTERN.matcher(diffContent).find()) {
            return false;
        }
        return HAS_DIFF_CHANGE_PATTERN.matcher(diffContent).find();
    }

    private static String describeDiffIssue(String diffContent) {
        if (diffContent == null || diffContent.isBlank()) {
            return "코드 변경 사항(diff)이 비어 있습니다.";
        }
        if (isUnavailableDiff(diffContent)) {
            return "compare 대상 브랜치 diff를 불러오지 못했습니다.";
        }
        if (CONFLICT_MARKER_PATTERN.matcher(diffContent).find()) {
            return "diff에 git 충돌 마커(<<<<<<<, =======, >>>>>>>)가 포함되어 있습니다.";
        }
        return "유효한 diff 변경 사항을 확인해 주세요.";
    }

    private static boolean isConventionalTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        return TITLE_CONVENTION_PATTERN.matcher(title.trim()).matches();
    }

    private static boolean hasBreakingChangeCheck(String body, String title) {
        if (title != null && (title.contains("!:") || title.toLowerCase(Locale.ROOT).contains("breaking") || title.contains("브레이킹"))) {
            return true;
        }
        if (body == null || body.isBlank()) {
            return false;
        }
        return containsAny(body,
            "브레이킹", "하위 호환", "호환성", "breaking change", "breaking changes",
            "breaking", "non-breaking", "no breaking", "compatibility");
    }

    private static boolean hasClosingIssueLink(String body, long issueNumber) {
        if (body == null || body.isBlank()) {
            return false;
        }
        Matcher matcher = CLOSING_PATTERN.matcher(body);
        while (matcher.find()) {
            try {
                if (Long.parseLong(matcher.group(1)) == issueNumber) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 경우 계속 탐색합니다.
            }
        }
        return false;
    }

    private static boolean containsAny(String body, String... needles) {
        String normalized = body.toLowerCase(Locale.ROOT);

        for (String needle : needles) {
            if (normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
