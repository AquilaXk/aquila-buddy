package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslationTimeline;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안 번역 결과의 제출 준비 timeline을 조회하는 유스케이스 계약입니다.
 */
public interface GetPullRequestDraftTranslationTimelineUseCase {

    Optional<PullRequestDraftTranslationTimeline> getTranslationTimeline(String draftId, String translationId);
}
