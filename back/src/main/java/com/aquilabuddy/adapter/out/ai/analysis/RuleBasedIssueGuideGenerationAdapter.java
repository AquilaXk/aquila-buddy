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
                "원인: " + failureExplanation(issue)
                    + " 수정 방향: " + focus.path() + "에서 확인되는 " + focus.primarySymbol()
                    + " 책임을 기준으로 실제 검증 로직을 좁히고, \"" + issue.title()
                    + "\" 조건과 반대되는 검사식을 작은 변경으로 맞춥니다."
                    + " 첫 검증: " + focus.fileName()
                    + "에서 공백/정상 입력 재현 테스트를 함께 확인해 기존 동작과 새 실패 조건을 비교합니다."
                    + " 코드 맥락만으로 구현 위치가 테스트 파일로만 보이면, 테스트는 재현 기준으로 두고 실제 검증 구현 파일을 추가 조사합니다.",
                String.join("\n",
                    pseudoCodeLines(issue, focus)
                ),
                List.of(
                    sideEffectFor(issue, focus.fileName()),
                    "코드 맥락이 테스트 파일 위주라면 실제 입력 검증 구현 위치를 먼저 확인한 뒤 수정 범위를 확정해야 합니다."
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
                    "IF current_behavior_accepts_issue_condition",
                    "CALL add_regression_test_for_issue_condition()",
                    "CALL update_visible_branch_for_issue_condition()",
                    "ASSERT regression_test_rejects_issue_condition()",
                    "ASSERT existing_valid_case_still_passes()"
                ),
            List.of(
                "응답 문구가 바뀌면 프론트엔드 표시와 문서 예시가 함께 영향을 받을 수 있습니다.",
                "기존 추천 점수와 생성 guide의 설명이 어긋나지 않는지 확인해야 합니다."
            )
        );
    }

    private static String[] pseudoCodeLines(RecommendedIssue issue, IssueGuideCodeContextAnchors.CodeFocus focus) {
        String issueText = (issue.title() + " " + issue.summary()).toLowerCase();
        if (issueText.contains("isempty") && issueText.contains("isblank")) {
            return new String[] {
                "GIVEN template = \"   \"",
                "AND inlineTemplate = \"\"",
                "WHEN " + focus.primarySymbol() + " validates template inputs",
                "IF template.isBlank() AND inlineTemplate.isBlank()",
                "RETURN validation_error(\"template or inlineTemplate is required\")",
                "ASSERT whitespace template is rejected before Thymeleaf rendering",
                "ASSERT template = \"<p>Hello</p>\" still renders successfully"
            };
        }
        if (issueText.contains("validation") || issueText.contains("검증")) {
            return new String[] {
                "GIVEN invalidInput = value_from_issue_title",
                "AND validInput = existing_success_case",
                "WHEN " + focus.primarySymbol() + " validates invalidInput",
                "IF current validation accepts invalidInput",
                "CALL tighten_validation_branch(invalidInput)",
                "ASSERT invalidInput is rejected with the documented error",
                "ASSERT validInput still passes"
            };
        }
        return new String[] {
            "GIVEN issueCondition = \"" + issue.title() + "\"",
            "AND validCase = existing_success_case",
            "WHEN " + focus.primarySymbol() + " handles issueCondition",
            "IF current branch does not expose the expected outcome",
            "CALL update_branch_for_issue_condition(issueCondition)",
            "ASSERT issueCondition produces the expected outcome",
            "ASSERT validCase still passes"
        };
    }

    private static String failureExplanation(RecommendedIssue issue) {
        String issueText = (issue.title() + " " + issue.summary()).toLowerCase();
        if (issueText.contains("isempty") && issueText.contains("isblank")) {
            return "공백 문자열이 빈 문자열로 취급되지 않아 isEmpty() 검증을 통과하고, isBlank() 기준이 필요한 입력이 이후 템플릿 처리 단계에서 뒤늦게 실패하는 문제입니다.";
        }
        if (issueText.contains("validation") || issueText.contains("검증")) {
            return "현재 검증 조건이 이슈에서 요구하는 실패 입력을 초기에 걸러내지 못하는 문제입니다.";
        }
        return "\"" + issue.title() + "\" 조건이 현재 코드 경로에서 어떤 입력으로 재현되는지 먼저 분리해야 하는 문제입니다.";
    }

    private static String sideEffectFor(RecommendedIssue issue, String fileName) {
        String issueText = (issue.title() + " " + issue.summary()).toLowerCase();
        if (issueText.contains("isempty") && issueText.contains("isblank")) {
            return fileName + "에서 공백 문자열을 실패 입력으로 바꾸면 기존 빈 문자열 처리 테스트와 예외 메시지 기대값이 함께 영향을 받을 수 있습니다.";
        }
        return fileName + "에서 검증 조건을 좁히면 기존 정상 입력과 실패 입력의 경계가 바뀔 수 있으므로 회귀 테스트를 함께 확인해야 합니다.";
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
