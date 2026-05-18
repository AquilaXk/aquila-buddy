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
        return new IssueGuide(
            issue.id(),
            selectedFilePaths(codeContext),
            issue.repoFullName() + " #" + issue.issueNumber() + "의 " + firstLabel
                + " 범위를 작게 나누고 현재 테스트 계약을 먼저 고정합니다.",
            String.join("\n",
                "1. 이슈 제목과 요약에서 변경 대상 흐름을 확인한다.",
                "2. 현재 동작을 재현하는 최소 테스트를 작성한다.",
                "3. 테스트를 통과하는 가장 작은 구현을 적용한다.",
                "4. 관련 API 응답과 PR 초안 맥락에 같은 용어가 쓰이는지 검증한다."
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
