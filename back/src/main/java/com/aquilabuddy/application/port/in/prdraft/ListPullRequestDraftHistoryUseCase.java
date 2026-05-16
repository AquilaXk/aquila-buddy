package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryPage;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryQuery;

/**
 * [Incoming Port] PR 초안 이력을 페이지 단위로 조회하는 유스케이스 계약입니다.
 */
public interface ListPullRequestDraftHistoryUseCase {

    PullRequestDraftHistoryPage listPullRequestDraftHistory(PullRequestDraftHistoryQuery query);
}
