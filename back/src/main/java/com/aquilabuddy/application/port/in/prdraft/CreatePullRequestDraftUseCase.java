package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;

/**
 * [Incoming Port] PR 초안을 생성하는 유스케이스 계약입니다.
 */
public interface CreatePullRequestDraftUseCase {

    /**
     * 저장소/이슈/브랜치/diff 맥락을 바탕으로 PR 제목과 본문 초안을 생성하고 저장합니다.
     */
    StoredPullRequestDraft create(PullRequestDraftRequest request);
}
