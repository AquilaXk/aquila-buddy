package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Outgoing Port] 외부 서비스에서 최신 추천 이슈 후보를 검색하는 계약입니다.
 */
public interface SearchRecommendedIssuesPort {

    /**
     * 현재 추천 정책에 맞는 최신 이슈 후보를 반환합니다.
     */
    List<RecommendedIssue> searchRecommendedIssues();
}
