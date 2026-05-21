package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.application.port.out.analysis.GenerateIssueGuidePort;
import com.aquilabuddy.domain.analysis.IssueGuide;
import com.aquilabuddy.domain.analysis.RepositoryCodeContext;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 외부 AI 없이도 동일 API 흐름을 검증할 수 있는 결정적 issue guide generator입니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fixture", matchIfMissing = true)
public class RuleBasedIssueGuideGenerationAdapter implements GenerateIssueGuidePort {

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue) {
        return generateIssueGuide(issue, RepositoryCodeContext.empty());
    }

    @Override
    public IssueGuide generateIssueGuide(RecommendedIssue issue, RepositoryCodeContext codeContext) {
        String firstLabel = issue.labels().isEmpty() ? "general" : issue.labels().get(0);
        IssueGuideCodeContextAnchors.CodeFocus focus = IssueGuideCodeContextAnchors.primaryFocus(codeContext);
        if (focus.hasFile()) {
            return new IssueGuide(
                issue.id(),
                selectedFilePaths(codeContext),
                focus.fileName() + "의 " + focus.primarySymbol() + "와 " + focus.methodSummary()
                    + " 흐름에서 \"" + issue.title()
                    + "\" 실패 조건을 먼저 재현 테스트로 고정합니다. 이후 " + focus.path()
                    + "에서 확인 가능한 책임 안에서 수정 방향을 적용하고 " + focus.methodSummary()
                    + " 정상/실패 입력을 첫 검증으로 다시 확인합니다. 코드 맥락만으로 실패 조건이 보이지 않으면 새 동작을 단정하지 말고 추가 조사 지점을 분리합니다.",
                String.join("\n",
                    "GIVEN target_file = \"" + focus.path() + "\"",
                    "AND target_symbol = \"" + focus.primarySymbol() + "\"",
                    "WHEN issue_failure = \"" + issue.title() + "\" is reproduced",
                    "CALL preserve_existing_flow(target_symbol, \"" + focus.methodSummary() + "\")",
                    "CALL apply_smallest_change(target_file, issue_failure, label=\"" + firstLabel + "\")",
                    "ASSERT \"" + focus.methodSummary() + "\" handles normal_input and failing_input"
                ),
                List.of(
                    focus.fileName() + "의 응답 계약이 바뀌면 관련 controller/service 테스트 기대값도 함께 바뀔 수 있습니다.",
                    focus.primarySymbol() + " 주변 호출부가 같은 " + focus.methodSummary() + " 결과를 재사용하는지 확인해야 합니다."
                )
            );
        }

        return new IssueGuide(
            issue.id(),
            selectedFilePaths(codeContext),
            issue.repoFullName() + " #" + issue.issueNumber() + "의 " + firstLabel
                + " 범위를 작게 나누고 현재 테스트 계약을 먼저 고정합니다.",
            String.join("\n",
                "GIVEN issue = \"" + issue.repoFullName() + "#" + issue.issueNumber() + "\"",
                "WHEN issue_title = \"" + issue.title() + "\" is reproduced",
                "CALL write_minimal_regression_test(issue_title)",
                "CALL apply_smallest_change(issue_title)",
                "ASSERT related_api_response_and_pr_context_use_same_terms()"
            ),
            List.of(
                "응답 문구가 바뀌면 프론트엔드 표시와 문서 예시가 함께 영향을 받을 수 있습니다.",
                "기존 추천 점수와 생성 guide의 설명이 어긋나지 않는지 확인해야 합니다."
            )
        );
    }

    private static List<String> selectedFilePaths(RepositoryCodeContext codeContext) {
        if (codeContext == null || !codeContext.hasFiles()) {
            return List.of();
        }

        return codeContext.files().stream()
            .map(file -> file.path())
            .filter(path -> path != null && !path.isBlank())
            .distinct()
            .toList();
    }
}
