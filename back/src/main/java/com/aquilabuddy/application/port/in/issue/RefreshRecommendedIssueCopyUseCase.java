package com.aquilabuddy.application.port.in.issue;

import com.aquilabuddy.domain.issue.RecommendedIssueCopyRefreshResult;

/**
 * 저장된 추천 이슈 카드 copy를 dry-run으로 점검하거나 제한 수량으로 갱신하는 use case입니다.
 */
public interface RefreshRecommendedIssueCopyUseCase {

    RecommendedIssueCopyRefreshResult refreshRecommendedIssueCopy(int limit, boolean dryRun);
}
