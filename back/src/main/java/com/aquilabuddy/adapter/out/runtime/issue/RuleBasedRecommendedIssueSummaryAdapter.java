package com.aquilabuddy.adapter.out.runtime.issue;

import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyQuality;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI provider 없이 실행할 때도 추천 카드가 영어 원문/generic 이유를 그대로 노출하지 않도록 보정합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fixture", matchIfMissing = true)
public class RuleBasedRecommendedIssueSummaryAdapter implements SummarizeRecommendedIssuePort {

    @Override
    public List<RecommendedIssue> summarizeRecommendedIssues(List<RecommendedIssue> issues) {
        return List.copyOf(issues).stream()
            .map(RuleBasedRecommendedIssueSummaryAdapter::summarize)
            .toList();
    }

    private static RecommendedIssue summarize(RecommendedIssue issue) {
        if (!RecommendedIssueCopyQuality.needsRefresh(issue)) {
            return issue.withCurrentCopyOutputVersion();
        }
        String summary = issue.title()
            + " 이슈는 저장소 " + issue.repoFullName()
            + "의 #" + issue.issueNumber()
            + "에서 확인된 문제를 작은 범위로 재현하고 수정하는 작업입니다.";
        return new RecommendedIssue(
            issue.id(),
            issue.repoFullName(),
            issue.issueNumber(),
            issue.title(),
            truncate(summary, 1_000),
            issue.score(),
            issue.labels(),
            issue.status(),
            reason(issue)
        );
    }

    private static String reason(RecommendedIssue issue) {
        if (issue.labels().stream().anyMatch(label -> label.equalsIgnoreCase("bug"))) {
            return "bug 라벨이 붙어 있어 실패 조건을 재현한 뒤 원인을 좁혀 수정하기 좋은 이슈입니다.";
        }
        if (issue.labels().stream().anyMatch(label -> label.equalsIgnoreCase("good first issue"))) {
            return "good first issue 범위가 붙어 있어 작은 변경으로 첫 기여 흐름을 검증하기 적합합니다.";
        }
        return "저장소와 이슈 제목에서 확인되는 범위가 명확해 재현, 수정, 검증 순서로 접근하기 좋습니다.";
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "...";
    }
}
