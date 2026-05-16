package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;

/**
 * [Outgoing Port] PR 초안 생성에 필요한 compare diff를 외부 저장소에서 읽어오는 계약입니다.
 */
public interface LoadPullRequestDraftDiffPort {

    /**
     * 요청의 저장소/브랜치 정보를 기준으로 PR 초안 입력 diff를 반환합니다.
     */
    String loadDiff(PullRequestDraftRequest request);
}
