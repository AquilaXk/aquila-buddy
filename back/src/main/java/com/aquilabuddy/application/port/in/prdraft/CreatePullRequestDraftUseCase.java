package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraft;
import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;

/**
 * [Incoming Port] PR 초안을 생성하는 유스케이스 계약입니다.
 */
public interface CreatePullRequestDraftUseCase {

    /**
     * 저장소/이슈/브랜치/diff 맥락을 바탕으로 PR 제목과 본문 초안을 생성합니다.
     */
    PullRequestDraft create(PullRequestDraftRequest request);
}
