package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftReadiness;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안의 제출 준비 상태를 조회하는 유스케이스 계약입니다.
 */
public interface GetPullRequestDraftReadinessUseCase {

    Optional<PullRequestDraftReadiness> getReadiness(String id);
}
