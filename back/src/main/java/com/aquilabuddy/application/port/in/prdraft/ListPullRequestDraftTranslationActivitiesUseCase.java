package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationActivity;
import java.util.List;

/**
 * Dashboard에 표시할 최근 PR 초안 번역 활동을 조회하는 inbound use case입니다.
 */
public interface ListPullRequestDraftTranslationActivitiesUseCase {

    List<PullRequestDraftTranslationActivity> listRecentTranslationActivities(int limit);
}
