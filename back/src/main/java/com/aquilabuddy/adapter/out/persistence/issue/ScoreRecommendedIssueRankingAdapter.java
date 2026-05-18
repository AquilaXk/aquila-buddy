package com.aquilabuddy.adapter.out.persistence.issue;

import com.aquilabuddy.application.port.out.issue.RankRecommendedIssuesPort;
import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 별도 similarity provider가 없을 때 기존 score 정렬 결과를 그대로 유지합니다.
 */
@Component
@ConditionalOnMissingBean(RankRecommendedIssuesPort.class)
public class ScoreRecommendedIssueRankingAdapter implements RankRecommendedIssuesPort {

    @Override
    public List<RecommendedIssue> rankRecommendedIssues(List<RecommendedIssue> issues) {
        return List.copyOf(issues);
    }
}
