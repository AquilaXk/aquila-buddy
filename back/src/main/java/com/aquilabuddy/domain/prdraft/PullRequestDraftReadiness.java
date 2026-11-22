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

    private static final int MAX_BODY_SCAN_CHARS = 32_768;
    private static final int MAX_DIFF_SCAN_CHARS = 65_536;

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
    private static final Pattern TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile(
        "(?i)(?:\\[\\s*tbd\\s*]|\\[\\s*describe\\b[^\\]]*]|\\[\\s*insert\\b[^\\]]*]|<\\s*describe\\b[^>]*>|\\btodo\\s*:)"
    );
    private static final Pattern DIFF_SECRET_PATTERN = Pattern.compile(
        "(?:AKIA[0-9A-Z]{16}"
        + "|(?:ghp|gho)_[A-Za-z0-9_]{16,}"
        + "|github_pat_[A-Za-z0-9_]{20,}"
        + "|sk-[A-Za-z0-9_-]{20,}"
        + "|-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----)"
    );

    public PullRequestDraftReadiness {
        items = List.copyOf(items);
    }

    /**
     * 제출 전 핵심 준비도 항목(브랜치 흐름, 이슈 연결 구문, 템플릿 구조 준수, 테스트 계획, 클린 Diff 검증, 브레이킹 체인지 확인, 제목 규격)을 평가합니다.
     */
    public static PullRequestDraftReadiness from(StoredPullRequestDraft draft) {
        boolean hasBranchFlow = !draft.baseBranch().isBlank()
            && !draft.headBranch().isBlank()
            && !draft.baseBranch().equals(draft.headBranch());

        String visibleBody = stripCommentsAndFencedBlocks(draft.body());
        String normalizedVisibleBody = visibleBody.toLowerCase(Locale.ROOT);

        boolean hasIssueLink = hasClosingIssueLink(visibleBody, draft.githubIssueNumber());
        boolean hasUnfilledPlaceholder = hasTemplatePlaceholder(visibleBody);
        boolean hasStructuredBody = !hasUnfilledPlaceholder
            && hasAtLeastLines(visibleBody, 3)
            && MARKDOWN_HEADING_PATTERN.matcher(visibleBody).find();
        boolean hasTestPlan = containsAnyNormalized(normalizedVisibleBody, "테스트", "검증", "test plan", "tests", "testing");
        boolean hasCleanDiff = isCleanDiff(draft.diffContent());
        boolean hasBreakingChangeCheck = hasBreakingChangeCheck(visibleBody, normalizedVisibleBody, draft.title());

        boolean hasValidTitleLength = draft.title().length() <= 120;
        boolean hasTitleConvention = hasValidTitleLength && isConventionalTitle(draft.title());

        String templateDetail = hasStructuredBody
            ? "본문 section 및 템플릿 필수 항목이 준비되어 있습니다."
            : (hasUnfilledPlaceholder
                ? "템플릿 placeholder 문구(TODO, [TBD] 등)를 실제 내용으로 교체해 주세요."
                : "본문에 section heading을 추가해 주세요.");

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
                templateDetail,
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
                "충돌 마커, 민감한 인증 키, 오류 없이 유효한 코드 변경 사항(diff)이 존재하는지 확인합니다.",
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

    static String stripCommentsAndFencedBlocks(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        int limit = Math.min(body.length(), MAX_BODY_SCAN_CHARS);
        StringBuilder out = new StringBuilder(limit);
        int i = 0;
        char activeFenceChar = 0;
        int activeFenceLen = 0;
        boolean atLineStart = true;

        while (i < limit) {
            if (activeFenceChar == 0 && i + 3 < limit
                && body.charAt(i) == '<'
                && body.charAt(i + 1) == '!'
                && body.charAt(i + 2) == '-'
                && body.charAt(i + 3) == '-') {
                int closeIdx = body.indexOf("-->", i + 4);
                if (closeIdx < 0 || closeIdx >= limit) {
                    break;
                }
                i = closeIdx + 3;
                continue;
            }

            if (atLineStart) {
                int lineStart = i;
                int indent = 0;
                while (i < limit && body.charAt(i) == ' ' && indent < 3) {
                    i++;
                    indent++;
                }
                char fenceCandidate = i < limit ? body.charAt(i) : 0;
                if (fenceCandidate == '`' || fenceCandidate == '~') {
                    int fenceRun = 0;
                    int scan = i;
                    while (scan < limit && body.charAt(scan) == fenceCandidate) {
                        fenceRun++;
                        scan++;
                    }
                    if (fenceRun >= 3) {
                        int lineEnd = findLineEnd(body, scan, limit);
                        if (activeFenceChar == 0) {
                            activeFenceChar = fenceCandidate;
                            activeFenceLen = fenceRun;
                            i = skipNewline(body, lineEnd, limit);
                            atLineStart = true;
                            continue;
                        }
                        if (fenceCandidate == activeFenceChar
                            && fenceRun >= activeFenceLen
                            && isBlankSlice(body, scan, lineEnd)) {
                            activeFenceChar = 0;
                            activeFenceLen = 0;
                            i = skipNewline(body, lineEnd, limit);
                            atLineStart = true;
                            continue;
                        }
                    }
                }
                if (activeFenceChar != 0) {
                    int lineEnd = findLineEnd(body, lineStart, limit);
                    i = skipNewline(body, lineEnd, limit);
                    atLineStart = true;
                    continue;
                }
                i = lineStart;
            }

            char ch = body.charAt(i++);
            out.append(ch);
            atLineStart = (ch == '\n' || ch == '\r');
        }
        return out.toString();
    }

    private static int findLineEnd(String text, int start, int limit) {
        int cursor = start;
        while (cursor < limit) {
            char ch = text.charAt(cursor);
            if (ch == '\n' || ch == '\r') {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    private static int skipNewline(String text, int pos, int limit) {
        int cursor = pos;
        if (cursor < limit && text.charAt(cursor) == '\r') {
            cursor++;
        }
        if (cursor < limit && text.charAt(cursor) == '\n') {
            cursor++;
        }
        return cursor;
    }

    private static boolean isBlankSlice(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasTemplatePlaceholder(String visibleBody) {
        if (visibleBody == null || visibleBody.isBlank()) {
            return false;
        }
        return TEMPLATE_PLACEHOLDER_PATTERN.matcher(visibleBody).find();
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

    public static boolean containsSecretInDiff(String diffContent) {
        if (diffContent == null || diffContent.isEmpty()) {
            return false;
        }
        String bounded = diffContent.length() > MAX_DIFF_SCAN_CHARS
            ? diffContent.substring(0, MAX_DIFF_SCAN_CHARS)
            : diffContent;
        return DIFF_SECRET_PATTERN.matcher(bounded).find();
    }

    public static boolean isCleanDiff(String diffContent) {
        if (isUnavailableDiff(diffContent)) {
            return false;
        }
        if (CONFLICT_MARKER_PATTERN.matcher(diffContent).find()) {
            return false;
        }
        if (containsSecretInDiff(diffContent)) {
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
        if (containsSecretInDiff(diffContent)) {
            return "diff에 민감한 인증 키 또는 비밀 정보 패턴이 포함되어 있습니다. 제거 후 다시 확인해 주세요.";
        }
        return "유효한 diff 변경 사항을 확인해 주세요.";
    }

    private static boolean isConventionalTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        return TITLE_CONVENTION_PATTERN.matcher(title.trim()).matches();
    }

    private static boolean hasBreakingChangeCheck(String body, String normalizedBody, String title) {
        if (title != null && (title.contains("!:") || title.toLowerCase(Locale.ROOT).contains("breaking") || title.contains("브레이킹"))) {
            return true;
        }
        if (body == null || body.isBlank()) {
            return false;
        }
        return containsAnyNormalized(normalizedBody,
            "브레이킹", "하위 호환", "호환성", "breaking change", "breaking changes",
            "breaking", "non-breaking", "no breaking", "compatibility");
    }

    static boolean hasAtLeastLines(String text, int minLines) {
        if (minLines <= 0) {
            return true;
        }
        if (text == null || text.isEmpty()) {
            return false;
        }
        int lineCount = 0;
        int length = text.length();
        int index = 0;
        while (index < length) {
            lineCount++;
            if (lineCount >= minLines) {
                return true;
            }
            while (index < length) {
                char ch = text.charAt(index++);
                if (ch == '\n') {
                    break;
                }
                if (ch == '\r') {
                    if (index < length && text.charAt(index) == '\n') {
                        index++;
                    }
                    break;
                }
            }
        }
        return false;
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

    private static boolean containsAnyNormalized(String normalizedBody, String... lowercaseNeedles) {
        for (String needle : lowercaseNeedles) {
            if (normalizedBody.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
