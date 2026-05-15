package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.StoredPullRequestDraft;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안 상세를 조회하는 유스케이스 계약입니다.
 */
public interface GetPullRequestDraftUseCase {

    Optional<StoredPullRequestDraft> getPullRequestDraft(String id);
}
