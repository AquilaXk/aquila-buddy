package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftAnalysisContext;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;

/**
 * [Outgoing Port] PR 초안 생성에 참고할 저장된 분석 guide context를 읽어오는 계약입니다.
 */
public interface LoadPullRequestDraftAnalysisContextPort {

    /**
     * 요청의 저장소와 이슈 번호를 기준으로 저장된 분석 guide context를 반환합니다.
     */
    PullRequestDraftAnalysisContext loadAnalysisContext(PullRequestDraftRequest request);
}
