package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * 추천 카드에 표시할 이슈 요약을 사용자 친화적인 문장으로 보강합니다.
 */
@FunctionalInterface
public interface SummarizeRecommendedIssuePort {

    List<RecommendedIssue> summarizeRecommendedIssues(List<RecommendedIssue> issues);
}
