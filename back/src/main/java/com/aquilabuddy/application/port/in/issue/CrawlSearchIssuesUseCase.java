package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Incoming Port] 검색어 기반으로 외부 이슈 후보를 수집하는 유스케이스 계약입니다.
 */
public interface CrawlSearchIssuesUseCase {

    List<RecommendedIssue> crawlSearchIssues(String query);
}
