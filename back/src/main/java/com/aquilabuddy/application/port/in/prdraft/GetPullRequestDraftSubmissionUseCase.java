package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftSubmission;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안의 제출용 복사 bundle을 조회하는 유스케이스 계약입니다.
 */
public interface GetPullRequestDraftSubmissionUseCase {

    Optional<PullRequestDraftSubmission> getSubmission(String id);
}
