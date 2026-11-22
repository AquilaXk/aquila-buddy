package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.Optional;

/**
 * [Incoming Port] 내부 이슈 ID로 저장된 추천 이슈를 조회하는 use case 계약입니다.
 */
public interface GetRecommendedIssueUseCase {

    /**
     * 내부 이슈 ID에 해당하는 추천 이슈를 조회합니다.
     */
    Optional<RecommendedIssue> getRecommendedIssue(String id);
}
