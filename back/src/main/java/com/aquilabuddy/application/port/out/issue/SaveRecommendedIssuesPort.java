package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Outgoing Port] 추천 이슈 후보를 fallback 저장소에 동기화하는 계약입니다.
 */
public interface SaveRecommendedIssuesPort {

    /**
     * 외부 검색에서 얻은 추천 후보를 이후 fallback 조회에 사용할 수 있게 저장합니다.
     */
    void saveRecommendedIssues(List<RecommendedIssue> issues);
}
