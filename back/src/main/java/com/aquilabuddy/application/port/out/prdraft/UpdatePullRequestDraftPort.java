package com.aquilabuddy.application.port.out.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftUpdateRequest;
import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.Optional;

/**
 * [Outgoing Port] 저장된 PR 초안 수정 내용을 반영하는 계약입니다.
 */
public interface UpdatePullRequestDraftPort {

    /**
     * 저장된 PR 초안의 제목/본문을 갱신하고 최신 초안을 반환합니다.
     */
    Optional<StoredPullRequestDraft> updatePullRequestDraft(String id, PullRequestDraftUpdateRequest request);
}
