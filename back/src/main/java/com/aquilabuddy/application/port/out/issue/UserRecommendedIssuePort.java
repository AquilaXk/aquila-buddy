package com.aquilabuddy.application.port.out.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import com.aquilabuddy.domain.issue.UserIssueState;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * [Outgoing Port] 사용자별 추천 이슈 북마크 및 분석 상태를 저장·조회합니다.
 */
public interface UserRecommendedIssuePort {

    Map<String, UserIssueState> findStatesByUserAndIssueIds(long userId, Collection<String> issueIds);

    List<RecommendedIssue> loadBookmarkedIssues(long userId);

    void setBookmarked(long userId, String issueId, boolean bookmarked, Instant timestamp);

    void markAnalyzed(long userId, String issueId, Instant timestamp);
}
