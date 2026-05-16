package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftRepositoryGuideContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;

/**
 * [Outgoing Port] PR 초안 생성에 참고할 저장소별 기여 가이드 context를 읽어오는 계약입니다.
 */
public interface LoadPullRequestDraftRepositoryGuideContextPort {

    /**
     * 요청 저장소의 PR template, CONTRIBUTING, merged PR sample context를 반환합니다.
     */
    PullRequestDraftRepositoryGuideContext loadRepositoryGuideContext(PullRequestDraftRequest request);
}
