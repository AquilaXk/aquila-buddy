package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Incoming Port] 사용자에게 기여 후보 이슈를 추천하는 유스케이스 계약입니다.
 */
public interface RecommendIssuesUseCase {

    /**
     * 추천 기준에 맞게 정렬된 이슈 목록을 반환합니다.
     */
    List<RecommendedIssue> recommend();
}
