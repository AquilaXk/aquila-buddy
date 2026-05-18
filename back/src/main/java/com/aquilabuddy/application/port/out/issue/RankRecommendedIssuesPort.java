package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * 추천 후보를 사용자 맥락 기반 순서로 재정렬하는 outbound port입니다.
 */
public interface RankRecommendedIssuesPort {

    /**
     * 추천 후보 목록을 ranking provider 기준으로 정렬해 반환합니다.
     */
    List<RecommendedIssue> rankRecommendedIssues(List<RecommendedIssue> issues);
}
