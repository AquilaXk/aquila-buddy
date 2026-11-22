package com.aquilabuddy.application.port.in.prdraft;

import com.aquilabuddy.domain.prdraft.PullRequestDraftTranslation;
import java.util.Optional;

/**
 * [Incoming Port] 저장된 PR 초안에서 번역 초안을 생성하는 유스케이스 계약입니다.
 */
public interface CreatePullRequestDraftTranslationUseCase {

    Optional<PullRequestDraftTranslation> createTranslation(String draftId, String targetLanguage);
}
