package com.aquilabuddy.adapter.out.fixture.prdraft;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryFormat;
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
            "## 테스트",
            "- 변경 후 관련 테스트를 실행해 주세요."
        ));

        return new PullRequestDraft(title, String.join("\n", bodyLines));
    }

    private String templateFormattedBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format,
        String diffSummary
    ) {
        List<String> bodyLines = new ArrayList<>();
        format.templateBody().lines()
            .map(String::stripTrailing)
            .forEach(bodyLines::add);
        bodyLines.add("");
        bodyLines.add("## 작업 메모");
        bodyLines.add("- " + diffSummary);
        appendRepositoryGuideSummary(bodyLines, request);
        return String.join("\n", bodyLines);
    }

    private String inferredFormatBody(
        PullRequestDraftRequest request,
        PullRequestDraftRepositoryFormat format,
        String diffSummary
    ) {
        List<String> bodyLines = new ArrayList<>();
        for (String heading : format.requiredHeadings()) {
            if (!bodyLines.isEmpty()) {
                bodyLines.add("");
            }
            bodyLines.add(heading);
            bodyLines.add("- " + contentForHeading(heading, diffSummary));
        }
        appendRepositoryGuideSummary(bodyLines, request);
        return String.join("\n", bodyLines);
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
}
