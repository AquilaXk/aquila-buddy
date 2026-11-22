package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;

/**
 * [Incoming Port] 사용자별 추천 이슈 북마크를 등록하거나 해제합니다.
 */
public interface BookmarkRecommendedIssueUseCase {

    RecommendedIssue bookmark(String issueId);

    RecommendedIssue unbookmark(String issueId);
}
