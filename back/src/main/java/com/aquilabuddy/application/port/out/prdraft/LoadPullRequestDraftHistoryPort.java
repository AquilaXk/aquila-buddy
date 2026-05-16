package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryPage;
import com.aquilabuddy.domain.prdraft.PullRequestDraftHistoryQuery;

/**
 * 저장된 PR 초안 이력을 페이지 단위로 읽기 위한 outbound port입니다.
 */
public interface LoadPullRequestDraftHistoryPort {

    PullRequestDraftHistoryPage loadPullRequestDraftHistory(PullRequestDraftHistoryQuery query);
}
