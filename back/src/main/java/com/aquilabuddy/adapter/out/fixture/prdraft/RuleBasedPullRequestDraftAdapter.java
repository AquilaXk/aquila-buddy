package com.aquilabuddy.adapter.out.fixture.prdraft;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryFormat;
import com.aquilabuddy.domain.prdraft.PullRequestTemplateSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 실제 AI 연동 전까지 사용할 규칙 기반 PR 초안 생성 adapter입니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fixture", matchIfMissing = true)
public class RuleBasedPullRequestDraftAdapter implements GeneratePullRequestDraftPort {

    /**
     * 요청 맥락과 diff의 첫 번째 추가 라인을 사용해 재현 가능한 PR 초안을 생성합니다.
     */
    @Override
    public PullRequestDraft generate(PullRequestDraftRequest request) {
        String title = "docs: " + request.upstreamRepo() + "#" + request.githubIssueNumber() + " 변경 초안";
        String diffSummary = summarizeDiff(request.diffContent());
        PullRequestDraftRepositoryFormat format = request.repositoryGuideContext().draftFormat();
        if (format.source() == PullRequestDraftRepositoryFormat.Source.TEMPLATE) {
            return new PullRequestDraft(title, templateFormattedBody(request, format, diffSummary));
        }
        if (format.source() == PullRequestDraftRepositoryFormat.Source.MERGED_PULL_REQUEST_SAMPLES) {
            return new PullRequestDraft(title, inferredFormatBody(request, format, diffSummary));
        }

        List<String> bodyLines = new ArrayList<>(List.of(
            "close #" + request.githubIssueNumber(),
            "",
            "## 변경 사항",
            "- " + request.upstreamRepo() + " #" + request.githubIssueNumber() + " 작업을 정리했습니다.",
            "- " + request.baseBranch() + " -> " + request.headBranch() + " 변경 흐름을 기준으로 PR 초안을 구성했습니다.",
            "",
            "## 작업 메모",
            "- " + diffSummary
        ));

        if (request.repositoryGuideContext().hasAnyGuide()) {
            bodyLines.add("");
            bodyLines.add("## 저장소 참고 자료");
            request.repositoryGuideContext().guideSummaryLines()
                .forEach(line -> bodyLines.add("- " + line));
        }

        if (request.issueContext().hasIssue()) {
            bodyLines.add("");
            bodyLines.add("## 연결 이슈");
            request.issueContext().contextSummaryLines()
                .forEach(line -> bodyLines.add("- " + line));
        }

        if (request.analysisContext().hasAnalysis()) {
            bodyLines.add("");
            bodyLines.add("## 분석 가이드");
            request.analysisContext().contextSummaryLines()
                .forEach(line -> bodyLines.add("- " + line));
        }

        bodyLines.addAll(List.of(
            "",
            "## 테스트 계획",
            "- 변경 후 관련 테스트를 실행해 주세요.",
            "",
            "## 체크리스트",
            "- [x] 변경 사항에 대한 셀프 리뷰를 완료했습니다.",
            "- [x] 관련 테스트가 모두 통과하는지 확인했습니다.",
            "",
            "## 브레이킹 체인지",
            "- 하위 호환성을 깨뜨리는 변경 사항이 없습니다."
        ));

        return new PullRequestDraft(title, String.join("\n", bodyLines));
    }

    private String templateFormattedBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format,
        String diffSummary
    ) {
        List<PullRequestTemplateSection> sections = request.repositoryGuideContext().templateSections();
        if (sections.isEmpty()) {
            return fallbackTemplateFormattedBody(request, format, diffSummary);
        }

        List<String> bodyLines = new ArrayList<>();
        bodyLines.add("close #" + request.githubIssueNumber());
        bodyLines.add("");

        boolean hasTestPlanSection = false;
        boolean hasBreakingChangeSection = false;
        boolean descriptionPopulated = false;

        for (int i = 0; i < sections.size(); i++) {
            PullRequestTemplateSection section = sections.get(i);
            if (i > 0) {
                bodyLines.add("");
            }
            bodyLines.add(section.rawHeading());

            switch (section.type()) {
                case DESCRIPTION -> {
                    descriptionPopulated = true;
                    if (section.hasContent()) {
                        section.content().lines()
                            .map(String::stripTrailing)
                            .forEach(bodyLines::add);
                    }
                    bodyLines.add("- " + diffSummary);
                }
                case TEST_PLAN -> {
                    hasTestPlanSection = true;
                    if (section.hasContent()) {
                        section.content().lines()
                            .map(String::stripTrailing)
                            .forEach(bodyLines::add);
                    }
                    if (!hasMeaningfulContent(section.content()) && !section.hasCheckboxes()) {
                        bodyLines.add("- 변경 후 관련 테스트를 실행해 주세요.");
                    }
                }
                case CHECKLIST -> {
                    if (section.hasContent()) {
                        section.content().lines()
                            .map(String::stripTrailing)
                            .forEach(bodyLines::add);
                    }
                }
                case BREAKING_CHANGES -> {
                    hasBreakingChangeSection = true;
                    if (section.hasContent()) {
                        section.content().lines()
                            .map(String::stripTrailing)
                            .forEach(bodyLines::add);
                    }
                    if (!hasMeaningfulContent(section.content())) {
                        bodyLines.add("- 하위 호환성을 깨뜨리는 변경 사항이 없습니다.");
                    }
                }
                case ISSUE_LINK, OTHER -> {
                    if (section.hasContent()) {
                        section.content().lines()
                            .map(String::stripTrailing)
                            .forEach(bodyLines::add);
                    }
                }
            }
        }

        if (!descriptionPopulated) {
            bodyLines.add("");
            bodyLines.add("## 작업 메모");
            bodyLines.add("- " + diffSummary);
        }

        appendDefaultSections(
            bodyLines,
            !hasTestPlanSection && !containsAny(format.templateBody(), "테스트", "검증", "test", "testing"),
            !hasBreakingChangeSection && !containsAny(format.templateBody(), "브레이킹", "호환성", "breaking")
        );

        appendRepositoryGuideSummary(bodyLines, request);
        return String.join("\n", bodyLines);
    }

    private String fallbackTemplateFormattedBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format,
        String diffSummary
    ) {
        List<String> bodyLines = new ArrayList<>();
        bodyLines.add("close #" + request.githubIssueNumber());
        bodyLines.add("");
        format.templateBody().lines()
            .map(String::stripTrailing)
            .forEach(bodyLines::add);
        bodyLines.add("");
        bodyLines.add("## 작업 메모");
        bodyLines.add("- " + diffSummary);
        appendDefaultSections(
            bodyLines,
            !containsAny(format.templateBody(), "테스트", "검증", "test", "testing"),
            !containsAny(format.templateBody(), "브레이킹", "호환성", "breaking")
        );
        appendRepositoryGuideSummary(bodyLines, request);
        return String.join("\n", bodyLines);
    }

    private String inferredFormatBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format,
        String diffSummary
    ) {
        List<String> bodyLines = new ArrayList<>(List.of(
            "close #" + request.githubIssueNumber(),
            ""
        ));
        for (String heading : format.requiredHeadings()) {
            if (bodyLines.size() > 2) {
                bodyLines.add("");
            }
            bodyLines.add(heading);
            bodyLines.add("- " + contentForHeading(heading, diffSummary));
        }
        appendDefaultSections(
            bodyLines,
            format.requiredHeadings().stream().noneMatch(h -> h.toLowerCase(Locale.ROOT).contains("test") || h.contains("검증")),
            format.requiredHeadings().stream().noneMatch(h -> h.toLowerCase(Locale.ROOT).contains("breaking") || h.contains("브레이킹"))
        );
        appendRepositoryGuideSummary(bodyLines, request);
        return String.join("\n", bodyLines);
    }

    private void appendDefaultSections(
        List<String> bodyLines,
        boolean includeTestPlan,
        boolean includeBreakingChanges
    ) {
        if (includeTestPlan) {
            bodyLines.add("");
            bodyLines.add("## 테스트 계획");
            bodyLines.add("- 변경 후 관련 테스트를 실행해 주세요.");
        }
        if (includeBreakingChanges) {
            bodyLines.add("");
            bodyLines.add("## 브레이킹 체인지");
            bodyLines.add("- 하위 호환성을 깨뜨리는 변경 사항이 없습니다.");
        }
    }

    private static boolean containsAny(String body, String... needles) {
        if (body == null) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void appendRepositoryGuideSummary(List<String> bodyLines, PullRequestDraftRequest request) {
        if (!request.repositoryGuideContext().hasAnyGuide()) {
            return;
        }
        bodyLines.add("");
        bodyLines.add("## 저장소 참고 자료");
        request.repositoryGuideContext().guideSummaryLines()
            .forEach(line -> bodyLines.add("- " + line));
    }

    private String contentForHeading(String heading, String diffSummary) {
        String normalizedHeading = heading.toLowerCase(Locale.ROOT);
        if (normalizedHeading.contains("test") || normalizedHeading.contains("검증")) {
            return "변경 후 관련 테스트를 실행해 주세요.";
        }
        return diffSummary;
    }

    private String summarizeDiff(String diffContent) {
        return diffContent.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("+") && !line.startsWith("+++"))
            .map(line -> line.substring(1).trim())
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse("입력된 diff를 기준으로 변경 목적을 정리했습니다.");
    }

    private static boolean hasMeaningfulContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // HTML 주석(<!-- ... -->)을 제거한 후 실제 내용이 남아있는지 확인합니다.
        String stripped = content.replaceAll("(?s)<!--.*?-->", "").trim();
        return !stripped.isBlank();
    }
}
