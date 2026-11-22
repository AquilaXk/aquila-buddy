package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationSubmission;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안 번역 결과의 제출용 복사 bundle을 조회하는 유스케이스 계약입니다.
 */
public interface GetPullRequestDraftTranslationSubmissionUseCase {

    Optional<PullRequestDraftTranslationSubmission> getTranslationSubmission(String draftId, String translationId);
}
