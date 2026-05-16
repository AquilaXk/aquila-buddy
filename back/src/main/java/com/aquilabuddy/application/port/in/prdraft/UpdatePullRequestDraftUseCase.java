package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안을 수정하는 use case 계약입니다.
 */
public interface UpdatePullRequestDraftUseCase {

    /**
     * 저장된 PR 초안의 제목/본문을 수정하고 최신 초안을 반환합니다.
     */
    Optional<StoredPullRequestDraft> updatePullRequestDraft(String id, PullRequestDraftUpdateRequest request);
}
