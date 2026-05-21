package com.aquilabuddy.adapter.out.runtime.issue;

import com.aquilabuddy.application.port.out.issue.SummarizeRecommendedIssuePort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI provider 없이 실행할 때 추천 이슈 요약을 원본 값 그대로 통과시킵니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fixture", matchIfMissing = true)
public class RuleBasedRecommendedIssueSummaryAdapter implements SummarizeRecommendedIssuePort {

    @Override
    public List<RecommendedIssue> summarizeRecommendedIssues(List<RecommendedIssue> issues) {
        return List.copyOf(issues);
    }
}
