package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;

/**
 * [Outgoing Port] 외부 생성기나 AI adapter를 통해 PR 초안을 생성하는 계약입니다.
 */
public interface GeneratePullRequestDraftPort {

    /**
     * PR 작성 맥락을 받아 제목과 본문 초안을 반환합니다.
     */
    PullRequestDraft generate(PullRequestDraftRequest request);
}
