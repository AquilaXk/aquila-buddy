package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssue;
import java.util.List;

/**
 * [Incoming Port] 현재 사용자가 북마크한 추천 이슈 목록을 조회합니다.
 */
public interface ListBookmarkedIssuesUseCase {

    List<RecommendedIssue> listBookmarks();
}
