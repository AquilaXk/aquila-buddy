package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Outgoing Port] 추천 이슈 데이터를 외부 저장소에서 읽어오는 계약입니다.
 */
public interface LoadRecommendedIssuesPort {

    /**
     * 추천 점수와 노출 정책에 맞춰 정렬할 수 있는 이슈 데이터를 반환합니다.
     */
    List<RecommendedIssue> loadRecommendedIssues();
}
