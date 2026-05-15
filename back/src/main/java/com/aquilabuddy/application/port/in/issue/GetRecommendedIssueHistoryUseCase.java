package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssueHistory;
import java.util.List;

/**
 * [Incoming Port] 저장된 추천 이슈 이력을 조회하는 use case 계약입니다.
 */
public interface GetRecommendedIssueHistoryUseCase {

    /**
     * 현재 저장소에 남아 있는 추천 후보를 이력 응답으로 반환합니다.
     */
    List<RecommendedIssueHistory> history();
}
