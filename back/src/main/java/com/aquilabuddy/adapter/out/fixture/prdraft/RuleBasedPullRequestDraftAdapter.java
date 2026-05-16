package com.aquilabuddy.adapter.out.fixture.prdraft;

import com.aquilabuddy.application.port.out.prdraft.GeneratePullRequestDraftPort;
import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * [Outgoing Adapter] 실제 AI 연동 전까지 사용할 규칙 기반 PR 초안 생성 adapter입니다.
 */
@Component
public class RuleBasedPullRequestDraftAdapter implements GeneratePullRequestDraftPort {

    /**
     * 요청 맥락과 diff의 첫 번째 추가 라인을 사용해 재현 가능한 PR 초안을 생성합니다.
     */
    @Override
    public PullRequestDraft generate(PullRequestDraftRequest request) {
        String title = "docs: " + request.upstreamRepo() + "#" + request.githubIssueNumber() + " 변경 초안";
        String diffSummary = summarizeDiff(request.diffContent());
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

        bodyLines.addAll(List.of(
            "",
            "## 테스트",
            "- 변경 후 관련 테스트를 실행해 주세요."
        ));

        return new PullRequestDraft(title, String.join("\n", bodyLines));
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
